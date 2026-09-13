package com.paymentgatewayengine.domain.account;

import com.paymentgatewayengine.domain.exception.InactiveAccountException;
import com.paymentgatewayengine.domain.exception.InsufficientFundsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    @DisplayName("REQ-FUNC-001 / ASM-005: una cuenta nueva se crea ACTIVE con el saldo inicial dado")
    void open_createsActiveAccountWithInitialBalance() {
        Account account = Account.open("user-1", new BigDecimal("100.00"));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
        assertThat(account.getOwnerId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("BR-001 / REQ-FUNC-003: no se puede abrir una cuenta con saldo inicial negativo")
    void open_rejectsNegativeInitialBalance() {
        assertThatThrownBy(() -> Account.open("user-1", new BigDecimal("-10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CON-005: el balance se normaliza a escala 2 con redondeo HALF_EVEN")
    void open_normalizesScaleWithHalfEvenRounding() {
        // 100.005 -> HALF_EVEN a escala 2 redondea al par más cercano: 100.00
        Account account = Account.open("user-1", new BigDecimal("100.005"));

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Débito exitoso reduce el balance exactamente en el monto solicitado")
    void debit_reducesBalance_whenSufficientFunds() {
        Account account = Account.open("user-1", new BigDecimal("100.00"));

        account.debit(new BigDecimal("40.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("BR-001: un débito que dejaría saldo negativo lanza InsufficientFundsException sin modificar el balance")
    void debit_throwsInsufficientFunds_whenAmountExceedsBalance() {
        Account account = Account.open("user-1", new BigDecimal("50.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("50.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Crédito exitoso incrementa el balance exactamente en el monto solicitado")
    void credit_increasesBalance() {
        Account account = Account.open("user-1", new BigDecimal("100.00"));

        account.credit(new BigDecimal("25.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("125.50");
    }

    @Test
    @DisplayName("BR-003: debitar una cuenta BLOCKED lanza InactiveAccountException")
    void debit_throwsInactiveAccount_whenAccountIsBlocked() {
        Account account = Account.reconstitute(UUID.randomUUID(), "user-1", new BigDecimal("100.00"), AccountStatus.BLOCKED);

        assertThatThrownBy(() -> account.debit(new BigDecimal("10.00")))
                .isInstanceOf(InactiveAccountException.class);
    }

    @Test
    @DisplayName("BR-003: acreditar una cuenta CLOSED lanza InactiveAccountException")
    void credit_throwsInactiveAccount_whenAccountIsClosed() {
        Account account = Account.reconstitute(UUID.randomUUID(), "user-1", new BigDecimal("100.00"), AccountStatus.CLOSED);

        assertThatThrownBy(() -> account.credit(new BigDecimal("10.00")))
                .isInstanceOf(InactiveAccountException.class);
    }

    @Test
    @DisplayName("Defensa de precondición: debitar un monto cero o negativo lanza IllegalArgumentException")
    void debit_rejectsNonPositiveAmount() {
        Account account = Account.open("user-1", new BigDecimal("100.00"));

        assertThatThrownBy(() -> account.debit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal("-5.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
