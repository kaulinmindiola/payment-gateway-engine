package com.paymentgateway.engine.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 */
public abstract class AbstractExternalProviderIntegrationTest extends AbstractApplicationIntegrationTest {

    // Instanciado estáticamente para vivir tanto como la JVM y el caché de Spring,
    // garantizando que el puerto no cambie entre clases de test.
    protected static final WireMockServer wireMock;

    static {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort().usingFilesUnderDirectory("wiremock"));
        wireMock.start();
    }

    @DynamicPropertySource
    static void authorizationProviderProperties(DynamicPropertyRegistry registry) {
        registry.add("payment-gateway.authorization-provider.base-url", wireMock::baseUrl);
    }

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetExternalProviderState() {
        // Vuelve el Scenario "external-provider-flaky" a su estado inicial
        wireMock.resetToDefaultMappings();
        
        // Evita fugas de estado (Circuitos Abiertos) entre diferentes tests
        circuitBreakerRegistry.circuitBreaker("authorizationProvider").reset();
    }
}