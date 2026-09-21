package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.model.Transaction;

/**
 * BR-002: una ejecución REAL (Executed) es distinguible de una respuesta
 * REPLICADA sin re-ejecutar (Replayed). El controller decide cómo serializar
 * cada una -- Executed pasa por TransactionResponse normal; Replayed emite
 * el body ya serializado tal cual, sin pasar de nuevo por GlobalExceptionHandler.
 */
public sealed interface TransferOutcome {
    record Executed(Transaction transaction) implements TransferOutcome {}
    record Replayed(int httpStatus, String responseBody) implements TransferOutcome {}
}