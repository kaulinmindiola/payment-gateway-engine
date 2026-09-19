package com.paymentgateway.engine.infrastructure.adapter.persistence.entity;

import com.paymentgateway.engine.domain.model.AccountStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    // Reservado (Account.version, Sección 4/10 del contexto): columna simple,
    // NUNCA @Version — eso activaría optimistic locking automático de Hibernate,
    // contradiciendo ADR-0004 (pessimistic locking es la única estrategia activa).
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountEntity() { }

    public AccountEntity(UUID id, UUID ownerId, BigDecimal balance, AccountStatus status, Long version) {
        this.id = id;
        this.ownerId = ownerId;
        this.balance = balance;
        this.status = status;
        this.version = version;
    }

    /**
     * Aplica cambios mutables sobre una instancia YA gestionada por Hibernate
     * (cargada en la misma transacción, p.ej. vía findByIdForUpdate).
     * No toca id/ownerId/createdAt — inmutables tras la creación.
     */
    public void applyChangesFrom(BigDecimal newBalance, AccountStatus newStatus) {
        this.balance = newBalance;
        this.status = newStatus;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}