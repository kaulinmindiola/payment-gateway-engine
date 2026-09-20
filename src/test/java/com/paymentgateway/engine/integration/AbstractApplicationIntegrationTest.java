package com.paymentgateway.engine.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para tests que necesitan el contexto Spring COMPLETO (no un slice) --
 * a diferencia de AbstractPersistenceIntegrationTest (@DataJpaTest, Fase 3),
 * aquí necesitamos @Transactional real de InternalTransferHandler operando
 * bajo hilos concurrentes reales, lo cual requiere el contenedor de Spring
 * completo con su gestor de transacciones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public abstract class AbstractApplicationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    // Evita que la contención del POOL de conexiones (default Hikari: 10)
    // se mezcle con la contención de LOCKS de fila que estos tests miden
    // deliberadamente (ver nota de diseño, Fase 5 Paso 7).
    @DynamicPropertySource
    static void increaseConnectionPoolForConcurrencyTests(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "60");
    }
}