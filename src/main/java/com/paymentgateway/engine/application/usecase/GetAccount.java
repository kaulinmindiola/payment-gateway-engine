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
        // Lectura sin lock -- es un GET, no una mutación.
        Account account = accountRepositoryPort.findById(query.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(query.getAccountId().toString()));

        // solo el owner puede consultar. Se evalúa DESPUÉS de confirmar
        // existencia -- orden intencional.
        if (!account.getOwnerId().equals(query.getRequestingUserId())) {
            throw new OwnershipViolationException(account.getId().toString());
        }

        return account;
    }
}