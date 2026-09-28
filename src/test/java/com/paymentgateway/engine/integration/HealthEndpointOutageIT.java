package com.paymentgateway.engine.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * /actuator/health refleja DOWN cuando una dependencia real falla.
 * Contenedores DEDICADOS (no los singleton de AbstractApplicationIntegrationTest):
 * este test los detiene a propósito. Mismo criterio que RedisOutageIdempotencyIT.
 * Orden explícito: primero lo no destructivo, luego Redis y al final Postgres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)   // Spring Boot desactiva la exportación de métricas en tests por defecto
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthEndpointOutageIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Solo en este test: sin esto, con Postgres detenido el health esperaría
        // los 30 s por defecto de Hikari para obtener una conexión.
        registry.add("spring.datasource.hikari.connection-timeout", () -> "1000");
        registry.add("spring.datasource.hikari.validation-timeout", () -> "500");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    @Order(1)
    void onlyHealthAndPrometheusAreExposed() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());

        // springdoc is disabled outside the docker profile
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());

        // Verifica que health sí está expuesto
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // Verifica que prometheus sí está expuesto
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("resilience4j_circuitbreaker_state")))
                .andExpect(content().string(containsString("application=\"payment-gateway-engine\"")));
    }

    @Test
    @Order(2)
    void allDependenciesHealthy_reportsUpWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.circuitBreakers.status").value("UP"))
                // show-details: never -> sin versiones ni detalles internos
                .andExpect(jsonPath("$.components.db.details").doesNotExist())
                .andExpect(jsonPath("$.components.redis.details").doesNotExist());
    }

    @Test
    @Order(3)
    void openCircuit_reportsDown_andRecoversWhenClosed() throws Exception {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("authorizationProvider");
        try {
            cb.transitionToOpenState();

            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("DOWN"))
                    .andExpect(jsonPath("$.components.circuitBreakers.status").value("DOWN"))
                    .andExpect(jsonPath("$.components.db.status").value("UP"));
        } finally {
            cb.reset();
        }

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(4)
    void redisStopped_reportsRedisDown() throws Exception {
        REDIS.stop();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.redis.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @Order(5)
    void postgresStopped_reportsDbDown() throws Exception {
        POSTGRES.stop();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("DOWN"));
    }
}