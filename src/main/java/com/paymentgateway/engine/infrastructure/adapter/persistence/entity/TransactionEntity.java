package com.paymentgateway.engine.infrastructure.adapter.persistence.entity;

import com.paymentgateway.engine.domain.model.TransactionStatus;
import com.paymentgateway.engine.domain.model.TransferType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 10)
    private TransferType transferType;

    @Column(name = "target_account_id")
    private UUID targetAccountId;

    @Column(name = "target_provider_id")
    private UUID targetProviderId;

    @Column(name = "target_bank_id")
    private UUID targetBankId;

    @Column(name = "target_external_reference")
    private String targetExternalReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionEntity() { }

    public TransactionEntity(UUID id, UUID sourceAccountId, BigDecimal amount, String idempotencyKey,
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

    /** Solo status/failureReason cambian tras la creación (PENDING → COMPLETED/FAILED). */
    public void applyChangesFrom(TransactionStatus newStatus, String newFailureReason) {
        this.status = newStatus;
        this.failureReason = newFailureReason;
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
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}