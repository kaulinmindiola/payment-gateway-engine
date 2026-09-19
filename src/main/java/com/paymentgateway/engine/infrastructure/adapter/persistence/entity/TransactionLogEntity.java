package com.paymentgateway.engine.infrastructure.adapter.persistence.entity;

import com.paymentgateway.engine.domain.model.LogStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_logs")
public class TransactionLogEntity {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LogStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TransactionLogEntity() { }

    public TransactionLogEntity(UUID id, UUID transactionId, LogStatus status, String detail) {
        this.id = id;
        this.transactionId = transactionId;
        this.status = status;
        this.detail = detail;
    }

    // Append-only — sin método de actualización, intencional (Sección 5 del contexto).

    public UUID getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public LogStatus getStatus() { return status; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}