package com.paymentgateway.engine.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final String email;
    private final String name;
    private final UserStatus status;

    private User(UUID id, String email, String name, UserStatus status) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.status = status;
    }

    public static User reconstitute(UUID id, String email, String name, UserStatus status) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return new User(id, email, name, status);
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public UserStatus getStatus() { return status; }
}