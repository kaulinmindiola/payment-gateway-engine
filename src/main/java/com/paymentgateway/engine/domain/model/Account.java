package com.paymentgateway.engine.domain.model;

import com.paymentgateway.engine.domain.exception.InsufficientBalanceException;
import com.paymentgateway.engine.domain.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public final class Account {

    private final UUID id;
    private final UUID ownerId;
    private BigDecimal balance;
    private AccountStatus status;

    private Account(UUID id, UUID ownerId, BigDecimal balance, AccountStatus status) {
        this.id = id;
        this.ownerId = ownerId;
        this.balance = balance;
        this.status = status;
    }

    public static Account createNew(UUID ownerId, BigDecimal initialBalance) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        BigDecimal normalized = normalize(initialBalance);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException("Initial balance must be >= 0");
        }
        return new Account(UUID.randomUUID(), ownerId, normalized, AccountStatus.ACTIVE);
    }

    public static Account reconstitute(UUID id, UUID ownerId, BigDecimal balance, AccountStatus status) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return new Account(id, ownerId, normalize(balance), status);
    }

    public void debit(BigDecimal amount) {
        BigDecimal normalized = requirePositive(amount);
        BigDecimal newBalance = this.balance.subtract(normalized);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException(this.id.toString());
        }
        this.balance = newBalance;
    }

    public void credit(BigDecimal amount) {
        BigDecimal normalized = requirePositive(amount);
        this.balance = this.balance.add(normalized);
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
    return this.balance.compareTo(normalize(amount)) >= 0;
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        BigDecimal normalized = normalize(amount);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be strictly positive");
        }
        return normalized;
    }

    private static BigDecimal normalize(BigDecimal value) {
        Objects.requireNonNull(value, "amount must not be null");
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
}