package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.exception.UserNotFoundException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.port.AccountRepositoryPort;
import com.paymentgateway.engine.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateAccount {

    private final AccountRepositoryPort accountRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;

    public CreateAccount(AccountRepositoryPort accountRepositoryPort, UserRepositoryPort userRepositoryPort) {
        this.accountRepositoryPort = accountRepositoryPort;
        this.userRepositoryPort = userRepositoryPort;
    }

    @Transactional
    public Account execute(CreateAccountCommand command) {
        // BR-012: validado explícitamente aquí, no vía excepción de integridad de la DB.
        if (!userRepositoryPort.existsById(command.getOwnerId())) {
            throw new UserNotFoundException(command.getOwnerId().toString());
        }

        // Account.createNew() ya fuerza status=ACTIVE y balance inicial >= 0 (Fase 2, BR-001).
        Account account = Account.createNew(command.getOwnerId(), command.getInitialBalance());
        accountRepositoryPort.save(account);
        return account;
    }
}