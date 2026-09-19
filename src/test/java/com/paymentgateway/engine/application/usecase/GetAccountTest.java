package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.application.fake.FakeAccountRepositoryPort;
import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetAccountTest {

    private FakeAccountRepositoryPort accountRepository;
    private GetAccount getAccount;

    @BeforeEach
    void setUp() {
        accountRepository = new FakeAccountRepositoryPort();
        getAccount = new GetAccount(accountRepository);
    }

    @Test
    void execute_asOwner_returnsAccount() {
        UUID ownerId = UUID.randomUUID();
        Account account = Account.createNew(ownerId, new BigDecimal("50.00"));
        accountRepository.save(account);

        Account result = getAccount.execute(GetAccountQuery.of(account.getId(), ownerId));

        assertThat(result.getId()).isEqualTo(account.getId());
        assertThat(result.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void execute_asNonOwner_throwsOwnershipViolationException() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Account account = Account.createNew(ownerId, new BigDecimal("50.00"));
        accountRepository.save(account);

        assertThatThrownBy(() ->
                getAccount.execute(GetAccountQuery.of(account.getId(), otherUserId)))
                .isInstanceOf(OwnershipViolationException.class);
    }

    @Test
    void execute_forNonExistentAccount_throwsAccountNotFoundException() {
        UUID requestingUserId = UUID.randomUUID();
        UUID nonExistentAccountId = UUID.randomUUID();

        assertThatThrownBy(() ->
                getAccount.execute(GetAccountQuery.of(nonExistentAccountId, requestingUserId)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void execute_forNonExistentAccount_prioritizesNotFoundOverOwnership() {
        // Confirma el orden de validación: 404 antes que 403, incluso cuando
        // NINGÚN usuario podría ser el owner de una cuenta que no existe.
        UUID anyUserId = UUID.randomUUID();
        UUID nonExistentAccountId = UUID.randomUUID();

        assertThatThrownBy(() ->
                getAccount.execute(GetAccountQuery.of(nonExistentAccountId, anyUserId)))
                .isInstanceOf(AccountNotFoundException.class)
                .isNotInstanceOf(OwnershipViolationException.class);
    }
}