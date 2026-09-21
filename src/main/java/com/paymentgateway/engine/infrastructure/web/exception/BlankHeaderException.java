package com.paymentgateway.engine.infrastructure.web.exception;

public class BlankHeaderException extends RuntimeException {
    public BlankHeaderException(String headerName) {
        super("Header '" + headerName + "' must not be blank");
    }
}