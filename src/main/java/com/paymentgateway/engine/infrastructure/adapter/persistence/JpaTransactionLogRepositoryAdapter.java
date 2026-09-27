package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.domain.model.TransactionLog;
import com.paymentgateway.engine.domain.port.TransactionLogRepositoryPort;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.TransactionLogEntity;
import org.springframework.stereotype.Component;

@Component
public class JpaTransactionLogRepositoryAdapter implements TransactionLogRepositoryPort {

    private final TransactionLogJpaRepository transactionLogJpaRepository;

    public JpaTransactionLogRepositoryAdapter(TransactionLogJpaRepository transactionLogJpaRepository) {
        this.transactionLogJpaRepository = transactionLogJpaRepository;
    }

    @Override
    public void save(TransactionLog log) {
        transactionLogJpaRepository.save(toEntity(log));
    }

    private TransactionLogEntity toEntity(TransactionLog log) {
        return new TransactionLogEntity(log.getId(), log.getTransactionId(), log.getStatus(), log.getDetail());
    }
}