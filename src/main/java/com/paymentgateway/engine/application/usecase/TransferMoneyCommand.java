package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.model.TransferType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class TransferMoneyCommand {

    private final UUID sourceAccountId;
    private final UUID requestingUserId;
    private final TransferType transferType;
    private final UUID targetAccountId;         // solo INTERNAL
    private final UUID targetProviderId;        // solo EXTERNAL
    private final UUID targetBankId;            // solo EXTERNAL
    private final String targetExternalReference; // solo EXTERNAL
    private final BigDecimal amount;
    private final String idempotencyKey;

    private TransferMoneyCommand(UUID sourceAccountId, UUID requestingUserId, TransferType transferType,
                                  UUID targetAccountId, UUID targetProviderId, UUID targetBankId,
                                  String targetExternalReference, BigDecimal amount, String idempotencyKey) {
        this.sourceAccountId = sourceAccountId;
        this.requestingUserId = requestingUserId;
        this.transferType = transferType;
        this.targetAccountId = targetAccountId;
        this.targetProviderId = targetProviderId;
        this.targetBankId = targetBankId;
        this.targetExternalReference = targetExternalReference;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public static TransferMoneyCommand forInternal(UUID sourceAccountId, UUID requestingUserId,
                                                    UUID targetAccountId, BigDecimal amount, String idempotencyKey) {
        validateCommon(sourceAccountId, requestingUserId, amount, idempotencyKey);
        Objects.requireNonNull(targetAccountId, "targetAccountId must not be null");
        return new TransferMoneyCommand(sourceAccountId, requestingUserId, TransferType.INTERNAL,
                targetAccountId, null, null, null, amount, idempotencyKey);
    }

    public static TransferMoneyCommand forExternal(UUID sourceAccountId, UUID requestingUserId,
                                                    UUID targetProviderId, UUID targetBankId,
                                                    String targetExternalReference, BigDecimal amount,
                                                    String idempotencyKey) {
        validateCommon(sourceAccountId, requestingUserId, amount, idempotencyKey);
        Objects.requireNonNull(targetProviderId, "targetProviderId must not be null");
        Objects.requireNonNull(targetBankId, "targetBankId must not be null");
        if (targetExternalReference == null || targetExternalReference.isBlank()) {
            throw new IllegalArgumentException("targetExternalReference must not be blank");
        }
        return new TransferMoneyCommand(sourceAccountId, requestingUserId, TransferType.EXTERNAL,
                null, targetProviderId, targetBankId, targetExternalReference, amount, idempotencyKey);
    }

    private static void validateCommon(UUID sourceAccountId, UUID requestingUserId, BigDecimal amount, String idempotencyKey) {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(requestingUserId, "requestingUserId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }

    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getRequestingUserId() { return requestingUserId; }
    public TransferType getTransferType() { return transferType; }
    public UUID getTargetAccountId() { return targetAccountId; }
    public UUID getTargetProviderId() { return targetProviderId; }
    public UUID getTargetBankId() { return targetBankId; }
    public String getTargetExternalReference() { return targetExternalReference; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
}