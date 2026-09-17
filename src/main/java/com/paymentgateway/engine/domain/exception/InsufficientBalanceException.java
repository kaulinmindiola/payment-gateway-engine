package com.paymentgateway.engine.domain.exception;

public class InsufficientBalanceException extends DomainException {
    public InsufficientBalanceException(String accountId) {
        super("Account " + accountId + " has insufficient balance for this operation");
    }
}