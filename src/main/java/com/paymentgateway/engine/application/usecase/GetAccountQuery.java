package com.paymentgateway.engine.application.usecase;

import java.util.Objects;
import java.util.UUID;

public final class GetAccountQuery {

    private final UUID accountId;
    private final UUID requestingUserId;

    private GetAccountQuery(UUID accountId, UUID requestingUserId) {
        this.accountId = accountId;
        this.requestingUserId = requestingUserId;
    }

    public static GetAccountQuery of(UUID accountId, UUID requestingUserId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(requestingUserId, "requestingUserId must not be null");
        return new GetAccountQuery(accountId, requestingUserId);
    }

    public UUID getAccountId() { return accountId; }
    public UUID getRequestingUserId() { return requestingUserId; }
}