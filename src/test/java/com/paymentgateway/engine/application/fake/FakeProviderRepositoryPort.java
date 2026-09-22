package com.paymentgateway.engine.application.fake;

import com.paymentgateway.engine.domain.model.Provider;
import com.paymentgateway.engine.domain.port.ProviderRepositoryPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeProviderRepositoryPort implements ProviderRepositoryPort {

    private final Map<UUID, Provider> storage = new HashMap<>();

    public void seed(Provider provider) {
        storage.put(provider.getId(), provider);
    }

    @Override
    public Optional<Provider> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Provider> findAll() {
        return List.copyOf(storage.values());
    }
}