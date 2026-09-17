package com.paymentgateway.engine.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalBankTest {

    private final UUID providerId = UUID.randomUUID();

    @Test
    void reconstitute_withActiveStatus_isActiveReturnsTrue() {
        ExternalBank bank = ExternalBank.reconstitute(
                UUID.randomUUID(), providerId, "DE-001", "Deutsche Demo Bank", "DE", "EUR", ExternalBankStatus.ACTIVE);
        assertThat(bank.isActive()).isTrue();
    }

    @Test
    void reconstitute_withInactiveStatus_isActiveReturnsFalse() {
        ExternalBank bank = ExternalBank.reconstitute(
                UUID.randomUUID(), providerId, "DE-001", "Deutsche Demo Bank", "DE", "EUR", ExternalBankStatus.INACTIVE);
        assertThat(bank.isActive()).isFalse();
    }

    @Test
    void reconstitute_withNullProviderId_throws() {
        assertThatThrownBy(() -> ExternalBank.reconstitute(
                UUID.randomUUID(), null, "DE-001", "Deutsche Demo Bank", "DE", "EUR", ExternalBankStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
    }
}