package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.ProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProviderJpaRepository extends JpaRepository<ProviderEntity, UUID> {
}