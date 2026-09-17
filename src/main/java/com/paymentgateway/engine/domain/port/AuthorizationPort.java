package com.paymentgateway.engine.domain.port;

/**
 * Invocado únicamente por transferencias EXTERNAL (ver Sección 7 del contexto).
 * Fallos técnicos (timeout / servicio no disponible) se comunican como
 * excepciones no declaradas aquí — domain/ nunca conoce esos tipos
 * (viven en infrastructure/adapter/http/, ver ADR-0002).
 */
public interface AuthorizationPort {

    AuthorizationResult authorize(AuthorizationRequest request);
}