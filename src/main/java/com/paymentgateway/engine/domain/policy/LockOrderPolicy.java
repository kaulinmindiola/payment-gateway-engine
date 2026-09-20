package com.paymentgateway.engine.domain.policy;

import java.util.Objects;
import java.util.UUID;

/**
 * RISK-001: resuelve deadlocks por orden inconsistente de locks.
 * Invocado UNA sola vez por transferencia INTERNAL, antes de cualquier
 * findByIdForUpdate() (Sección 10 del contexto). No aplica a EXTERNAL
 * (solo se bloquea la cuenta origen, no hay una segunda cuenta propia
 * del sistema -- ver Sección 10).
 */
public final class LockOrderPolicy {

    public LockOrder resolveLockOrder(UUID sourceId, UUID targetId) {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");

        if (sourceId.compareTo(targetId) < 0) {
            return new LockOrder(sourceId, targetId);
        }
        return new LockOrder(targetId, sourceId);
    }
}