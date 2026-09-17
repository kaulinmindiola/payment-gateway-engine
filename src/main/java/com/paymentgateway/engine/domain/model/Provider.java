package com.paymentgateway.engine.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class Provider {

    private final UUID id;
    private final String code;
    private final String name;
    private final ProviderStatus status;

    private Provider(UUID id, String code, String name, ProviderStatus status) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.status = status;
    }

    public static Provider reconstitute(UUID id, String code, String name, ProviderStatus status) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return new Provider(id, code, name, status);
    }

    public boolean isActive() {
        return this.status == ProviderStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public ProviderStatus getStatus() { return status; }
}