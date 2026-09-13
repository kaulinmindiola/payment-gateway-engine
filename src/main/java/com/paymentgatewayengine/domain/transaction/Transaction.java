package com.paymentgatewayengine.domain.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de una transferencia entre dos cuentas. Modela su propio ciclo de
 * vida (DV-012): nace PENDING y transiciona una única vez a un estado
 * terminal (COMPLETED o FAILED, BR-010). Ninguna transición es válida una
 * vez alcanzado un estado terminal.
 *
 * No valida BR-007 (self-transfer): esa regla pertenece a la solicitud de
 * transferencia, verificada por TransferMoneyUseCase antes de invocar
 * initiate() (Fase 9), no a un invariante propio de esta entidad.
 */
public class Transaction {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_EVEN;

    private final UUID id;
    private final UUID sourceAccountId;
    private final UUID targetAccountId;
    private final BigDecimal amount;
    private final String idempotencyKey;
    private TransactionStatus status;
    private String failureReason;

    private Transaction(UUID id, UUID sourceAccountId, UUID targetAccountId, BigDecimal amount,
                         String idempotencyKey, TransactionStatus status, String failureReason) {
        this.id = Objects.requireNonNull(id, "id no puede ser null");
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId no puede ser null");
        this.targetAccountId = Objects.requireNonNull(targetAccountId, "targetAccountId no puede ser null");
        this.amount = normalize(Objects.requireNonNull(amount, "amount no puede ser null"));
        this.idempotencyKey = requireNonBlank(idempotencyKey, "idempotencyKey no puede ser vacío");
        this.status = Objects.requireNonNull(status, "status no puede ser null");
        this.failureReason = failureReason;
    }

    /**
     * Inicia una nueva transferencia en estado PENDING.
     *
     * @throws IllegalArgumentException si el monto no es estrictamente positivo (ASM-003)
     */
    public static Transaction initiate(UUID sourceAccountId, UUID targetAccountId,
                                        BigDecimal amount, String idempotencyKey) {
        BigDecimal normalized = normalize(Objects.requireNonNull(amount, "amount no puede ser null"));
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("El monto de la transferencia debe ser estrictamente positivo");
        }
        return new Transaction(UUID.randomUUID(), sourceAccountId, targetAccountId,
                normalized, idempotencyKey, TransactionStatus.PENDING, null);
    }

    /**
     * Reconstruye una transacción ya existente a partir de datos persistidos.
     * Uso exclusivo de los mappers de infraestructura (ADR-0001, Fase 3).
     */
    public static Transaction reconstitute(UUID id, UUID sourceAccountId, UUID targetAccountId,
                                            BigDecimal amount, String idempotencyKey,
                                            TransactionStatus status, String failureReason) {
        return new Transaction(id, sourceAccountId, targetAccountId, amount, idempotencyKey, status, failureReason);
    }

    /**
     * Marca la transacción como COMPLETED (autorización APPROVED + persistencia OK).
     *
     * @throws IllegalStateException si la transacción ya está en un estado terminal
     */
    public void complete() {
        requirePending();
        this.status = TransactionStatus.COMPLETED;
    }

    /**
     * Marca la transacción como FAILED con la razón indicada (BR-010: incluye
     * el caso DECLINED del proveedor externo, que es un resultado de negocio
     * válido, no un error técnico).
     *
     * @throws IllegalStateException si la transacción ya está en un estado terminal
     */
    public void fail(String reason) {
        requirePending();
        this.status = TransactionStatus.FAILED;
        this.failureReason = requireNonBlank(reason, "failureReason no puede ser vacío al fallar una transacción");
    }

    private void requirePending() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "No se puede transicionar una Transaction que ya está en estado terminal: " + this.status);
        }
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
