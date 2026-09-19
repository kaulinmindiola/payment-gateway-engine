package com.paymentgateway.engine.infrastructure.adapter.persistence.entity;

import com.paymentgateway.engine.domain.model.ExternalBankStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "external_banks")
public class ExternalBankEntity {

    @Id
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExternalBankStatus status;

    protected ExternalBankEntity() { }

    public ExternalBankEntity(UUID id, UUID providerId, String code, String name,
                               String country, String currency, ExternalBankStatus status) {
        this.id = id;
        this.providerId = providerId;
        this.code = code;
        this.name = name;
        this.country = country;
        this.currency = currency;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getCurrency() { return currency; }
    public ExternalBankStatus getStatus() { return status; }
}