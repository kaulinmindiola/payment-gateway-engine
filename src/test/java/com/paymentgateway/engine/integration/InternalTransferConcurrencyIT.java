package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferMoneyCommand;
import com.paymentgateway.engine.domain.model.AccountStatus;
import com.paymentgateway.engine.domain.model.UserStatus;
import com.paymentgateway.engine.infrastructure.adapter.persistence.AccountJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.UserJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.AccountEntity;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.UserEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CS-01: test de concurrencia obligatorio (Sección 9/15 del plan).
 * Escenario de máxima contención: N hilos transfiriendo simultáneamente
 * desde la MISMA cuenta origen hacia la MISMA cuenta destino.
 * Saldo inicial suficiente para que TODAS las transferencias tengan éxito
 * -- este test aísla ausencia de condiciones de carrera, no BR-001.
 */
class InternalTransferConcurrencyIT extends AbstractApplicationIntegrationTest {

    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("10.00");

    @Autowired
    private TransferMoney transferMoney;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest(name = "{0} hilos concurrentes sobre el mismo par de cuentas")
    @ValueSource(ints = {20, 50})
    void concurrentInternalTransfers_sameAccountPair_noLostUpdatesNoDuplicates(int threadCount) throws Exception {
        UUID sourceOwnerId = UUID.randomUUID();
        UUID targetOwnerId = UUID.randomUUID();
        seedUser(sourceOwnerId);
        seedUser(targetOwnerId);

        BigDecimal initialSourceBalance = TRANSFER_AMOUNT
                .multiply(BigDecimal.valueOf(threadCount))
                .add(new BigDecimal("100.00")); // margen -- no queremos tocar BR-001 aquí
        UUID sourceId = seedAccount(sourceOwnerId, initialSourceBalance);
        UUID targetId = seedAccount(targetOwnerId, BigDecimal.ZERO);

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            String idempotencyKey = "concurrency-test-" + UUID.randomUUID();
            Callable<Void> task = () -> {
                readyLatch.countDown();
                startLatch.await(); // TODOS los hilos esperan la MISMA señal de arranque
                TransferMoneyCommand command = TransferMoneyCommand.forInternal(
                        sourceId, sourceOwnerId, targetId, TRANSFER_AMOUNT, idempotencyKey);
                transferMoney.execute(command);
                return null;
            };
            futures.add(executor.submit(task));
        }

        boolean allThreadsReady = readyLatch.await(10, TimeUnit.SECONDS);
        assertThat(allThreadsReady).as("Todos los hilos deben estar listos antes de liberar la señal").isTrue();

        startLatch.countDown(); // libera los N hilos simultáneamente

        List<Exception> failures = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                failures.add(e);
            }
        }
        executor.shutdown();

        assertThat(failures)
                .as("Ninguna transferencia debe fallar (saldo suficiente, sin condición de negocio en juego): %s", failures)
                .isEmpty();

        AccountEntity finalSource = accountJpaRepository.findById(sourceId).orElseThrow();
        AccountEntity finalTarget = accountJpaRepository.findById(targetId).orElseThrow();

        BigDecimal expectedDebited = TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(threadCount));
        assertThat(finalSource.getBalance())
                .as("Balance de ORIGEN debe reflejar EXACTAMENTE %d débitos -- ni lost update ni doble débito", threadCount)
                .isEqualByComparingTo(initialSourceBalance.subtract(expectedDebited));
        assertThat(finalTarget.getBalance())
                .as("Balance de DESTINO debe reflejar EXACTAMENTE %d créditos", threadCount)
                .isEqualByComparingTo(expectedDebited);

        Integer completedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE source_account_id = ? AND status = 'COMPLETED'",
                Integer.class, sourceId);
        assertThat(completedCount)
                .as("Debe existir EXACTAMENTE una Transaction COMPLETED por hilo -- sin duplicados, sin pérdidas")
                .isEqualTo(threadCount);
    }

    private void seedUser(UUID id) {
        userJpaRepository.save(new UserEntity(id, "u-" + id + "@example.com", "User " + id, UserStatus.ACTIVE));
    }

    private UUID seedAccount(UUID ownerId, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        accountJpaRepository.save(new AccountEntity(id, ownerId, balance, AccountStatus.ACTIVE, 0L));
        return id;
    }
}