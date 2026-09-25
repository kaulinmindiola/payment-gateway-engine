package com.paymentgateway.engine.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base para tests que necesitan el contexto Spring COMPLETO (no un slice) --
 * a diferencia de AbstractPersistenceIntegrationTest (@DataJpaTest, Fase 3),
 * aquí necesitamos @Transactional real de InternalTransferHandler operando
 * bajo hilos concurrentes reales, lo cual requiere el contenedor de Spring
 * completo con su gestor de transacciones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractApplicationIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);
    
    // Arranca los contenedores una sola vez para toda la suite de pruebas (JVM).
    // Spring reutilizará el Hikari pool y estos contenedores seguirán vivos.
    static {
        POSTGRES.start();
        REDIS.start();
    }

    // Evita que la contención del POOL de conexiones (default Hikari: 10)
    // se mezcle con la contención de LOCKS de fila que estos tests miden
    // deliberadamente (ver nota de diseño, Fase 5 Paso 7).
    @DynamicPropertySource
    static void increaseConnectionPoolForConcurrencyTests(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "60");
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}