package com.paymentgateway.engine.application.handler;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public sealed interface TransferCommand {

    record Internal(UUID sourceAccountId, UUID requestingUserId, UUID targetAccountId,
                     BigDecimal amount, String idempotencyKey) implements TransferCommand {
        public Internal {
            Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
            Objects.requireNonNull(requestingUserId, "requestingUserId must not be null");
            Objects.requireNonNull(targetAccountId, "targetAccountId must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey must not be blank");
            }
        }
    }

    record External(UUID sourceAccountId, UUID requestingUserId, UUID targetProviderId,
                     UUID targetBankId, String targetExternalReference,
                     BigDecimal amount, String idempotencyKey) implements TransferCommand {
        public External {
            Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
            Objects.requireNonNull(requestingUserId, "requestingUserId must not be null");
            Objects.requireNonNull(targetProviderId, "targetProviderId must not be null");
            Objects.requireNonNull(targetBankId, "targetBankId must not be null");
            if (targetExternalReference == null || targetExternalReference.isBlank()) {
                throw new IllegalArgumentException("targetExternalReference must not be blank");
            }
            Objects.requireNonNull(amount, "amount must not be null");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey must not be blank");
            }
        }
    }
}