package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferMoneyCommand;
import com.paymentgateway.engine.application.usecase.TransferOutcome;
import com.paymentgateway.engine.domain.model.AccountStatus;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransactionStatus;
import com.paymentgateway.engine.domain.model.UserStatus;
import com.paymentgateway.engine.infrastructure.adapter.http.AuthorizationTechnicalException;
import com.paymentgateway.engine.infrastructure.adapter.persistence.AccountJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.UserJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.AccountEntity;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.UserEntity;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CS-02: ninguna combinación de timeout + retry + circuit breaker produce
 * una transferencia duplicada. Todo pasa por el proxy real de Spring
 * (TransferMoney inyectado) para ejercitar los aspectos de Resilience4j.
 */
class ExternalTransferResilienceIT extends AbstractExternalProviderIntegrationTest {

    private static final String APPROVED_JSON = """
            {"status":"APPROVED","providerReference":"AUTH-RUNTIME","reason":null,"processedAt":"2026-01-01T00:00:00Z"}
            """;

    @Autowired private TransferMoney transferMoney;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private AccountJpaRepository accountJpaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    private record Fixture(UUID ownerId, UUID accountId, UUID providerId, UUID bankId) {}

    @BeforeEach
    void isolateResilienceState() {
        // Se ejecuta DESPUÉS de resetWireMockState() de la clase base.
        circuitBreakerRegistry.circuitBreaker("authorizationProvider").reset();
        wireMock.resetRequests();
        wireMock.resetScenarios();
    }

    // ---------- fixtures ----------

    private Fixture fixture(String providerCode) {
        UUID ownerId = UUID.randomUUID();
        userJpaRepository.save(new UserEntity(ownerId, "u-" + ownerId + "@example.com", "User", UserStatus.ACTIVE));
        UUID accountId = UUID.randomUUID();
        accountJpaRepository.save(new AccountEntity(accountId, ownerId, new BigDecimal("100.00"), AccountStatus.ACTIVE, 0L));

        jdbcTemplate.update(
                "INSERT INTO providers (id, code, name, status) VALUES (?, ?, ?, 'ACTIVE') ON CONFLICT (code) DO NOTHING",
                UUID.randomUUID(), providerCode, providerCode + " rail");
        UUID providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE code = ?", UUID.class, providerCode);

        UUID bankId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO external_banks (id, provider_id, code, name, country, currency, status) "
                        + "VALUES (?, ?, ?, 'Test Bank', 'DE', 'EUR', 'ACTIVE')",
                bankId, providerId, "BANK-" + bankId);
        return new Fixture(ownerId, accountId, providerId, bankId);
    }

    private TransferMoneyCommand command(Fixture f, String reference, String key) {
        return TransferMoneyCommand.forExternal(f.accountId(), f.ownerId(), f.providerId(), f.bankId(),
                reference, new BigDecimal("10.00"), key);
    }

    private int providerCalls() {
        return wireMock.findAll(postRequestedFor(urlEqualTo("/v1/authorizations"))).size();
    }

    private int transactionsWithKey(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE idempotency_key = ?", Integer.class, key);
    }

    private BigDecimal balance(UUID accountId) {
        return accountJpaRepository.findById(accountId).orElseThrow().getBalance();
    }

    private String newKey() {
        return "resilience-" + UUID.randomUUID();
    }

    // ---------- tests ----------

    @Test
    void approved_completesAndDebitsExactlyOnce() {
        Fixture f = fixture("SWIFT-demo");
        String key = newKey();

        TransferOutcome outcome = transferMoney.execute(command(f, "ES9121000418450200051332", key));

        Transaction tx = ((TransferOutcome.Executed) outcome).transaction();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(balance(f.accountId())).isEqualByComparingTo("90.00");
        assertThat(providerCalls()).isEqualTo(1);
    }

    @Test
    void declined_isBusinessResult_neverRetried() {
        Fixture f = fixture("SWIFT-demo");

        TransferOutcome outcome = transferMoney.execute(command(f, "REF-DECLINE", newKey()));

        Transaction tx = ((TransferOutcome.Executed) outcome).transaction();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(tx.getFailureReason()).isEqualTo("DECLINED");
        assertThat(balance(f.accountId())).isEqualByComparingTo("100.00");
        assertThat(providerCalls())
                .as("BR-010: DECLINED es resultado de negocio, NO dispara retry")
                .isEqualTo(1);
    }

    @Test
    void flakyProvider_recoversWithinRetryPolicy_singleTransaction() {
        // Scenario: 503 -> timeout (3s > readTimeout 2s) -> APPROVED
        Fixture f = fixture("RAILS-flaky");
        String key = newKey();

        TransferOutcome outcome = transferMoney.execute(command(f, "REF-FLAKY", key));

        assertThat(((TransferOutcome.Executed) outcome).transaction().getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);
        assertThat(providerCalls())
                .as("maxAttempts=3: si esto da 1, los aspectos de Resilience4j NO están activos")
                .isEqualTo(3);
        assertThat(transactionsWithKey(key)).isEqualTo(1);
        assertThat(balance(f.accountId())).isEqualByComparingTo("90.00");
    }

    @Test
    void persistent5xx_exhaustsRetries_persistsNothing_thenClientRetrySucceedsOnce() {
        Fixture f = fixture("RAILS-flaky");
        String key = newKey();
        TransferMoneyCommand cmd = command(f, "REF-CB-ALWAYS-FAIL", key);

        // 1) Fallo técnico persistente: 3 intentos, nada persistido, sin débito.
        assertThatThrownBy(() -> transferMoney.execute(cmd))
                .isInstanceOf(AuthorizationTechnicalException.class);
        assertThat(providerCalls()).isEqualTo(3);
        assertThat(transactionsWithKey(key)).isZero();
        assertThat(balance(f.accountId())).isEqualByComparingTo("100.00");
        assertThat(redisTemplate.hasKey("idempotency:transfer:" + key))
                .as("La key se libera tras un fallo técnico")
                .isFalse();

        // 2) El proveedor se recupera (stub más reciente con misma prioridad gana).
        wireMock.stubFor(post(urlPathEqualTo("/v1/authorizations")).atPriority(1)
                .withHeader("X-Provider-Code", equalTo("RAILS-flaky"))
                .withRequestBody(matchingJsonPath("$.targetExternalReference", matching(".*-CB-ALWAYS-FAIL$")))
                .willReturn(okJson(APPROVED_JSON)));

        // 3) Reintento del CLIENTE con la MISMA key: no hay 409, se ejecuta una sola vez.
        TransferOutcome retry = transferMoney.execute(cmd);

        assertThat(((TransferOutcome.Executed) retry).transaction().getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transactionsWithKey(key))
                .as("CS-02: exactamente UNA Transaction tras fallo técnico + reintento del cliente")
                .isEqualTo(1);
        assertThat(balance(f.accountId())).isEqualByComparingTo("90.00");
    }

    @Test
    void persistentTimeout_exhaustsRetries_persistsNothing() {
        Fixture f = fixture("SWIFT-demo");
        String key = newKey();
        wireMock.stubFor(post(urlPathEqualTo("/v1/authorizations")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.targetExternalReference", matching(".*-ALWAYS-TIMEOUT$")))
                .willReturn(aResponse().withFixedDelay(3000).withStatus(200)));

        assertThatThrownBy(() -> transferMoney.execute(command(f, "REF-ALWAYS-TIMEOUT", key)))
                .isInstanceOf(AuthorizationTechnicalException.class);

        assertThat(providerCalls()).isEqualTo(3);
        assertThat(transactionsWithKey(key)).isZero();
        assertThat(balance(f.accountId())).isEqualByComparingTo("100.00");
    }

    @Test
    void circuitBreaker_opensAfterThreshold_andStopsCallingProvider() {
        Fixture f = fixture("RAILS-flaky");

        for (int i = 0; i < 10; i++) {
            try {
                transferMoney.execute(command(f, "REF-CB-ALWAYS-FAIL", newKey()));
            } catch (AuthorizationTechnicalException expected) {
                // fallo técnico esperado en cada intento
            }
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("authorizationProvider");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int callsBeforeOpenCircuitRequest = providerCalls();

        assertThatThrownBy(() -> transferMoney.execute(command(f, "REF-CB-ALWAYS-FAIL", newKey())))
                .isInstanceOf(AuthorizationTechnicalException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class); // Gap A: traducida por el fallback

        assertThat(providerCalls())
                .as("Con el circuito OPEN, el proveedor NO debe recibir nuevas llamadas")
                .isEqualTo(callsBeforeOpenCircuitRequest);
        assertThat(balance(f.accountId())).isEqualByComparingTo("100.00");
    }
}