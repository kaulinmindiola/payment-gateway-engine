package com.paymentgatewayengine.domain.exception;

/**
 * Excepción base para violaciones de reglas de negocio del dominio.
 * Unchecked deliberadamente: forzar try/catch en cada invocación de un caso
 * de uso añadiría ruido sin beneficio real — el GlobalExceptionHandler
 * (Fase 4) captura este tipo base y sus subtipos de forma centralizada para
 * traducirlos a respuestas RFC 7807 (DEC-011).
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
