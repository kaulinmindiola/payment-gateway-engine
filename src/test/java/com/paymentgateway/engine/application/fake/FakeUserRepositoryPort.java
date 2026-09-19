package com.paymentgateway.engine.application.fake;

import com.paymentgateway.engine.domain.port.UserRepositoryPort;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FakeUserRepositoryPort implements UserRepositoryPort {

    private final Set<UUID> existingUserIds = new HashSet<>();

    public void seedExistingUser(UUID userId) {
        existingUserIds.add(userId);
    }

    @Override
    public boolean existsById(UUID id) {
        return existingUserIds.contains(id);
    }
}