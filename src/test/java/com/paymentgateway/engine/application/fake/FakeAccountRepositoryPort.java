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
        Account stored = storage.get(id);
        if (stored == null) {
            throw new AccountNotFoundException(id.toString());
        }
        return copyOf(stored);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return Optional.ofNullable(storage.get(id))
                .map(this::copyOf);
    }

    @Override
    public void save(Account account) {
        storage.put(account.getId(), account);
    }

    public int size() {
        return storage.size();
    }
    private Account copyOf(Account account) {
        return Account.reconstitute(
                account.getId(),
                account.getOwnerId(),
                account.getBalance(),
                account.getStatus()
        );
    }
}