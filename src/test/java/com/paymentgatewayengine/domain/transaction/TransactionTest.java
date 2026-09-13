package com.paymentgatewayengine.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private final UUID sourceId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @Test
    @DisplayName("REQ-FUNC-014: una transacción se inicia en estado PENDING con el monto normalizado")
    void initiate_createsPendingTransaction() {
        Transaction tx = Transaction.initiate(sourceId, targetId, new BigDecimal("100.00"), "key-1");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(tx.getAmount()).isEqualByComparingTo("100.00");
        assertThat(tx.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("ASM-003: el monto de una transferencia debe ser estrictamente positivo")
    void initiate_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Transaction.initiate(sourceId, targetId, BigDecimal.ZERO, "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Transaction.initiate(sourceId, targetId, new BigDecimal("-1.00"), "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DV-012: complete() transiciona PENDING -> COMPLETED")
    void complete_transitionsToCompleted() {
        Transaction tx = Transaction.initiate(sourceId, targetId, new BigDecimal("50.00"), "key-1");

        tx.complete();

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("BR-010: fail() transiciona PENDING -> FAILED registrando la razón (p. ej. DECLINED)")
    void fail_transitionsToFailedWithReason() {
        Transaction tx = Transaction.initiate(sourceId, targetId, new BigDecimal("50.00"), "key-1");

        tx.fail("DECLINED");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(tx.getFailureReason()).isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("DV-012: una transacción COMPLETED no admite una segunda transición")
    void complete_thenComplete_throwsIllegalState() {
        Transaction tx = Transaction.initiate(sourceId, targetId, new BigDecimal("50.00"), "key-1");
        tx.complete();

        assertThatThrownBy(tx::complete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("DV-012: una transacción FAILED no puede transicionar a COMPLETED")
    void fail_thenComplete_throwsIllegalState() {
        Transaction tx = Transaction.initiate(sourceId, targetId, new BigDecimal("50.00"), "key-1");
        tx.fail("INSUFFICIENT_FUNDS");

        assertThatThrownBy(tx::complete).isInstanceOf(IllegalStateException.class);
    }
}
