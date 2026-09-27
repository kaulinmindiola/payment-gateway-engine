package com.paymentgateway.engine.domain.port;

import com.paymentgateway.engine.domain.model.Transaction;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepositoryPort {

    void save(Transaction transaction);

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Transacciones donde la cuenta es origen O destino, filtradas,
     * ordenadas por createdAt DESC, id DESC y paginadas.
     */
    PageResult<Transaction> findHistory(TransactionHistoryQuery query);
}