package com.paymentgatewayengine.domain.exception;

import com.paymentgatewayengine.domain.account.AccountStatus;

import java.util.UUID;

/**
 * BR-003: ambas cuentas (origen y destino) deben estar ACTIVE para participar
 * en una transferencia. Lanzada por Account.debit()/credit() cuando la cuenta
 * no está en estado ACTIVE.
 */
public class InactiveAccountException extends DomainException {

    private final UUID accountId;
    private final AccountStatus status;

    public InactiveAccountException(UUID accountId, AccountStatus status) {
        super("La cuenta " + accountId + " no está activa (estado actual: " + status + ")");
        this.accountId = accountId;
        this.status = status;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
