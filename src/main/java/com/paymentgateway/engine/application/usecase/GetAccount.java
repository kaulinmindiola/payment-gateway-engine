package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.port.AccountRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class GetAccount {

    private final AccountRepositoryPort accountRepositoryPort;

    public GetAccount(AccountRepositoryPort accountRepositoryPort) {
        this.accountRepositoryPort = accountRepositoryPort;
    }

    public Account execute(GetAccountQuery query) {
        // Lectura sin lock -- es un GET, no una mutación (ver Decisión 1, Fase 2 Paso 5).
        Account account = accountRepositoryPort.findById(query.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(query.getAccountId().toString()));

        // BR-008: solo el owner puede consultar. Se evalúa DESPUÉS de confirmar
        // existencia -- orden intencional, ver nota de diseño arriba (RISK-006).
        if (!account.getOwnerId().equals(query.getRequestingUserId())) {
            throw new OwnershipViolationException(account.getId().toString());
        }

        return account;
    }
}