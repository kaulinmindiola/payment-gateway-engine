package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.AccountStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID ownerId,
        BigDecimal balance,
        AccountStatus status
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(), account.getOwnerId(), account.getBalance(), account.getStatus());
    }
}