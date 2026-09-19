package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.TransactionEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaTransactionRepositoryAdapter implements TransactionRepositoryPort {

    private final TransactionJpaRepository transactionJpaRepository;

    public JpaTransactionRepositoryAdapter(TransactionJpaRepository transactionJpaRepository) {
        this.transactionJpaRepository = transactionJpaRepository;
    }

    @Override
    public void save(Transaction transaction) {
        // Mismo patrón create/update que JpaAccountRepositoryAdapter (Paso 4):
        // evita que un merge() ingenuo pise created_at en una actualización.
        transactionJpaRepository.findById(transaction.getId()).ifPresentOrElse(
                existing -> existing.applyChangesFrom(transaction.getStatus(), transaction.getFailureReason()),
                () -> transactionJpaRepository.save(toEntity(transaction))
        );
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return transactionJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return transactionJpaRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    // Mapeo manual dominio↔JPA (ADR-0001).

    private Transaction toDomain(TransactionEntity entity) {
        return Transaction.reconstitute(
                entity.getId(), entity.getSourceAccountId(), entity.getAmount(), entity.getIdempotencyKey(),
                entity.getStatus(), entity.getFailureReason(), entity.getTransferType(),
                entity.getTargetAccountId(), entity.getTargetProviderId(), entity.getTargetBankId(),
                entity.getTargetExternalReference()
        );
    }

    private TransactionEntity toEntity(Transaction transaction) {
        return new TransactionEntity(
                transaction.getId(), transaction.getSourceAccountId(), transaction.getAmount(),
                transaction.getIdempotencyKey(), transaction.getStatus(), transaction.getFailureReason(),
                transaction.getTransferType(), transaction.getTargetAccountId(),
                transaction.getTargetProviderId(), transaction.getTargetBankId(),
                transaction.getTargetExternalReference()
        );
    }
}