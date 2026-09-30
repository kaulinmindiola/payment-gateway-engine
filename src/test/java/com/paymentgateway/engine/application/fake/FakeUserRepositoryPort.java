package com.paymentgateway.engine.application.fake;

import com.paymentgateway.engine.domain.port.UserRepositoryPort;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import com.paymentgateway.engine.domain.model.User;
import com.paymentgateway.engine.domain.model.UserStatus;

public class FakeUserRepositoryPort implements UserRepositoryPort {

    private final Map<UUID, User> users = new HashMap<>();

    /** Existing tests: only the id matters. */
    public void seedExistingUser(UUID userId) {
        seed(User.reconstitute(userId, userId + "@example.com", "Test User", UserStatus.ACTIVE));
    }

    public void seed(User user) {
        users.put(user.getId(), user);
    }

    @Override
    public boolean existsById(UUID id) {
        return users.containsKey(id);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(users.get(id));
    }
}