package com.paymentgateway.engine.application.fake;

import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.port.PageResult;
import com.paymentgateway.engine.domain.port.TransactionHistoryQuery;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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

    @Override
    public PageResult<Transaction> findHistory(TransactionHistoryQuery q) {
        List<Transaction> matching = storage.values().stream()
                .filter(t -> q.accountId().equals(t.getSourceAccountId()) || q.accountId().equals(t.getTargetAccountId()))
                .filter(t -> q.status() == null || t.getStatus() == q.status())
                .filter(t -> q.transferType() == null || t.getTransferType() == q.transferType())
                .filter(t -> q.dateFrom() == null || !t.getCreatedAt().isBefore(q.dateFrom()))
                .filter(t -> q.dateTo() == null || t.getCreatedAt().isBefore(q.dateTo()))
                // Nota: UUID.compareTo (Java, con signo) NO ordena igual que Postgres.
                // El desempate exacto solo se verifica en el IT contra Postgres real;
                // los tests con este fake no deben depender del orden en empates.
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed()
                        .thenComparing(Transaction::getId, Comparator.reverseOrder()))
                .toList();

        int from = Math.min(q.page() * q.size(), matching.size());
        int to = Math.min(from + q.size(), matching.size());
        int totalPages = (int) Math.ceil((double) matching.size() / q.size());

        return new PageResult<>(matching.subList(from, to), q.page(), q.size(), matching.size(), totalPages);
    }

    public int size() { 
        return storage.size(); 
    }
}