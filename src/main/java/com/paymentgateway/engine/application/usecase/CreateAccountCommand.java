package com.paymentgateway.engine.application.usecase;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class CreateAccountCommand {

    private final UUID ownerId;
    private final BigDecimal initialBalance;

    private CreateAccountCommand(UUID ownerId, BigDecimal initialBalance) {
        this.ownerId = ownerId;
        this.initialBalance = initialBalance;
    }

    public static CreateAccountCommand of(UUID ownerId, BigDecimal initialBalance) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(initialBalance, "initialBalance must not be null");
        return new CreateAccountCommand(ownerId, initialBalance);
    }

    public UUID getOwnerId() { return ownerId; }
    public BigDecimal getInitialBalance() { return initialBalance; }
}