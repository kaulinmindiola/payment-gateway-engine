package com.paymentgateway.engine.domain.exception;

public class SelfTransferException extends DomainException {
    public SelfTransferException(String accountId) {
        super("Source and target account must not be the same: " + accountId);
    }
}