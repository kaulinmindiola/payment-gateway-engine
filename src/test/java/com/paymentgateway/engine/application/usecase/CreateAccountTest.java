package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.application.fake.FakeAccountRepositoryPort;
import com.paymentgateway.engine.application.fake.FakeUserRepositoryPort;
import com.paymentgateway.engine.domain.exception.InvalidAmountException;
import com.paymentgateway.engine.domain.exception.UserNotFoundException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateAccountTest {

    private FakeAccountRepositoryPort accountRepository;
    private FakeUserRepositoryPort userRepository;
    private CreateAccount createAccount;

    @BeforeEach
    void setUp() {
        accountRepository = new FakeAccountRepositoryPort();
        userRepository = new FakeUserRepositoryPort();
        createAccount = new CreateAccount(accountRepository, userRepository);
    }

    @Test
    void execute_withExistingUserAndValidBalance_createsActiveAccount() {
        UUID ownerId = UUID.randomUUID();
        userRepository.seedExistingUser(ownerId);

        Account account = createAccount.execute(CreateAccountCommand.of(ownerId, new BigDecimal("100.00")));

        assertThat(account.getOwnerId()).isEqualTo(ownerId);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
        assertThat(accountRepository.findById(account.getId())).isPresent();
    }

    @Test
    void execute_withZeroInitialBalance_isAllowed() {
        UUID ownerId = UUID.randomUUID();
        userRepository.seedExistingUser(ownerId);

        Account account = createAccount.execute(CreateAccountCommand.of(ownerId, BigDecimal.ZERO));

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void execute_withNonExistingUser_throwsUserNotFoundException() {
        UUID ownerId = UUID.randomUUID(); // nunca sembrado

        assertThatThrownBy(() ->
                createAccount.execute(CreateAccountCommand.of(ownerId, new BigDecimal("10.00"))))
                .isInstanceOf(UserNotFoundException.class);

        // BR-012: si el usuario no existe, no debe persistirse ninguna cuenta.
        assertThat(accountRepository.size()).isZero();
    }

    @Test
    void execute_withNegativeInitialBalance_throwsInvalidAmountException() {
        UUID ownerId = UUID.randomUUID();
        userRepository.seedExistingUser(ownerId);

        assertThatThrownBy(() ->
                createAccount.execute(CreateAccountCommand.of(ownerId, new BigDecimal("-1.00"))))
                .isInstanceOf(InvalidAmountException.class);

        assertThat(accountRepository.size()).isZero();
    }
}