package com.paymentgateway.engine.domain.port;

public final class IdempotencyClaim {

    public enum ClaimStatus {
        ACQUIRED,     // no existía claim; el caller puede proceder con la transferencia
        IN_PROGRESS,  // claim existente aún no terminal; el caller debe responder 409
        COMPLETED,    // claim terminal; el caller debe devolver la respuesta cacheada
        FAILED        // claim terminal; el caller debe devolver la respuesta cacheada
    }

    private final ClaimStatus status;
    private final Integer cachedHttpStatus;
    private final String cachedResponseBody;

    private IdempotencyClaim(ClaimStatus status, Integer cachedHttpStatus, String cachedResponseBody) {
        this.status = status;
        this.cachedHttpStatus = cachedHttpStatus;
        this.cachedResponseBody = cachedResponseBody;
    }

    public static IdempotencyClaim acquired() {
        return new IdempotencyClaim(ClaimStatus.ACQUIRED, null, null);
    }

    public static IdempotencyClaim inProgress() {
        return new IdempotencyClaim(ClaimStatus.IN_PROGRESS, null, null);
    }

    public static IdempotencyClaim terminal(ClaimStatus status, int cachedHttpStatus, String cachedResponseBody) {
        if (status != ClaimStatus.COMPLETED && status != ClaimStatus.FAILED) {
            throw new IllegalArgumentException("Terminal claim must be COMPLETED or FAILED");
        }
        if (cachedResponseBody == null || cachedResponseBody.isBlank()) {
            throw new IllegalArgumentException("cachedResponseBody must not be blank for a terminal claim");
        }
        return new IdempotencyClaim(status, cachedHttpStatus, cachedResponseBody);
    }

    public ClaimStatus getStatus() { return status; }
    public Integer getCachedHttpStatus() { return cachedHttpStatus; }
    public String getCachedResponseBody() { return cachedResponseBody; }
}