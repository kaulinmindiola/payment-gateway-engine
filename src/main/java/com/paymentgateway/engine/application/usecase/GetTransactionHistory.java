package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.port.AccountRepositoryPort;
import com.paymentgateway.engine.domain.port.PageResult;
import com.paymentgateway.engine.domain.port.TransactionHistoryQuery;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class GetTransactionHistory {

    private final AccountRepositoryPort accountRepositoryPort;
    private final TransactionRepositoryPort transactionRepositoryPort;

    public GetTransactionHistory(AccountRepositoryPort accountRepositoryPort,
                                  TransactionRepositoryPort transactionRepositoryPort) {
        this.accountRepositoryPort = accountRepositoryPort;
        this.transactionRepositoryPort = transactionRepositoryPort;
    }

    public PageResult<Transaction> execute(TransactionHistoryQuery query, UUID requestingUserId) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(requestingUserId, "requestingUserId must not be null");

        Account account = accountRepositoryPort.findById(query.accountId())
                .orElseThrow(() -> new AccountNotFoundException(query.accountId().toString()));

        if (!account.getOwnerId().equals(requestingUserId)) {
            throw new OwnershipViolationException(account.getId().toString());
        }

        // BR-015: filtros, orden y paginación se resuelven en el adapter.
        return transactionRepositoryPort.findHistory(query);
    }
}