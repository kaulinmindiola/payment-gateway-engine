package com.paymentgateway.engine.domain.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyClaimTest {

    @Test
    void acquired_hasAcquiredStatusAndNoCachedData() {
        IdempotencyClaim claim = IdempotencyClaim.acquired();
        assertThat(claim.getStatus()).isEqualTo(IdempotencyClaim.ClaimStatus.ACQUIRED);
        assertThat(claim.getCachedResponseBody()).isNull();
    }

    @Test
    void inProgress_hasInProgressStatus() {
        IdempotencyClaim claim = IdempotencyClaim.inProgress();
        assertThat(claim.getStatus()).isEqualTo(IdempotencyClaim.ClaimStatus.IN_PROGRESS);
    }

    @Test
    void terminal_withCompletedStatus_holdsCachedResponse() {
        IdempotencyClaim claim = IdempotencyClaim.terminal(
                IdempotencyClaim.ClaimStatus.COMPLETED, 201, "{\"id\":\"...\"}");
        assertThat(claim.getCachedHttpStatus()).isEqualTo(201);
        assertThat(claim.getCachedResponseBody()).isEqualTo("{\"id\":\"...\"}");
    }

    @Test
    void terminal_withNonTerminalStatus_throws() {
        assertThatThrownBy(() -> IdempotencyClaim.terminal(
                IdempotencyClaim.ClaimStatus.ACQUIRED, 201, "body"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminal_withBlankResponseBody_throws() {
        assertThatThrownBy(() -> IdempotencyClaim.terminal(
                IdempotencyClaim.ClaimStatus.FAILED, 422, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}