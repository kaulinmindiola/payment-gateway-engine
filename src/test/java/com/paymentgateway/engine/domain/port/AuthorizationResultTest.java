package com.paymentgateway.engine.domain.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationResultTest {

    @Test
    void approved_withProviderReference_isApprovedTrue() {
        AuthorizationResult result = AuthorizationResult.approved("AUTH-9F3E2C");
        assertThat(result.isApproved()).isTrue();
        assertThat(result.getProviderReference()).isEqualTo("AUTH-9F3E2C");
        assertThat(result.getDeclineReason()).isNull();
    }

    @Test
    void declined_withReason_isApprovedFalse() {
        AuthorizationResult result = AuthorizationResult.declined("INSUFFICIENT_FUNDS_AT_DESTINATION");
        assertThat(result.isApproved()).isFalse();
        assertThat(result.getDeclineReason()).isEqualTo("INSUFFICIENT_FUNDS_AT_DESTINATION");
        assertThat(result.getProviderReference()).isNull();
    }

    @Test
    void declined_withBlankReason_throws() {
        assertThatThrownBy(() -> AuthorizationResult.declined(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approved_withNullProviderReference_throws() {
        assertThatThrownBy(() -> AuthorizationResult.approved(null))
                .isInstanceOf(NullPointerException.class);
    }
}