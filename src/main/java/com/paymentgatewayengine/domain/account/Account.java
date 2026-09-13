package com.paymentgatewayengine.domain.account;

import com.paymentgatewayengine.domain.exception.InactiveAccountException;
import com.paymentgatewayengine.domain.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta bancaria del dominio. Encapsula sus invariantes de negocio:
 * - El balance nunca puede ser negativo (BR-001).
 * - Solo una cuenta ACTIVE puede participar en débitos/créditos (BR-003).
 *
 * CON-005: el balance se representa siempre como BigDecimal(19,2) con
 * redondeo HALF_EVEN — nunca float/double.
 */
public class Account {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_EVEN;

    private final UUID id;
    private final String ownerId;
    private BigDecimal balance;
    private AccountStatus status;

    private Account(UUID id, String ownerId, BigDecimal balance, AccountStatus status) {
        this.id = Objects.requireNonNull(id, "id no puede ser null");
        this.ownerId = requireNonBlank(ownerId, "ownerId no puede ser vacío");
        this.balance = normalize(Objects.requireNonNull(balance, "balance no puede ser null"));
        this.status = Objects.requireNonNull(status, "status no puede ser null");
    }

    /**
     * Crea una cuenta nueva. Toda cuenta nueva se crea en estado ACTIVE (ASM-005).
     *
     * @throws IllegalArgumentException si el saldo inicial es negativo (BR-001, REQ-FUNC-003)
     */
    public static Account open(String ownerId, BigDecimal initialBalance) {
        BigDecimal normalized = normalize(Objects.requireNonNull(initialBalance, "initialBalance no puede ser null"));
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException("El saldo inicial no puede ser negativo");
        }
        return new Account(UUID.randomUUID(), ownerId, normalized, AccountStatus.ACTIVE);
    }

    /**
     * Reconstruye una cuenta ya existente a partir de datos persistidos.
     * Uso exclusivo de los mappers de infraestructura (ADR-0001, Fase 3) —
     * no aplica las validaciones de "cuenta nueva".
     */
    public static Account reconstitute(UUID id, String ownerId, BigDecimal balance, AccountStatus status) {
        return new Account(id, ownerId, balance, status);
    }

    /**
     * Debita el monto indicado.
     * BR-003: solo una cuenta ACTIVE puede debitar.
     * BR-001: el balance resultante nunca puede ser negativo.
     */
    public void debit(BigDecimal amount) {
        requireActive();
        BigDecimal normalizedAmount = requirePositiveAmount(amount);
        BigDecimal resultingBalance = this.balance.subtract(normalizedAmount);
        if (resultingBalance.signum() < 0) {
            throw new InsufficientFundsException(id, normalizedAmount, this.balance);
        }
        this.balance = resultingBalance;
    }

    /**
     * Acredita el monto indicado.
     * BR-003: solo una cuenta ACTIVE puede recibir crédito.
     */
    public void credit(BigDecimal amount) {
        requireActive();
        BigDecimal normalizedAmount = requirePositiveAmount(amount);
        this.balance = this.balance.add(normalizedAmount);
    }

    private void requireActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(id, status);
        }
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        BigDecimal normalized = normalize(Objects.requireNonNull(amount, "amount no puede ser null"));
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser estrictamente positivo");
        }
        return normalized;
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

    public String getOwnerId() {
        return ownerId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
