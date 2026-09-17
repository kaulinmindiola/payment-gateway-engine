package com.paymentgateway.engine.domain.port;

import java.util.Objects;

public final class IdempotencyResult {

    public enum Outcome { COMPLETED, FAILED }

    private final Outcome outcome;
    private final int httpStatus;
    private final String responseBody;

    private IdempotencyResult(Outcome outcome, int httpStatus, String responseBody) {
        this.outcome = outcome;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public static IdempotencyResult of(Outcome outcome, int httpStatus, String responseBody) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalArgumentException("responseBody must not be blank");
        }
        return new IdempotencyResult(outcome, httpStatus, responseBody);
    }

    public Outcome getOutcome() { return outcome; }
    public int getHttpStatus() { return httpStatus; }
    public String getResponseBody() { return responseBody; }
}