package com.paymentgateway.engine.infrastructure.adapter.http;

public class AuthorizationUnavailableException extends AuthorizationTechnicalException {
    public AuthorizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}