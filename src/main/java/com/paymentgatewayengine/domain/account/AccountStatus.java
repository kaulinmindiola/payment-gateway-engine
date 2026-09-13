package com.paymentgatewayengine.domain.account;

/**
 * Estados posibles de una Account (DEC-006).
 * BLOCKED/CLOSED solo se fijan vía fixtures de test o acceso directo a BD
 * (CON-010) — no existe endpoint para cambiarlos.
 */
public enum AccountStatus {
    ACTIVE,
    BLOCKED,
    CLOSED
}
