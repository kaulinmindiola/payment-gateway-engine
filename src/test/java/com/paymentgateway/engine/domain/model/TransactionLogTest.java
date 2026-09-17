package com.paymentgateway.engine.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionLogTest {

    private final UUID transactionId = UUID.randomUUID();

    @Test
    void create_generatesIdAndCreatedAt() {
        TransactionLog log = TransactionLog.create(transactionId, LogStatus.PENDING, "Transaction created");

        assertThat(log.getId()).isNotNull();
        assertThat(log.getTransactionId()).isEqualTo(transactionId);
        assertThat(log.getStatus()).isEqualTo(LogStatus.PENDING);
        assertThat(log.getDetail()).isEqualTo("Transaction created");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void create_withBlankDetail_throws() {
        assertThatThrownBy(() -> TransactionLog.create(transactionId, LogStatus.PENDING, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitute_withAllFields_succeeds() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        TransactionLog log = TransactionLog.reconstitute(
                id, transactionId, LogStatus.COMPLETED, "Completed OK", createdAt);

        assertThat(log.getId()).isEqualTo(id);
        assertThat(log.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void reconstitute_withNullCreatedAt_throws() {
        assertThatThrownBy(() -> TransactionLog.reconstitute(
                UUID.randomUUID(), transactionId, LogStatus.COMPLETED, "detail", null))
                .isInstanceOf(NullPointerException.class);
    }
}