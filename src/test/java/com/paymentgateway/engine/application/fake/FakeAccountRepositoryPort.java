package com.paymentgateway.engine.application.fake;

import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.port.AccountRepositoryPort;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeAccountRepositoryPort implements AccountRepositoryPort {

    private final Map<UUID, Account> storage = new HashMap<>();

    @Override
    public Account findByIdForUpdate(UUID id) {
        Account account = storage.get(id);
        if (account == null) {
            throw new AccountNotFoundException(id.toString());
        }
        return account;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public void save(Account account) {
        storage.put(account.getId(), account);
    }

    public int size() {
        return storage.size();
    }
}