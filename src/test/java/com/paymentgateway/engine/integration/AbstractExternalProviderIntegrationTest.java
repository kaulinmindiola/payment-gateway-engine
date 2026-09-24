package com.paymentgateway.engine.integration;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * RISK-007: carga los MISMOS archivos de wiremock/mappings/ que usará el
 * WireMock standalone de docker-compose (Fase 13) -- .usingFilesUnderDirectory
 * apunta a la carpeta compartida en la raíz del repo (working directory de
 * Maven durante failsafe), sin duplicar mappings en src/test/resources.
 */
public abstract class AbstractExternalProviderIntegrationTest extends AbstractApplicationIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderDirectory("wiremock"))
            .build();

    @DynamicPropertySource
    static void authorizationProviderProperties(DynamicPropertyRegistry registry) {
        registry.add("payment-gateway.authorization-provider.base-url", wireMock::baseUrl);
    }

    @BeforeEach
    void resetWireMockState() {
        // Vuelve el Scenario "external-provider-flaky" a su estado inicial
        // (Started) antes de cada test -- sin esto, tests posteriores
        // heredarían el estado THIRD_ATTEMPT del test anterior.
        wireMock.resetToDefaultMappings();
    }
}