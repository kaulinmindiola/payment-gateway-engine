package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.domain.model.*;
import com.paymentgateway.engine.infrastructure.adapter.persistence.TransactionJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.JpaTransactionRepositoryAdapter;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager; // ajusta al import real confirmado en tu entorno
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaTransactionRepositoryAdapterIT extends AbstractPersistenceIntegrationTest {

    @Autowired
    private TransactionJpaRepository transactionJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private JpaTransactionRepositoryAdapter adapter;

    private JpaTransactionRepositoryAdapter adapter() {
        if (adapter == null) {
            adapter = new JpaTransactionRepositoryAdapter(transactionJpaRepository);
        }
        return adapter;
    }

    private UUID seedAccount() {
        UUID userId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new UserEntity(userId, "u-" + userId + "@example.com", "User", UserStatus.ACTIVE));
        UUID accountId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new AccountEntity(accountId, userId, new BigDecimal("1000.00"), AccountStatus.ACTIVE, 0L));
        return accountId;
    }

    @Test
    void save_newInternalTransaction_persistsRow() {
        UUID source = seedAccount();
        UUID target = seedAccount();
        entityManager.flush();
        entityManager.clear();

        Transaction tx = Transaction.createInternal(source, target, new BigDecimal("100.00"), "key-tx-1");
        adapter().save(tx);
        entityManager.flush();
        entityManager.clear();

        TransactionEntity persisted = entityManager.find(TransactionEntity.class, tx.getId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    void save_existingTransaction_updatesStatusWithoutOverwritingCreatedAt() {
        UUID source = seedAccount();
        UUID target = seedAccount();
        entityManager.flush();
        entityManager.clear();

        Transaction tx = Transaction.createInternal(source, target, new BigDecimal("50.00"), "key-tx-2");
        adapter().save(tx);
        entityManager.flush();

        Instant originalCreatedAt = entityManager.find(TransactionEntity.class, tx.getId()).getCreatedAt();
        entityManager.clear();

        Transaction reloaded = adapter().findById(tx.getId()).orElseThrow();
        reloaded.markCompleted();
        adapter().save(reloaded);
        entityManager.flush();
        entityManager.clear();

        TransactionEntity updated = entityManager.find(TransactionEntity.class, tx.getId());
        assertThat(updated.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    @Test
    void findByIdempotencyKey_forExistingKey_returnsTransaction() {
        UUID source = seedAccount();
        UUID target = seedAccount();
        entityManager.flush();
        entityManager.clear();

        Transaction tx = Transaction.createInternal(source, target, new BigDecimal("30.00"), "key-tx-3");
        adapter().save(tx);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter().findByIdempotencyKey("key-tx-3"))
                .isPresent()
                .get()
                .extracting(Transaction::getId)
                .isEqualTo(tx.getId());
    }

    @Test
    void findByIdempotencyKey_forUnknownKey_returnsEmpty() {
        assertThat(adapter().findByIdempotencyKey("does-not-exist")).isEmpty();
    }

    /**
     * RISK-012: backstop de base de datos. Simula un bypass del dominio
     * (insert SQL crudo, como podría ocurrir por un bug de migración o acceso
     * directo a la BD) con campos target mixtos para transfer_type=INTERNAL
     * (target_account_id Y target_provider_id/target_bank_id a la vez).
     * El dominio (Fase 2, Transaction.reconstitute) ya rechaza esto en memoria;
     * este test prueba que Postgres también lo rechaza, de forma independiente.
     */
    @Test
void findById_forExistingTransaction_returnsTransaction() {
    UUID source = seedAccount();
    UUID target = seedAccount();
    entityManager.flush();
    entityManager.clear();

    Transaction tx = Transaction.createInternal(source, target, new BigDecimal("75.00"), "key-tx-findbyid");
    adapter().save(tx);
    entityManager.flush();
    entityManager.clear();

    assertThat(adapter().findById(tx.getId()))
            .isPresent()
            .get()
            .extracting(Transaction::getAmount)
            .satisfies(amount -> assertThat((BigDecimal) amount).isEqualByComparingTo("75.00"));
}

@Test
void findById_forUnknownId_returnsEmpty() {
    assertThat(adapter().findById(UUID.randomUUID())).isEmpty();
}
    @Test
    void checkConstraint_rejectsInconsistentTargetFields_evenBypassingDomain() {
        UUID source = seedAccount();
        UUID target = seedAccount();

        UUID providerId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new ProviderEntity(providerId, "SWIFT-demo-" + providerId, "SWIFT Demo", ProviderStatus.ACTIVE));

        UUID bankId = UUID.randomUUID();
        entityManager.persistAndFlush(new ExternalBankEntity(
                bankId, providerId, "DE-001-" + bankId, "Demo Bank", "DE", "EUR", ExternalBankStatus.ACTIVE));

        entityManager.flush();

        String insertSql = """
                INSERT INTO transactions (
                    id, source_account_id, amount, idempotency_key, status, failure_reason,
                    transfer_type, target_account_id, target_provider_id, target_bank_id,
                    target_external_reference, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        assertThatThrownBy(() -> jdbcTemplate.update(insertSql,
                UUID.randomUUID(), source, new BigDecimal("10.00"), "key-check-violation",
                "PENDING", null, "INTERNAL",
                target, providerId, bankId, "REF-SHOULD-NOT-COEXIST",   // mixto: INTERNAL con target_account_id Y campos EXTERNAL
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_transactions_target_exclusivity");
    }
}