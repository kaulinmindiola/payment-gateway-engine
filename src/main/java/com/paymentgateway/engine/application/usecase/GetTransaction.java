package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.exception.TransactionNotFoundException;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.port.AccountRepositoryPort;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class GetTransaction {

    private final TransactionRepositoryPort transactionRepositoryPort;
    private final AccountRepositoryPort accountRepositoryPort;

    public GetTransaction(TransactionRepositoryPort transactionRepositoryPort,
                           AccountRepositoryPort accountRepositoryPort) {
        this.transactionRepositoryPort = transactionRepositoryPort;
        this.accountRepositoryPort = accountRepositoryPort;
    }

    public Transaction execute(GetTransactionQuery query) {
        Transaction transaction = transactionRepositoryPort.findById(query.getTransactionId())
                .orElseThrow(() -> new TransactionNotFoundException(query.getTransactionId().toString()));

        // BR-009: accesible por el owner de ORIGEN o DESTINO. Ambas lecturas
        // sin lock -- es un GET (mismo criterio que GetAccount, Fase 4).
        boolean isSourceOwner = accountRepositoryPort.findById(transaction.getSourceAccountId())
                .map(account -> account.getOwnerId().equals(query.getRequestingUserId()))
                .orElse(false);

        boolean isTargetOwner = transaction.getTargetAccountId() != null
                && accountRepositoryPort.findById(transaction.getTargetAccountId())
                        .map(account -> account.getOwnerId().equals(query.getRequestingUserId()))
                        .orElse(false);

        if (!isSourceOwner && !isTargetOwner) {
            throw new OwnershipViolationException(transaction.getId().toString());
        }

        return transaction;
    }
}