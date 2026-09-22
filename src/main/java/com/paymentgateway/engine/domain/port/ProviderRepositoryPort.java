package com.paymentgateway.engine.domain.port;

import com.paymentgateway.engine.domain.model.Provider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderRepositoryPort {
    Optional<Provider> findById(UUID id);
    List<Provider> findAll();
}