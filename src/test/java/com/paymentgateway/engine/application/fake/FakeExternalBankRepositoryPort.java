package com.paymentgateway.engine.application.fake;

import com.paymentgateway.engine.domain.model.ExternalBank;
import com.paymentgateway.engine.domain.port.ExternalBankRepositoryPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeExternalBankRepositoryPort implements ExternalBankRepositoryPort {

    private final Map<UUID, ExternalBank> storage = new HashMap<>();

    public void seed(ExternalBank bank) {
        storage.put(bank.getId(), bank);
    }

    @Override
    public Optional<ExternalBank> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<ExternalBank> findAll() {
        return List.copyOf(storage.values());
    }
}