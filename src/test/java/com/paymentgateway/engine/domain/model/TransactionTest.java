package com.paymentgateway.engine.domain.model;

import com.paymentgateway.engine.domain.exception.InvalidAmountException;
import com.paymentgateway.engine.domain.exception.InvalidTransactionTargetException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private final UUID source = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final UUID provider = UUID.randomUUID();
    private final UUID bank = UUID.randomUUID();

    @Nested
    class FactoryMethods {

        @Test
        void createInternal_buildsValidPendingTransaction() {
            Transaction tx = Transaction.createInternal(source, target, new BigDecimal("100.00"), "key-1");

            assertThat(tx.getTransferType()).isEqualTo(TransferType.INTERNAL);
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(tx.getTargetAccountId()).isEqualTo(target);
            assertThat(tx.getTargetProviderId()).isNull();
            assertThat(tx.getTargetBankId()).isNull();
            assertThat(tx.getTargetExternalReference()).isNull();
        }

        @Test
        void createExternal_buildsValidPendingTransaction() {
            Transaction tx = Transaction.createExternal(
                    source, provider, bank, "ES9121000418450200051332", new BigDecimal("50.00"), "key-2");

            assertThat(tx.getTransferType()).isEqualTo(TransferType.EXTERNAL);
            assertThat(tx.getTargetAccountId()).isNull();
            assertThat(tx.getTargetProviderId()).isEqualTo(provider);
            assertThat(tx.getTargetBankId()).isEqualTo(bank);
            assertThat(tx.getTargetExternalReference()).isEqualTo("ES9121000418450200051332");
        }

        @Test
        void createInternal_withNonPositiveAmount_throws() {
            assertThatThrownBy(() ->
                    Transaction.createInternal(source, target, BigDecimal.ZERO, "key-3"))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void createInternal_withBlankIdempotencyKey_throws() {
            assertThatThrownBy(() ->
                    Transaction.createInternal(source, target, new BigDecimal("10.00"), " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ReconstituteInvariant {

        @Test
        void reconstitute_internalWithCorrectTarget_succeeds() {
            Transaction tx = Transaction.reconstitute(
                    UUID.randomUUID(), source, new BigDecimal("10.00"), "key-4",
                    TransactionStatus.COMPLETED, null, TransferType.INTERNAL,
                    target, null, null, null);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }

        @Test
        void reconstitute_internalMissingTargetAccountId_throws() {
            assertThatThrownBy(() -> Transaction.reconstitute(
                    UUID.randomUUID(), source, new BigDecimal("10.00"), "key-5",
                    TransactionStatus.PENDING, null, TransferType.INTERNAL,
                    null, null, null, null))
                    .isInstanceOf(InvalidTransactionTargetException.class);
        }

        @Test
        void reconstitute_internalWithMixedTargetFields_throws() {
            // BR conceptual: caso "inválido mixto" explícito de la Sección 15/9 del contexto
            assertThatThrownBy(() -> Transaction.reconstitute(
                    UUID.randomUUID(), source, new BigDecimal("10.00"), "key-6",
                    TransactionStatus.PENDING, null, TransferType.INTERNAL,
                    target, provider, bank, "REF"))
                    .isInstanceOf(InvalidTransactionTargetException.class);
        }

        @Test
        void reconstitute_externalWithFullTarget_succeeds() {
            Transaction tx = Transaction.reconstitute(
                    UUID.randomUUID(), source, new BigDecimal("10.00"), "key-7",
                    TransactionStatus.PENDING, null, TransferType.EXTERNAL,
                    null, provider, bank, "REF");

            assertThat(tx.getTransferType()).isEqualTo(TransferType.EXTERNAL);
        }

        @Test
        void reconstitute_externalMissingBankId_throws() {
            assertThatThrownBy(() -> Transaction.reconstitute(
                    UUID.randomUUID(), source, new BigDecimal("10.00"), "key-8",
                    TransactionStatus.PENDING, null, TransferType.EXTERNAL,
                    null, provider, null, "REF"))
                    .isInstanceOf(InvalidTransactionTargetException.class);
        }

        @Test
        void reconstitute_externalWithTargetAccountIdAlsoSet_throws() {
            assertThatThrownBy(() -> Transaction.reconstitute(
                    UUID.randomUUID(), source, new BigDecimal("10.00"), "key-9",
                    TransactionStatus.PENDING, null, TransferType.EXTERNAL,
                    target, provider, bank, "REF"))
                    .isInstanceOf(InvalidTransactionTargetException.class);
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void markCompleted_fromPending_succeeds() {
            Transaction tx = Transaction.createInternal(source, target, new BigDecimal("10.00"), "key-10");
            tx.markCompleted();
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }

        @Test
        void markCompleted_twice_throws() {
            Transaction tx = Transaction.createInternal(source, target, new BigDecimal("10.00"), "key-11");
            tx.markCompleted();
            assertThatThrownBy(tx::markCompleted).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void markFailed_fromPending_setsReasonAndStatus() {
            Transaction tx = Transaction.createExternal(
                    source, provider, bank, "REF", new BigDecimal("10.00"), "key-12");
            tx.markFailed("DECLINED");

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
            assertThat(tx.getFailureReason()).isEqualTo("DECLINED");
        }

        @Test
        void markFailed_withBlankReason_throws() {
            Transaction tx = Transaction.createInternal(source, target, new BigDecimal("10.00"), "key-13");
            assertThatThrownBy(() -> tx.markFailed(" ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void markFailed_afterAlreadyCompleted_throws() {
            Transaction tx = Transaction.createInternal(source, target, new BigDecimal("10.00"), "key-14");
            tx.markCompleted();
            assertThatThrownBy(() -> tx.markFailed("SOME_REASON")).isInstanceOf(IllegalStateException.class);
        }
    }
}