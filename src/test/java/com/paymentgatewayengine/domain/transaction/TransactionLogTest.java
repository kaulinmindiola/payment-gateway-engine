package com.paymentgatewayengine.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionLogTest {

    @Test
    @DisplayName("REQ-FUNC-019: record() crea una entrada inmutable con timestamp de creación")
    void record_createsLogEntryWithTimestamp() {
        UUID transactionId = UUID.randomUUID();

        TransactionLog log = TransactionLog.record(transactionId, LogStatus.APPROVED, "autorizado por el proveedor externo");

        assertThat(log.getTransactionId()).isEqualTo(transactionId);
        assertThat(log.getStatus()).isEqualTo(LogStatus.APPROVED);
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("REQ-FUNC-019: detail es opcional (nullable)")
    void record_allowsNullDetail() {
        TransactionLog log = TransactionLog.record(UUID.randomUUID(), LogStatus.PENDING, null);

        assertThat(log.getDetail()).isNull();
    }
}
