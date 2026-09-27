package com.paymentgateway.engine.infrastructure.adapter.http;

import com.paymentgateway.engine.domain.port.AuthorizationResult;

import java.time.Instant;

record AuthorizationHttpResponseBody(String status, String providerReference, String reason, Instant processedAt) {

    AuthorizationResult toDomainResult() {
        if ("APPROVED".equals(status)) {
            if (providerReference == null || providerReference.isBlank()) {
                throw new AuthorizationProtocolException("APPROVED response without providerReference", null);
            }
            return AuthorizationResult.approved(providerReference);
        }
        if ("DECLINED".equals(status)) {
            if (reason == null || reason.isBlank()) {
                throw new AuthorizationProtocolException("DECLINED response without reason", null);
            }
            return AuthorizationResult.declined(reason);
        }
        throw new AuthorizationProtocolException("Unexpected authorization status: " + status, null);
    }
}