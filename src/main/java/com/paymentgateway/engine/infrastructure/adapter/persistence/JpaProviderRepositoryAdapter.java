package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.domain.model.Provider;
import com.paymentgateway.engine.domain.port.ProviderRepositoryPort;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.ProviderEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaProviderRepositoryAdapter implements ProviderRepositoryPort {

    private final ProviderJpaRepository providerJpaRepository;

    public JpaProviderRepositoryAdapter(ProviderJpaRepository providerJpaRepository) {
        this.providerJpaRepository = providerJpaRepository;
    }

    @Override
    public Optional<Provider> findById(UUID id) {
        return providerJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Provider> findAll() {
        return providerJpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    private Provider toDomain(ProviderEntity entity) {
        return Provider.reconstitute(entity.getId(), entity.getCode(), entity.getName(), entity.getStatus());
    }
}