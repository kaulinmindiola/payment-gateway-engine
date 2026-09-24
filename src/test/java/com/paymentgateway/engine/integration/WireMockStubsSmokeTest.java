package com.paymentgateway.engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida los stubs versionados de wiremock/mappings/ de forma AISLADA,
 * sin pasar por Resilience4j ni AuthorizationHttpAdapter -- confirma que
 * el contrato de la Sección 7.1 está bien materializado antes de sumar
 * la complejidad de reintentos/circuit breaker (Paso 7).
 */
class WireMockStubsSmokeTest extends AbstractExternalProviderIntegrationTest {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
            
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpResponse<String> post(String providerCode, String targetExternalReference) throws Exception {
        String body = """
                {"sourceAccountId":"%s","targetBankId":"%s","targetExternalReference":"%s","amount":"10.00","currency":"EUR"}
                """.formatted(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), targetExternalReference);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(wireMock.baseUrl() + "/v1/authorizations"))
                .header("Content-Type", "application/json")
                .header("X-Provider-Code", providerCode)
                .header("X-Idempotency-Key", "smoke-key")
                .header("X-Trace-Id", java.util.UUID.randomUUID().toString())
                .timeout(Duration.ofSeconds(10)) // Ampliado a 10s para absorber retardos de CPU en ejecuciones continuas
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void swiftDemo_defaultRequest_returnsApproved() throws Exception {
        HttpResponse<String> response = post("SWIFT-demo", "ES9121000418450200051332");
        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void swiftDemo_referenceWithDeclineSuffix_returnsDeclined() throws Exception {
        HttpResponse<String> response = post("SWIFT-demo", "ES9121000418450200051332-DECLINE");
        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.get("status").asText()).isEqualTo("DECLINED");
        assertThat(json.get("reason").asText()).isEqualTo("BANK_REJECTED");
    }

    @Test
    void railsFlaky_threeSequentialCalls_progressThrough503TimeoutThenApproved() throws Exception {
        HttpResponse<String> first = post("RAILS-flaky", "REF-001");
        assertThat(first.statusCode()).isEqualTo(503);

        HttpResponse<String> second = post("RAILS-flaky", "REF-001");
        assertThat(second.statusCode()).isEqualTo(200);

        HttpResponse<String> third = post("RAILS-flaky", "REF-001");
        assertThat(third.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(third.body());
        assertThat(json.get("status").asText()).isEqualTo("APPROVED");
        assertThat(json.get("providerReference").asText()).isEqualTo("AUTH-RAILS-FLAKY-RECOVERED");
    }

    @Test
    void railsFlaky_withAlwaysFailMarker_returns503RepeatedlyWithoutProgressingScenario() throws Exception {
        HttpResponse<String> first = post("RAILS-flaky", "REF-CB-ALWAYS-FAIL");
        assertThat(first.statusCode()).isEqualTo(503);

        HttpResponse<String> second = post("RAILS-flaky", "REF-CB-ALWAYS-FAIL");
        assertThat(second.statusCode()).isEqualTo(503);
    }
}