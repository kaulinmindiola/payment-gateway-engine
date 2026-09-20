package com.paymentgateway.engine.application.usecase;

import java.util.Objects;
import java.util.UUID;

public final class GetTransactionQuery {

    private final UUID transactionId;
    private final UUID requestingUserId;

    private GetTransactionQuery(UUID transactionId, UUID requestingUserId) {
        this.transactionId = transactionId;
        this.requestingUserId = requestingUserId;
    }

    public static GetTransactionQuery of(UUID transactionId, UUID requestingUserId) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(requestingUserId, "requestingUserId must not be null");
        return new GetTransactionQuery(transactionId, requestingUserId);
    }

    public UUID getTransactionId() { return transactionId; }
    public UUID getRequestingUserId() { return requestingUserId; }
}