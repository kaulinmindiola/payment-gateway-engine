package com.paymentgatewayengine.domain.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * BR-001: el balance de una cuenta nunca puede ser negativo.
 * Lanzada por Account.debit() cuando el monto solicitado excede el saldo disponible.
 */
public class InsufficientFundsException extends DomainException {

    private final UUID accountId;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;

    public InsufficientFundsException(UUID accountId, BigDecimal requestedAmount, BigDecimal availableBalance) {
        super("Saldo insuficiente en la cuenta " + accountId
                + ": solicitado=" + requestedAmount + ", disponible=" + availableBalance);
        this.accountId = accountId;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}
