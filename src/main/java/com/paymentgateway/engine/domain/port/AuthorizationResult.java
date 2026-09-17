package com.paymentgateway.engine.domain.port;

import java.util.Objects;

public final class AuthorizationResult {

    public enum Status { APPROVED, DECLINED }

    private final Status status;
    private final String declineReason;      // presente solo si DECLINED
    private final String providerReference;  // presente solo si APPROVED

    private AuthorizationResult(Status status, String declineReason, String providerReference) {
        this.status = status;
        this.declineReason = declineReason;
        this.providerReference = providerReference;
    }

    public static AuthorizationResult approved(String providerReference) {
        Objects.requireNonNull(providerReference, "providerReference must not be null when APPROVED");
        return new AuthorizationResult(Status.APPROVED, null, providerReference);
    }

    public static AuthorizationResult declined(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank when DECLINED");
        }
        return new AuthorizationResult(Status.DECLINED, reason, null);
    }

    public boolean isApproved() { return status == Status.APPROVED; }
    public Status getStatus() { return status; }
    public String getDeclineReason() { return declineReason; }
    public String getProviderReference() { return providerReference; }
}