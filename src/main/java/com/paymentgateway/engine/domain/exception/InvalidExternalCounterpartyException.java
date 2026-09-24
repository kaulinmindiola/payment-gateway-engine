package com.paymentgateway.engine.domain.exception;

public class InvalidExternalCounterpartyException extends DomainException {
    public InvalidExternalCounterpartyException(String message) {
        super(message);
    }
}