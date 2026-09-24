package com.paymentgateway.engine.domain.port;

public interface IdempotencyPort {
    IdempotencyClaim tryBegin(String idempotencyKey);
    void complete(String idempotencyKey, IdempotencyResult result);

    /**
     * Libera una key reclamada tras un fallo TÉCNICO (no de negocio) --
     * permite que el cliente reintente sin esperar el TTL de 24h. Fase 8,
     * Decisión 2: distinto del caso ya aceptado en Fase 6 (rutas de
     * scaffolding/programador, marginal); aquí es el escenario central
     * que esta fase existe para ejercitar (provider "flaky").
     */
    void release(String idempotencyKey);
}