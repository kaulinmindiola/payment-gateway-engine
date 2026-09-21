package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransactionStatus;
import com.paymentgateway.engine.domain.model.TransferType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Espeja los campos de infrastructure.web.TransactionResponse -- duplicación
 * INTENCIONAL: application/ no puede depender de infrastructure/web/ (ArchUnit),
 * así que no se puede reutilizar ese DTO directamente para serializar la
 * respuesta cacheada de éxito.
 */
public record CachedTransferPayload(
        UUID id, UUID sourceAccountId, UUID targetAccountId, UUID targetProviderId, UUID targetBankId,
        String targetExternalReference, BigDecimal amount, TransferType transferType,
        TransactionStatus status, String failureReason
) {
    public static CachedTransferPayload from(Transaction transaction) {
        return new CachedTransferPayload(
                transaction.getId(), transaction.getSourceAccountId(), transaction.getTargetAccountId(),
                transaction.getTargetProviderId(), transaction.getTargetBankId(),
                transaction.getTargetExternalReference(), transaction.getAmount(),
                transaction.getTransferType(), transaction.getStatus(), transaction.getFailureReason());
    }
}