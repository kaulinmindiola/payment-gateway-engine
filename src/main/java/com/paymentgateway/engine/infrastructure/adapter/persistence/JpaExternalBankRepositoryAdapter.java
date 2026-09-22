package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.domain.model.ExternalBank;
import com.paymentgateway.engine.domain.port.ExternalBankRepositoryPort;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.ExternalBankEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaExternalBankRepositoryAdapter implements ExternalBankRepositoryPort {

    private final ExternalBankJpaRepository externalBankJpaRepository;

    public JpaExternalBankRepositoryAdapter(ExternalBankJpaRepository externalBankJpaRepository) {
        this.externalBankJpaRepository = externalBankJpaRepository;
    }

    @Override
    public Optional<ExternalBank> findById(UUID id) {
        return externalBankJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ExternalBank> findAll() {
        return externalBankJpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    private ExternalBank toDomain(ExternalBankEntity entity) {
        return ExternalBank.reconstitute(entity.getId(), entity.getProviderId(), entity.getCode(),
                entity.getName(), entity.getCountry(), entity.getCurrency(), entity.getStatus());
    }
}