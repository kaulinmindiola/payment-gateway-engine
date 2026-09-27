package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.port.AccountRepositoryPort;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.AccountEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaAccountRepositoryAdapter implements AccountRepositoryPort {

    private final AccountJpaRepository accountJpaRepository;

    public JpaAccountRepositoryAdapter(AccountJpaRepository accountJpaRepository) {
        this.accountJpaRepository = accountJpaRepository;
    }

    @Override
    public Account findByIdForUpdate(UUID id) {
        AccountEntity entity = accountJpaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id.toString()));
        return toDomain(entity);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return accountJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public void save(Account account) {
        accountJpaRepository.findById(account.getId()).ifPresentOrElse(
                existing -> existing.applyChangesFrom(account.getBalance(), account.getStatus()),
                () -> accountJpaRepository.save(toEntity(account))
        );
    }

    // Mapeo manual dominio↔JPA (ADR-0001) — sin MapStruct, sin anotar el dominio.

    private Account toDomain(AccountEntity entity) {
        return Account.reconstitute(entity.getId(), entity.getOwnerId(), entity.getBalance(), entity.getStatus());
    }

    private AccountEntity toEntity(Account account) {
        return new AccountEntity(account.getId(), account.getOwnerId(),
                account.getBalance(), account.getStatus(), 0L);
    }
}