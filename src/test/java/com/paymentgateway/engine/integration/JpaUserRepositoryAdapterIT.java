package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.domain.model.UserStatus;
import com.paymentgateway.engine.infrastructure.adapter.persistence.JpaUserRepositoryAdapter;
import com.paymentgateway.engine.infrastructure.adapter.persistence.UserJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaUserRepositoryAdapterIT extends AbstractPersistenceIntegrationTest {

    @Autowired
    private UserJpaRepository userJpaRepository;

    private JpaUserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaUserRepositoryAdapter(userJpaRepository);
    }

    @Test
    void existsById_forSeededUser_returnsTrue() {
        UUID id = UUID.randomUUID();
        userJpaRepository.save(new UserEntity(id, "alice@example.com", "Alice", UserStatus.ACTIVE));

        assertThat(adapter.existsById(id)).isTrue();
    }

    @Test
    void existsById_forUnknownId_returnsFalse() {
        assertThat(adapter.existsById(UUID.randomUUID())).isFalse();
    }
}