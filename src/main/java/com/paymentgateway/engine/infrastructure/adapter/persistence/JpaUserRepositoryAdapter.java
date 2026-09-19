package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JpaUserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;

    public JpaUserRepositoryAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public boolean existsById(UUID id) {
        return userJpaRepository.existsById(id);
    }
}