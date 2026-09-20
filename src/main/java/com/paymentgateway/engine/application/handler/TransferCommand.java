package com.paymentgateway.engine.application.handler;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class TransferCommand {

    private final UUID sourceAccountId;
    private final UUID requestingUserId;
    private final UUID targetAccountId;
    private final BigDecimal amount;
    private final String idempotencyKey;

    private TransferCommand(UUID sourceAccountId, UUID requestingUserId, UUID targetAccountId,
                             BigDecimal amount, String idempotencyKey) {
        this.sourceAccountId = sourceAccountId;
        this.requestingUserId = requestingUserId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * INTERNAL únicamente (Fase 5). forExternal(...) se añade en Fase 8
     * -- nuevo factory method + campos adicionales, sin tocar esta clase
     * ni el contrato de TransferHandler (ver Decisión 1).
     */
    public static TransferCommand forInternal(UUID sourceAccountId, UUID requestingUserId,
                                               UUID targetAccountId, BigDecimal amount, String idempotencyKey) {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(requestingUserId, "requestingUserId must not be null");
        Objects.requireNonNull(targetAccountId, "targetAccountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        return new TransferCommand(sourceAccountId, requestingUserId, targetAccountId, amount, idempotencyKey);
    }

    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getRequestingUserId() { return requestingUserId; }
    public UUID getTargetAccountId() { return targetAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
}