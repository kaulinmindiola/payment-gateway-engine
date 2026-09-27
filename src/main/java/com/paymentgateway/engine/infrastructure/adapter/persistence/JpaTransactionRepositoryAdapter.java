package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.port.PageResult;
import com.paymentgateway.engine.domain.port.TransactionHistoryQuery;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.TransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaTransactionRepositoryAdapter implements TransactionRepositoryPort {

    private static final Sort HISTORY_ORDER =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")); 

    private final TransactionJpaRepository transactionJpaRepository;

    public JpaTransactionRepositoryAdapter(TransactionJpaRepository transactionJpaRepository) {
        this.transactionJpaRepository = transactionJpaRepository;
    }

    @Override
    public void save(Transaction transaction) {
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

    @Override
    public PageResult<Transaction> findHistory(TransactionHistoryQuery query) {
        Page<TransactionEntity> page = transactionJpaRepository.findAll(
                TransactionHistorySpecifications.from(query),
                PageRequest.of(query.page(), query.size(), HISTORY_ORDER));
        return new PageResult<>(
                page.getContent().stream().map(this::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    // Mapeo manual dominio↔JPA (ADR-0001).

    private Transaction toDomain(TransactionEntity entity) {
        return Transaction.reconstitute(
                entity.getId(), entity.getSourceAccountId(), entity.getAmount(), entity.getIdempotencyKey(),
                entity.getStatus(), entity.getFailureReason(), entity.getTransferType(),
                entity.getTargetAccountId(), entity.getTargetProviderId(), entity.getTargetBankId(),
                entity.getTargetExternalReference(), entity.getCreatedAt()
        );
    }

    private TransactionEntity toEntity(Transaction transaction) {
        return new TransactionEntity(
                transaction.getId(), transaction.getSourceAccountId(), transaction.getAmount(),
                transaction.getIdempotencyKey(), transaction.getStatus(), transaction.getFailureReason(),
                transaction.getTransferType(), transaction.getTargetAccountId(),
                transaction.getTargetProviderId(), transaction.getTargetBankId(),
                transaction.getTargetExternalReference(), transaction.getCreatedAt()
        );
    }
}