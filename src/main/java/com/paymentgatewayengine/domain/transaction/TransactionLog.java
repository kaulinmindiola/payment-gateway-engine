package com.paymentgatewayengine.domain.transaction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada de auditoría inmutable del ciclo de vida de una Transaction
 * (REQ-FUNC-019, NFR-AUDIT-01). Append-only: no expone forma de modificar
 * una instancia una vez creada, ni updatedAt.
 */
public final class TransactionLog {

    private final UUID id;
    private final UUID transactionId;
    private final LogStatus status;
    private final String detail;
    private final Instant createdAt;

    private TransactionLog(UUID id, UUID transactionId, LogStatus status, String detail, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id no puede ser null");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId no puede ser null");
        this.status = Objects.requireNonNull(status, "status no puede ser null");
        this.detail = detail;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser null");
    }

    /**
     * Registra una nueva entrada de auditoría con timestamp actual.
     *
     * @param detail texto libre opcional (nullable)
     */
    public static TransactionLog record(UUID transactionId, LogStatus status, String detail) {
        return new TransactionLog(UUID.randomUUID(), transactionId, status, detail, Instant.now());
    }

    /**
     * Reconstruye una entrada ya existente a partir de datos persistidos.
     * Uso exclusivo de los mappers de infraestructura (ADR-0001, Fase 3).
     */
    public static TransactionLog reconstitute(UUID id, UUID transactionId, LogStatus status,
                                               String detail, Instant createdAt) {
        return new TransactionLog(id, transactionId, status, detail, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public LogStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
