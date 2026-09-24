package com.paymentgateway.engine.domain.model;

import com.paymentgateway.engine.domain.exception.InsufficientBalanceException;
import com.paymentgateway.engine.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private final UUID ownerId = UUID.randomUUID();

    @Test
    void createNew_withZeroInitialBalance_isAllowed() {
        Account account = Account.createNew(ownerId, BigDecimal.ZERO);
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void createNew_withNegativeInitialBalance_throws() {
        assertThatThrownBy(() -> Account.createNew(ownerId, new BigDecimal("-1")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void debit_exceedingBalance_throwsInsufficientBalance_andLeavesBalanceUnchanged() {
        Account account = Account.createNew(ownerId, new BigDecimal("100.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(InsufficientBalanceException.class);

        // BR-001: un intento fallido no debe dejar rastro en el balance
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void debit_exactBalance_resultsInZero() {
        Account account = Account.createNew(ownerId, new BigDecimal("50.00"));
        account.debit(new BigDecimal("50.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void credit_increasesBalance() {
        Account account = Account.createNew(ownerId, new BigDecimal("10.00"));
        account.credit(new BigDecimal("5.50"));
        assertThat(account.getBalance()).isEqualByComparingTo("15.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void debit_withNonPositiveAmount_throws(String value) {
        Account account = Account.createNew(ownerId, new BigDecimal("10.00"));
        assertThatThrownBy(() -> account.debit(new BigDecimal(value)))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void hasSufficientBalance_withExactAmount_returnsTrue() {
        Account account = Account.createNew(ownerId, new BigDecimal("50.00"));
        assertThat(account.hasSufficientBalance(new BigDecimal("50.00"))).isTrue();
    }

    @Test
    void hasSufficientBalance_withMoreThanBalance_returnsFalse() {
        Account account = Account.createNew(ownerId, new BigDecimal("50.00"));
        assertThat(account.hasSufficientBalance(new BigDecimal("50.01"))).isFalse();
    }

    @Test
    void hasSufficientBalance_doesNotMutateBalance() {
        Account account = Account.createNew(ownerId, new BigDecimal("50.00"));
        account.hasSufficientBalance(new BigDecimal("30.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("50.00");
    }
}