package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
}