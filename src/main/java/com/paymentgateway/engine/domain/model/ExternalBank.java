package com.paymentgateway.engine.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class ExternalBank {

    private final UUID id;
    private final UUID providerId;
    private final String code;
    private final String name;
    private final String country;
    private final String currency;
    private final ExternalBankStatus status;

    private ExternalBank(UUID id, UUID providerId, String code, String name,
                          String country, String currency, ExternalBankStatus status) {
        this.id = id;
        this.providerId = providerId;
        this.code = code;
        this.name = name;
        this.country = country;
        this.currency = currency;
        this.status = status;
    }

    public static ExternalBank reconstitute(UUID id, UUID providerId, String code, String name,
                                             String country, String currency, ExternalBankStatus status) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(country, "country must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return new ExternalBank(id, providerId, code, name, country, currency, status);
    }

    public boolean isActive() {
        return this.status == ExternalBankStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getCurrency() { return currency; }
    public ExternalBankStatus getStatus() { return status; }
}