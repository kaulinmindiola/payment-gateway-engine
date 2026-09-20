package com.paymentgateway.engine.domain.exception;

public class TransactionNotFoundException extends DomainException {
    public TransactionNotFoundException(String transactionId) {
        super("Transaction not found: " + transactionId);
    }
}