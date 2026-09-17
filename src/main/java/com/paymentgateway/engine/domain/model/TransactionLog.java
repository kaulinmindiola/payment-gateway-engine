package com.paymentgateway.engine.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TransactionLog {

    private final UUID id;
    private final UUID transactionId;
    private final LogStatus status;
    private final String detail;
    private final Instant createdAt;

    private TransactionLog(UUID id, UUID transactionId, LogStatus status, String detail, Instant createdAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.status = status;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public static TransactionLog create(UUID transactionId, LogStatus status, String detail) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        requireNonBlankDetail(detail);
        return new TransactionLog(UUID.randomUUID(), transactionId, status, detail, Instant.now());
    }

    public static TransactionLog reconstitute(UUID id, UUID transactionId, LogStatus status,
                                               String detail, Instant createdAt) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireNonBlankDetail(detail);
        return new TransactionLog(id, transactionId, status, detail, createdAt);
    }

    private static void requireNonBlankDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail must not be blank");
        }
    }

    public UUID getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public LogStatus getStatus() { return status; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}