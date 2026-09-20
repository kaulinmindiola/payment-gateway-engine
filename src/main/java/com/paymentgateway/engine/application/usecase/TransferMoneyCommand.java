package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.model.TransferType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando de entrada, agnóstico al tipo de transferencia -- refleja lo que
 * llega crudo desde la API. TransferMoney lo traduce al comando específico
 * del handler correspondiente (TransferCommand para INTERNAL; su equivalente
 * EXTERNAL llega en Fase 8).
 */
public final class TransferMoneyCommand {

    private final UUID sourceAccountId;
    private final UUID requestingUserId;
    private final TransferType transferType;
    private final UUID targetAccountId; // solo INTERNAL en este alcance
    private final BigDecimal amount;
    private final String idempotencyKey;

    private TransferMoneyCommand(UUID sourceAccountId, UUID requestingUserId, TransferType transferType,
                                  UUID targetAccountId, BigDecimal amount, String idempotencyKey) {
        this.sourceAccountId = sourceAccountId;
        this.requestingUserId = requestingUserId;
        this.transferType = transferType;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public static TransferMoneyCommand of(UUID sourceAccountId, UUID requestingUserId, TransferType transferType,
                                           UUID targetAccountId, BigDecimal amount, String idempotencyKey) {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(requestingUserId, "requestingUserId must not be null");
        Objects.requireNonNull(transferType, "transferType must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        return new TransferMoneyCommand(sourceAccountId, requestingUserId, transferType,
                targetAccountId, amount, idempotencyKey);
    }

    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getRequestingUserId() { return requestingUserId; }
    public TransferType getTransferType() { return transferType; }
    public UUID getTargetAccountId() { return targetAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
}