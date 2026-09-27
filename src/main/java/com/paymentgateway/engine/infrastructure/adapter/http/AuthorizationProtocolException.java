package com.paymentgateway.engine.infrastructure.adapter.http;

public class AuthorizationProtocolException extends AuthorizationTechnicalException {
    public AuthorizationProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}