package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.domain.port.IdempotencyClaim;
import com.paymentgateway.engine.domain.port.IdempotencyResult;
import com.paymentgateway.engine.infrastructure.adapter.redis.RedisIdempotencyAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisIdempotencyAdapterIT extends AbstractApplicationIntegrationTest {

    @Autowired
    private RedisIdempotencyAdapter adapter;

    @Test
    void tryBegin_forNewKey_returnsAcquired() {
        String key = "test-" + UUID.randomUUID();
        IdempotencyClaim claim = adapter.tryBegin(key);
        assertThat(claim.getStatus()).isEqualTo(IdempotencyClaim.ClaimStatus.ACQUIRED);
    }

    @Test
    void tryBegin_forAlreadyInProgressKey_returnsInProgress() {
        String key = "test-" + UUID.randomUUID();
        adapter.tryBegin(key); // primera reclamación -> ACQUIRED

        IdempotencyClaim second = adapter.tryBegin(key);
        assertThat(second.getStatus()).isEqualTo(IdempotencyClaim.ClaimStatus.IN_PROGRESS);
    }

    @Test
    void complete_thenTryBegin_returnsTerminalWithCachedResponse() {
        String key = "test-" + UUID.randomUUID();
        adapter.tryBegin(key);

        adapter.complete(key, IdempotencyResult.of(IdempotencyResult.Outcome.COMPLETED, 201, "{\"id\":\"abc\"}"));

        IdempotencyClaim claim = adapter.tryBegin(key);
        assertThat(claim.getStatus()).isEqualTo(IdempotencyClaim.ClaimStatus.COMPLETED);
        assertThat(claim.getCachedHttpStatus()).isEqualTo(201);
        assertThat(claim.getCachedResponseBody()).isEqualTo("{\"id\":\"abc\"}");
    }

    @Test
    void complete_withFailedOutcome_returnsTerminalFailed() {
        String key = "test-" + UUID.randomUUID();
        adapter.tryBegin(key);

        adapter.complete(key, IdempotencyResult.of(
                IdempotencyResult.Outcome.FAILED, 422, "{\"detail\":\"Insufficient balance\"}"));

        IdempotencyClaim claim = adapter.tryBegin(key);
        assertThat(claim.getStatus()).isEqualTo(IdempotencyClaim.ClaimStatus.FAILED);
        assertThat(claim.getCachedHttpStatus()).isEqualTo(422);
    }
    @Test
    void release_afterAcquired_allowsNewAcquisitionOfSameKey() {
        String key = "test-" + UUID.randomUUID();
        adapter.tryBegin(key); // ACQUIRED

        adapter.release(key);

        IdempotencyClaim claim = adapter.tryBegin(key);
        assertThat(claim.getStatus()).isEqualTo(IdempotencyClaim.ClaimStatus.ACQUIRED);
    }
}