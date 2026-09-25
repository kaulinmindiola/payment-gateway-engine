package com.paymentgateway.engine.domain.port;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionHistoryQueryTest {

    private TransactionHistoryQuery query(Instant from, Instant to, int page, int size) {
        return new TransactionHistoryQuery(UUID.randomUUID(), null, null, from, to, page, size);
    }

    @Test
    void validQuery_withMaxSize_isAccepted() {
        assertThatCode(() -> query(null, null, 0, 100)).doesNotThrowAnyException();
    }

    @Test
    void sizeAboveMax_throws() {
        assertThatThrownBy(() -> query(null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sizeBelowOne_throws() {
        assertThatThrownBy(() -> query(null, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePage_throws() {
        assertThatThrownBy(() -> query(null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dateFromAfterDateTo_throws() {
        assertThatThrownBy(() -> query(Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalDates_isAcceptedAsEmptyRange() {
        Instant same = Instant.parse("2026-01-01T00:00:00Z");
        assertThatCode(() -> query(same, same, 0, 20)).doesNotThrowAnyException();
    }
}