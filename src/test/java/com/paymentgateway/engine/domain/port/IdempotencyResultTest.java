package com.paymentgateway.engine.domain.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyResultTest {

    @Test
    void of_withValidData_holdsOutcomeStatusAndBody() {
        IdempotencyResult result = IdempotencyResult.of(IdempotencyResult.Outcome.COMPLETED, 201, "{\"id\":\"x\"}");

        assertThat(result.getOutcome()).isEqualTo(IdempotencyResult.Outcome.COMPLETED);
        assertThat(result.getHttpStatus()).isEqualTo(201);
        assertThat(result.getResponseBody()).isEqualTo("{\"id\":\"x\"}");
    }

    @Test
    void of_withBlankBody_throws_soAnEmptyResponseIsNeverCachedForReplay() {
        assertThatThrownBy(() -> IdempotencyResult.of(IdempotencyResult.Outcome.FAILED, 422, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_withNullOutcome_throws() {
        assertThatThrownBy(() -> IdempotencyResult.of(null, 201, "{}"))
                .isInstanceOf(NullPointerException.class);
    }
}