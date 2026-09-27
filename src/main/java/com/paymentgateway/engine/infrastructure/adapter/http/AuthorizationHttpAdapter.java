package com.paymentgateway.engine.infrastructure.adapter.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paymentgateway.engine.domain.port.AuthorizationPort;
import com.paymentgateway.engine.domain.port.AuthorizationRequest;
import com.paymentgateway.engine.domain.port.AuthorizationResult;
import com.paymentgateway.engine.infrastructure.filter.TraceContext;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Duration;

@Component
public class AuthorizationHttpAdapter implements AuthorizationPort {

    private final RestClient restClient;

    public AuthorizationHttpAdapter(RestClient.Builder restClientBuilder,
                                     @Value("${payment-gateway.authorization-provider.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));

        this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    @CircuitBreaker(name = "authorizationProvider", fallbackMethod = "circuitOpenFallback")
    @Retry(name = "authorizationProvider")
    public AuthorizationResult authorize(AuthorizationRequest request) {
        try {
            AuthorizationHttpResponseBody response = restClient.post()
                    .uri("/v1/authorizations")
                    .header("X-Provider-Code", request.getProviderCode())
                    .header("X-Idempotency-Key", request.getIdempotencyKey())
                    .header("X-Trace-Id", TraceContext.currentOrNew())
                    .body(AuthorizationHttpRequestBody.from(request))
                    .retrieve()
                    .body(AuthorizationHttpResponseBody.class);

            if (response == null) {
                throw new AuthorizationProtocolException("Authorization provider returned an empty response body", null);
            }
            return response.toDomainResult();

        } catch (HttpServerErrorException ex) {
            throw new AuthorizationUnavailableException(
                    "Authorization provider returned a server error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new AuthorizationTimeoutException(
                    "Authorization provider did not respond within the configured timeout", ex);
        } catch (RestClientException ex) {
            if (hasCause(ex, HttpMessageNotReadableException.class) || hasCause(ex, JsonProcessingException.class)) {
                throw new AuthorizationProtocolException("Authorization provider returned an unreadable response", ex);
            }
            if (hasCause(ex, IOException.class)) {
                throw new AuthorizationTimeoutException(
                        "Authorization provider did not respond within the configured timeout", ex);
            }
            throw ex;
        }
    }

    /**
     * Helper para verificar recursivamente si la excepción fue causada por un tipo específico.
     */
    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Solo se invoca para CallNotPermittedException (circuito OPEN): el tipo
     * del segundo parámetro restringe el fallback a esa excepción; el resto
     * se propaga tal cual. La traduce a la jerarquía técnica propia para que
     * TransferMoney libere la idempotency key sin que
     * application/ conozca Resilience4j.
     */
    private AuthorizationResult circuitOpenFallback(AuthorizationRequest request, CallNotPermittedException ex) {
        throw new AuthorizationUnavailableException("Circuit breaker is OPEN for authorization provider", ex);
    }
}