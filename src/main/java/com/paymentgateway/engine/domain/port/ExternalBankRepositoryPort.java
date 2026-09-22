package com.paymentgateway.engine.domain.port;

import com.paymentgateway.engine.domain.model.ExternalBank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalBankRepositoryPort {
    Optional<ExternalBank> findById(UUID id);
    List<ExternalBank> findAll();
}