package com.paymentgateway.engine.infrastructure.adapter.http;

import com.paymentgateway.engine.domain.port.AuthorizationResult;

import java.time.Instant;

record AuthorizationHttpResponseBody(String status, String providerReference, String reason, Instant processedAt) {
    AuthorizationResult toDomainResult() {
        return "APPROVED".equals(status)
                ? AuthorizationResult.approved(providerReference)
                : AuthorizationResult.declined(reason);
    }
}