package com.paymentgateway.engine.domain.port;

import com.paymentgateway.engine.domain.model.Transaction;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepositoryPort {

    void save(Transaction transaction);

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * BR-015: transacciones donde la cuenta es origen O destino, filtradas,
     * ordenadas por createdAt DESC, id DESC (Decisión 6) y paginadas.
     */
    PageResult<Transaction> findHistory(TransactionHistoryQuery query);
}