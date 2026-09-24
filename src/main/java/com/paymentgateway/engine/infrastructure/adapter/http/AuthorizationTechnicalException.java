package com.paymentgateway.engine.infrastructure.adapter.http;

/**
 * Padre común de fallos técnicos del proveedor de autorización (timeout,
 * 5xx) -- permite que retryExceptions (Sección 11 del contexto) liste una
 * única clase en vez de enumerar cada subtipo. APPROVED/DECLINED nunca
 * pasan por esta jerarquía (son valores de retorno, no excepciones).
 */
public abstract class AuthorizationTechnicalException extends RuntimeException {
    protected AuthorizationTechnicalException(String message, Throwable cause) {
        super(message, cause);
    }
}