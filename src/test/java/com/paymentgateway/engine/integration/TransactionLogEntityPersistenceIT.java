package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.domain.model.*;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionLogEntityPersistenceIT extends AbstractPersistenceIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void transactionLogEntity_persistsAndReloadsCorrectly() {
        UUID userId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new UserEntity(userId, "u-" + userId + "@example.com", "User", UserStatus.ACTIVE));

        UUID sourceId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new AccountEntity(sourceId, userId, new BigDecimal("100.00"), AccountStatus.ACTIVE, 0L));
        UUID targetId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new AccountEntity(targetId, userId, new BigDecimal("100.00"), AccountStatus.ACTIVE, 0L));

        UUID txId = UUID.randomUUID();
        entityManager.persistAndFlush(new TransactionEntity(
                txId, sourceId, new BigDecimal("10.00"), "key-log-smoke",
                TransactionStatus.PENDING, null, TransferType.INTERNAL,
                targetId, null, null, null, Instant.now().truncatedTo(ChronoUnit.MICROS)));

        UUID logId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new TransactionLogEntity(logId, txId, LogStatus.PENDING, "Transaction created"));

        entityManager.clear();

        TransactionLogEntity reloaded = entityManager.find(TransactionLogEntity.class, logId);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getTransactionId()).isEqualTo(txId);
        assertThat(reloaded.getStatus()).isEqualTo(LogStatus.PENDING);
        assertThat(reloaded.getDetail()).isEqualTo("Transaction created");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }
}