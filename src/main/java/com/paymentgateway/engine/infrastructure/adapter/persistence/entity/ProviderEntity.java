package com.paymentgateway.engine.infrastructure.adapter.persistence.entity;

import com.paymentgateway.engine.domain.model.ProviderStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "providers")
public class ProviderEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderStatus status;

    protected ProviderEntity() { }

    public ProviderEntity(UUID id, String code, String name, ProviderStatus status) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public ProviderStatus getStatus() { return status; }
}