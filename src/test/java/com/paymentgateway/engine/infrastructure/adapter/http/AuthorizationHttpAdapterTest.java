package com.paymentgateway.engine.infrastructure.adapter.http;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.paymentgateway.engine.domain.port.AuthorizationRequest;
import com.paymentgateway.engine.domain.port.AuthorizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationHttpAdapterTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AuthorizationHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AuthorizationHttpAdapter(RestClient.builder(), wireMock.baseUrl());
    }

    private AuthorizationRequest sampleRequest() {
        return AuthorizationRequest.of(UUID.randomUUID(), UUID.randomUUID(), "SWIFT-demo",
                UUID.randomUUID(), "ES9121000418450200051332", new BigDecimal("100.00"), "EUR", "idem-key-1");
    }

    @Test
    void authorize_whenProviderApproves_returnsApprovedResult() {
        wireMock.stubFor(post(urlEqualTo("/v1/authorizations"))
                .withHeader("X-Provider-Code", equalTo("SWIFT-demo"))
                .willReturn(okJson("""
                        {"status":"APPROVED","providerReference":"AUTH-1","reason":null,"processedAt":"2026-01-01T00:00:00Z"}
                        """)));

        AuthorizationResult result = adapter.authorize(sampleRequest());

        assertThat(result.isApproved()).isTrue();
        assertThat(result.getProviderReference()).isEqualTo("AUTH-1");
    }

    @Test
    void authorize_whenProviderDeclines_returnsDeclinedResult() {
        wireMock.stubFor(post(urlEqualTo("/v1/authorizations"))
                .willReturn(okJson("""
                        {"status":"DECLINED","providerReference":null,"reason":"INSUFFICIENT_FUNDS_AT_DESTINATION","processedAt":"2026-01-01T00:00:00Z"}
                        """)));

        AuthorizationResult result = adapter.authorize(sampleRequest());

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getDeclineReason()).isEqualTo("INSUFFICIENT_FUNDS_AT_DESTINATION");
    }

    @Test
    void authorize_whenProviderReturns503_throwsAuthorizationUnavailableException() {
        wireMock.stubFor(post(urlEqualTo("/v1/authorizations")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> adapter.authorize(sampleRequest()))
                .isInstanceOf(AuthorizationUnavailableException.class);
    }

   @Test
    void authorize_whenProviderTimesOut_throwsAuthorizationTimeoutException() {
        // Delay (3s) > readTimeout (2s) configurado en el adapter
        wireMock.stubFor(post(urlEqualTo("/v1/authorizations"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(3000)
                        .withStatus(200)));

        assertThatThrownBy(() -> adapter.authorize(sampleRequest()))
                .isInstanceOf(AuthorizationTimeoutException.class);
    }

    @Test
    void authorize_sendsAmountAsStringNotNumber() {
        wireMock.stubFor(post(urlEqualTo("/v1/authorizations"))
                .willReturn(okJson("""
                        {"status":"APPROVED","providerReference":"AUTH-2","reason":null,"processedAt":"2026-01-01T00:00:00Z"}
                        """)));

        adapter.authorize(sampleRequest());

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/authorizations"))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("100.00")))
                .withHeader("X-Idempotency-Key", equalTo("idem-key-1")));
    }
}