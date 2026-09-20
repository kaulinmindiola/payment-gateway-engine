package com.paymentgateway.engine.application.handler;

import com.paymentgateway.engine.domain.model.Transaction;

/**
 * InternalTransferHandler (Fase 5, sin AuthorizationPort) y
 * ExternalTransferHandler (Fase 8, con AuthorizationPort + Resilience4j)
 * implementan este contrato (ADR-0009). "TransactionResult" (pseudocódigo,
 * Sección 3 del contexto) se materializa como domain.model.Transaction --
 * ya expone status/failureReason, no se necesita un tipo envoltorio nuevo.
 */
public interface TransferHandler {
    Transaction handle(TransferCommand command);
}