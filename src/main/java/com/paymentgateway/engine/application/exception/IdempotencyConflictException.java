package com.paymentgateway.engine.application.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("A transfer with idempotency key '" + idempotencyKey + "' is already in progress");
    }
}