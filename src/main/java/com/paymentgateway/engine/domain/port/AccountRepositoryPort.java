package com.paymentgateway.engine.domain.port;

import com.paymentgateway.engine.domain.model.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepositoryPort {

    /**
     * Lectura con PESSIMISTIC_WRITE. Usar únicamente en rutas de mutación
     * (transferencias). Lanza AccountNotFoundException si no existe.
     */
    Account findByIdForUpdate(UUID id);

    /**
     * Lectura simple sin lock, para rutas de consulta (GetAccount).
     */
    Optional<Account> findById(UUID id);

    void save(Account account);
}