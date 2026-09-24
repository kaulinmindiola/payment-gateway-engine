package com.paymentgateway.engine.infrastructure.adapter.http;

public class AuthorizationTimeoutException extends AuthorizationTechnicalException {
    public AuthorizationTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}