package com.paymentgatewayengine.domain.transaction;

/**
 * Estados posibles de una entrada de transaction_logs (Sección 11, Context Maestro).
 * REVERSED se reserva para extensibilidad futura (ASM-002): ningún caso de uso
 * de este alcance lo dispara todavía.
 */
public enum LogStatus {
    PENDING,
    APPROVED,
    DECLINED,
    REVERSED
}
