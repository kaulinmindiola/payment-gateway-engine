package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.domain.model.*;
import com.paymentgateway.engine.infrastructure.adapter.persistence.JpaTransactionLogRepositoryAdapter;
import com.paymentgateway.engine.infrastructure.adapter.persistence.TransactionLogJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager; // ajustar al import confirmado en tu entorno

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaTransactionLogRepositoryAdapterIT extends AbstractPersistenceIntegrationTest {

    @Autowired
    private TransactionLogJpaRepository transactionLogJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private JpaTransactionLogRepositoryAdapter adapter;

    private JpaTransactionLogRepositoryAdapter adapter() {
        if (adapter == null) {
            adapter = new JpaTransactionLogRepositoryAdapter(transactionLogJpaRepository);
        }
        return adapter;
    }

    private UUID seedTransaction() {
        UUID userId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new UserEntity(userId, "u-" + userId + "@example.com", "User", UserStatus.ACTIVE));
        UUID source = UUID.randomUUID();
        entityManager.persistAndFlush(
                new AccountEntity(source, userId, new BigDecimal("100.00"), AccountStatus.ACTIVE, 0L));
        UUID target = UUID.randomUUID();
        entityManager.persistAndFlush(
                new AccountEntity(target, userId, new BigDecimal("100.00"), AccountStatus.ACTIVE, 0L));

        UUID txId = UUID.randomUUID();
        entityManager.persistAndFlush(new TransactionEntity(
                txId, source, new BigDecimal("10.00"), "key-log-" + txId,
                TransactionStatus.PENDING, null, TransferType.INTERNAL,
                target, null, null, null));

        entityManager.flush();
        return txId;
    }

    @Test
    void save_persistsLogWithCreatedAtGenerated() {
        UUID txId = seedTransaction();
        entityManager.clear();

        TransactionLog log = TransactionLog.create(txId, LogStatus.PENDING, "Transaction created");
        adapter().save(log);
        entityManager.flush();
        entityManager.clear();

        TransactionLogEntity persisted = entityManager.find(TransactionLogEntity.class, log.getId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getTransactionId()).isEqualTo(txId);
        assertThat(persisted.getStatus()).isEqualTo(LogStatus.PENDING);
        assertThat(persisted.getDetail()).isEqualTo("Transaction created");
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    void save_multipleLogsForSameTransaction_appendsRowsRatherThanOverwriting() {
        // Invariante central del append-only: dos logs de la MISMA transacción
        // deben coexistir como dos filas, no colapsar en una (contraste directo
        // con Account/Transaction, donde save() sobre el mismo id actualiza).
        UUID txId = seedTransaction();
        entityManager.clear();

        adapter().save(TransactionLog.create(txId, LogStatus.PENDING, "Transaction created"));
        adapter().save(TransactionLog.create(txId, LogStatus.COMPLETED, "Transaction completed"));
        entityManager.flush();
        entityManager.clear();

        List<TransactionLogEntity> allLogs = transactionLogJpaRepository.findAll();
        long logsForThisTx = allLogs.stream()
                .filter(l -> l.getTransactionId().equals(txId))
                .count();

        assertThat(logsForThisTx).isEqualTo(2);
    }
}