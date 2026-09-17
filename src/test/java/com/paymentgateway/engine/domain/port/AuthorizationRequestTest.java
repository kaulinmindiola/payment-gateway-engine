package com.paymentgateway.engine.domain.port;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationRequestTest {

    @Test
    void of_withValidData_normalizesAmountScale() {
        AuthorizationRequest request = AuthorizationRequest.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ES9121000418450200051332", new BigDecimal("1250"), "idem-key-1");

        assertThat(request.getAmount()).isEqualByComparingTo("1250.00");
    }

    @Test
    void of_withNonPositiveAmount_throws() {
        assertThatThrownBy(() -> AuthorizationRequest.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "REF", BigDecimal.ZERO, "idem-key-2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_withBlankReference_throws() {
        assertThatThrownBy(() -> AuthorizationRequest.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                " ", new BigDecimal("10.00"), "idem-key-3"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}