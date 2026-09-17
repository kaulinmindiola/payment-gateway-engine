package com.paymentgateway.engine.domain.port;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public final class AuthorizationRequest {

    private final UUID sourceAccountId;
    private final UUID targetProviderId;
    private final UUID targetBankId;
    private final String targetExternalReference;
    private final BigDecimal amount;
    private final String idempotencyKey;

    private AuthorizationRequest(UUID sourceAccountId, UUID targetProviderId, UUID targetBankId,
                                  String targetExternalReference, BigDecimal amount, String idempotencyKey) {
        this.sourceAccountId = sourceAccountId;
        this.targetProviderId = targetProviderId;
        this.targetBankId = targetBankId;
        this.targetExternalReference = targetExternalReference;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public static AuthorizationRequest of(UUID sourceAccountId, UUID targetProviderId, UUID targetBankId,
                                           String targetExternalReference, BigDecimal amount,
                                           String idempotencyKey) {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(targetProviderId, "targetProviderId must not be null");
        Objects.requireNonNull(targetBankId, "targetBankId must not be null");
        if (targetExternalReference == null || targetExternalReference.isBlank()) {
            throw new IllegalArgumentException("targetExternalReference must not be blank");
        }
        Objects.requireNonNull(amount, "amount must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.HALF_EVEN);
        if (normalizedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be strictly positive");
        }
        return new AuthorizationRequest(sourceAccountId, targetProviderId, targetBankId,
                targetExternalReference, normalizedAmount, idempotencyKey);
    }

    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getTargetProviderId() { return targetProviderId; }
    public UUID getTargetBankId() { return targetBankId; }
    public String getTargetExternalReference() { return targetExternalReference; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
}