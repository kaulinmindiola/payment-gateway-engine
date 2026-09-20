package com.paymentgateway.engine.domain.exception;

public class InactiveAccountException extends DomainException {
    public InactiveAccountException(String accountId) {
        super("Account is not ACTIVE: " + accountId);
    }
}