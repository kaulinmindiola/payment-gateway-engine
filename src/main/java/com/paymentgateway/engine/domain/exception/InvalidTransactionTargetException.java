package com.paymentgateway.engine.domain.exception;

public class InvalidTransactionTargetException extends DomainException {
    public InvalidTransactionTargetException(String message) {
        super(message);
    }
}