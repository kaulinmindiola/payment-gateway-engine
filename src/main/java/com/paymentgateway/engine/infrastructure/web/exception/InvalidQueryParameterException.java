package com.paymentgateway.engine.infrastructure.web.exception;

/** Parámetro de consulta con formato válido pero valor fuera de rango o incoherente. */
public class InvalidQueryParameterException extends RuntimeException {
    public InvalidQueryParameterException(String detail, Throwable cause) {
        super(detail, cause);
    }
}