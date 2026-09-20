package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransactionStatus;
import com.paymentgateway.engine.domain.model.TransferType;

import java.math.BigDecimal;
import java.util.UUID;

// idempotencyKey deliberadamente excluido -- campo de control interno,
// no información de negocio (Decisión 4).
public record TransactionResponse(
        UUID id,
        UUID sourceAccountId,
        UUID targetAccountId,
        UUID targetProviderId,
        UUID targetBankId,
        String targetExternalReference,
        BigDecimal amount,
        TransferType transferType,
        TransactionStatus status,
        String failureReason
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(), transaction.getSourceAccountId(), transaction.getTargetAccountId(),
                transaction.getTargetProviderId(), transaction.getTargetBankId(),
                transaction.getTargetExternalReference(), transaction.getAmount(),
                transaction.getTransferType(), transaction.getStatus(), transaction.getFailureReason());
    }
}