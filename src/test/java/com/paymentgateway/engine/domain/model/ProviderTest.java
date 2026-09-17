package com.paymentgateway.engine.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderTest {

    @Test
    void reconstitute_withActiveStatus_isActiveReturnsTrue() {
        Provider provider = Provider.reconstitute(
                UUID.randomUUID(), "SWIFT-demo", "SWIFT Demo Rail", ProviderStatus.ACTIVE);
        assertThat(provider.isActive()).isTrue();
    }

    @Test
    void reconstitute_withInactiveStatus_isActiveReturnsFalse() {
        Provider provider = Provider.reconstitute(
                UUID.randomUUID(), "SWIFT-demo", "SWIFT Demo Rail", ProviderStatus.INACTIVE);
        assertThat(provider.isActive()).isFalse();
    }

    @Test
    void reconstitute_withNullCode_throws() {
        assertThatThrownBy(() -> Provider.reconstitute(UUID.randomUUID(), null, "name", ProviderStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
    }
}