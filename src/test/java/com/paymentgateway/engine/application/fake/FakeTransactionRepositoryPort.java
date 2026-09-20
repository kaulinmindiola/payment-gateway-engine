package com.paymentgateway.engine.application.fake;

import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeTransactionRepositoryPort implements TransactionRepositoryPort {

    private final Map<UUID, Transaction> storage = new HashMap<>();

    @Override
    public void save(Transaction transaction) {
        storage.put(transaction.getId(), transaction);
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return storage.values().stream()
                .filter(t -> t.getIdempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    public int size() { return storage.size(); }
}