package com.paymentgateway.engine.domain.exception;

public class ExternalBankNotFoundException extends DomainException {
    public ExternalBankNotFoundException(String externalBankId) {
        super("External bank not found: " + externalBankId);
    }
}