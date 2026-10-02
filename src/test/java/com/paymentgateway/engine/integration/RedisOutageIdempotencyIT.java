package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferMoneyCommand;
import com.paymentgateway.engine.application.usecase.TransferOutcome;
import com.paymentgateway.engine.domain.model.AccountStatus;
import com.paymentgateway.engine.domain.model.UserStatus;
import com.paymentgateway.engine.infrastructure.adapter.persistence.AccountJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.UserJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.AccountEntity;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class RedisOutageIdempotencyIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private TransferMoney transferMoney;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID seedUser() {
        UUID id = UUID.randomUUID();
        userJpaRepository.save(new UserEntity(id, "u-" + id + "@example.com", "User", UserStatus.ACTIVE));
        return id;
    }

    private UUID seedAccount(UUID ownerId, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        accountJpaRepository.save(new AccountEntity(id, ownerId, balance, AccountStatus.ACTIVE, 0L));
        return id;
    }

    @Test
    void transferWithRedisDown_neverProducesDuplicateTransaction() {
        UUID sourceOwner = seedUser();
        UUID targetOwner = seedUser();
        UUID sourceId = seedAccount(sourceOwner, new BigDecimal("100.00"));
        UUID targetId = seedAccount(targetOwner, BigDecimal.ZERO);
        String key = "redis-outage-" + UUID.randomUUID();

        // Redis caído a mitad de secuencia -- ANTES de la primera transferencia.
        REDIS.stop();

        TransferMoneyCommand command = TransferMoneyCommand.forInternal(
                sourceId, sourceOwner, targetId, new BigDecimal("10.00"), key);

        // Primer intento: tryBegin() falla silenciosamente (Redis caído) ->
        // acquired() de fallback -> ejecuta normalmente, persiste en Postgres.
        TransferOutcome first = transferMoney.execute(command);
        assertThat(first).isInstanceOf(TransferOutcome.Executed.class);

        // Segundo intento, MISMA key -- simula un retry del cliente mientras
        // Redis SIGUE caído. tryBegin() vuelve a fallar -> acquired() de nuevo
        // (Redis no puede decirnos que ya existe) -> InternalTransferHandler
        // reintenta el flujo completo -> Postgres rechaza el INSERT duplicado
        // por el UNIQUE constraint -> TransferMoney captura
        // DataIntegrityViolationException -> devuelve la Transaction ORIGINAL.
        TransferOutcome second = transferMoney.execute(command);
        assertThat(second).isInstanceOf(TransferOutcome.Executed.class);

        UUID firstTxId = ((TransferOutcome.Executed) first).transaction().getId();
        UUID secondTxId = ((TransferOutcome.Executed) second).transaction().getId();
        assertThat(secondTxId)
                .as("Ambos intentos deben resolver a la MISMA Transaction, no crear una segunda")
                .isEqualTo(firstTxId);

        // Verificación directa en Postgres: exactamente UNA fila con esta key.
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE idempotency_key = ?", Integer.class, key);
        assertThat(rowCount)
                .as("RISK-003: el UNIQUE constraint debe garantizar exactamente una fila, incluso con Redis caído")
                .isEqualTo(1);

        // El balance debe reflejar UN solo débito, no dos -- confirma que la
        // transacción de Postgres abortó limpiamente en el segundo intento
        // (débito+crédito del reintento se revirtieron junto con el INSERT fallido).
        AccountEntity finalSource = accountJpaRepository.findById(sourceId).orElseThrow();
        assertThat(finalSource.getBalance())
                .as("El balance NO debe reflejar un doble débito pese al reintento con Redis caído")
                .isEqualByComparingTo("90.00");
    }
}