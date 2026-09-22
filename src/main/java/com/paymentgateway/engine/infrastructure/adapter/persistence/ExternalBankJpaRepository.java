package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.ExternalBankEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExternalBankJpaRepository extends JpaRepository<ExternalBankEntity, UUID> {
}