package com.paymentgatewayengine.domain.transaction;

/**
 * Estados del ciclo de vida de una Transaction (DV-012).
 * PENDING es el único estado no terminal; COMPLETED y FAILED son terminales
 * y no permiten transiciones posteriores.
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}
