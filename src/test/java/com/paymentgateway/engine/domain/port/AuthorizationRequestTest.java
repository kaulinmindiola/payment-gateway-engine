package com.paymentgateway.engine.domain.port;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationRequestTest {

    private AuthorizationRequest build(String providerCode, String targetExternalReference,
                                        BigDecimal amount, String currency, String idempotencyKey) {
        return AuthorizationRequest.of(UUID.randomUUID(), UUID.randomUUID(), providerCode,
                UUID.randomUUID(), targetExternalReference, amount, currency, idempotencyKey);
    }

    @Test
    void of_withValidData_normalizesAmountScale() {
        AuthorizationRequest request = build(
                "SWIFT-demo", "ES9121000418450200051332", new BigDecimal("1250"), "EUR", "idem-key-1");

        assertThat(request.getAmount()).isEqualByComparingTo("1250.00");
        assertThat(request.getProviderCode()).isEqualTo("SWIFT-demo");
        assertThat(request.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void of_withNonPositiveAmount_throws() {
        assertThatThrownBy(() -> build("SWIFT-demo", "REF", BigDecimal.ZERO, "EUR", "idem-key-2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_withBlankReference_throws() {
        assertThatThrownBy(() -> build("SWIFT-demo", " ", new BigDecimal("10.00"), "EUR", "idem-key-3"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_withBlankProviderCode_throws() {
        assertThatThrownBy(() -> build(" ", "REF", new BigDecimal("10.00"), "EUR", "idem-key-4"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_withBlankCurrency_throws() {
        assertThatThrownBy(() -> build("SWIFT-demo", "REF", new BigDecimal("10.00"), " ", "idem-key-5"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}