package com.paymentgateway.engine.domain.model;

import com.paymentgateway.engine.domain.exception.InvalidAmountException;
import com.paymentgateway.engine.domain.exception.InvalidTransactionTargetException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public final class Transaction {

    private final UUID id;
    private final UUID sourceAccountId;
    private final BigDecimal amount;
    private final String idempotencyKey;
    private TransactionStatus status;
    private String failureReason;
    private final TransferType transferType;
    private final UUID targetAccountId;
    private final UUID targetProviderId;
    private final UUID targetBankId;
    private final String targetExternalReference;

    private Transaction(UUID id, UUID sourceAccountId, BigDecimal amount, String idempotencyKey,
                         TransactionStatus status, String failureReason, TransferType transferType,
                         UUID targetAccountId, UUID targetProviderId, UUID targetBankId,
                         String targetExternalReference) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.failureReason = failureReason;
        this.transferType = transferType;
        this.targetAccountId = targetAccountId;
        this.targetProviderId = targetProviderId;
        this.targetBankId = targetBankId;
        this.targetExternalReference = targetExternalReference;
    }

    public static Transaction createInternal(UUID sourceAccountId, UUID targetAccountId,
                                              BigDecimal amount, String idempotencyKey) {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(targetAccountId, "targetAccountId must not be null");
        BigDecimal normalizedAmount = requirePositiveAmount(amount);
        String normalizedKey = requireNonBlankKey(idempotencyKey);

        return new Transaction(
                UUID.randomUUID(), sourceAccountId, normalizedAmount, normalizedKey,
                TransactionStatus.PENDING, null, TransferType.INTERNAL,
                targetAccountId, null, null, null
        );
    }

    public static Transaction createExternal(UUID sourceAccountId, UUID targetProviderId, UUID targetBankId,
                                              String targetExternalReference, BigDecimal amount,
                                              String idempotencyKey) {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(targetProviderId, "targetProviderId must not be null");
        Objects.requireNonNull(targetBankId, "targetBankId must not be null");
        if (targetExternalReference == null || targetExternalReference.isBlank()) {
            throw new IllegalArgumentException("targetExternalReference must not be blank");
        }
        BigDecimal normalizedAmount = requirePositiveAmount(amount);
        String normalizedKey = requireNonBlankKey(idempotencyKey);

        return new Transaction(
                UUID.randomUUID(), sourceAccountId, normalizedAmount, normalizedKey,
                TransactionStatus.PENDING, null, TransferType.EXTERNAL,
                null, targetProviderId, targetBankId, targetExternalReference
        );
    }

    public static Transaction reconstitute(UUID id, UUID sourceAccountId, BigDecimal amount, String idempotencyKey,
                                            TransactionStatus status, String failureReason, TransferType transferType,
                                            UUID targetAccountId, UUID targetProviderId, UUID targetBankId,
                                            String targetExternalReference) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(transferType, "transferType must not be null");

        validateTargetInvariant(transferType, targetAccountId, targetProviderId, targetBankId, targetExternalReference);

        return new Transaction(
                id, sourceAccountId, amount.setScale(2, RoundingMode.HALF_EVEN), idempotencyKey,
                status, failureReason, transferType,
                targetAccountId, targetProviderId, targetBankId, targetExternalReference
        );
    }

    private static void validateTargetInvariant(TransferType transferType, UUID targetAccountId,
                                                 UUID targetProviderId, UUID targetBankId,
                                                 String targetExternalReference) {
        boolean hasInternalTarget = targetAccountId != null;
        boolean hasAnyExternalField = targetProviderId != null || targetBankId != null
                || targetExternalReference != null;
        boolean hasFullExternalTarget = targetProviderId != null && targetBankId != null
                && targetExternalReference != null && !targetExternalReference.isBlank();

        if (transferType == TransferType.INTERNAL) {
            if (!hasInternalTarget || hasAnyExternalField) {
                throw new InvalidTransactionTargetException(
                        "INTERNAL transaction must set targetAccountId only, with no EXTERNAL target fields");
            }
        } else { // EXTERNAL
            if (hasInternalTarget || !hasFullExternalTarget) {
                throw new InvalidTransactionTargetException(
                        "EXTERNAL transaction must set targetProviderId, targetBankId and targetExternalReference, with no targetAccountId");
            }
        }
    }

    public void markCompleted() {
        requirePending();
        this.status = TransactionStatus.COMPLETED;
    }

    public void markFailed(String reason) {
        requirePending();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("failureReason must not be blank when marking a transaction as FAILED");
        }
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
    }

    private void requirePending() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "Transaction " + this.id + " is not PENDING (current status: " + this.status + ")");
        }
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_EVEN);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Transaction amount must be strictly positive");
        }
        return normalized;
    }

    private static String requireNonBlankKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        return idempotencyKey;
    }

    public UUID getId() { return id; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public TransactionStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public TransferType getTransferType() { return transferType; }
    public UUID getTargetAccountId() { return targetAccountId; }
    public UUID getTargetProviderId() { return targetProviderId; }
    public UUID getTargetBankId() { return targetBankId; }
    public String getTargetExternalReference() { return targetExternalReference; }
}