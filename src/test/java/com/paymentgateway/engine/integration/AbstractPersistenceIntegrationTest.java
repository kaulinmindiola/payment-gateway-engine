package com.paymentgateway.engine.integration;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Base para tests de persistencia.
 * Mantiene una única instancia de Postgres abierta durante toda la ejecución del build
 * para ser compatible con el caché de contexto de Spring Boot.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
public abstract class AbstractPersistenceIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16");
        POSTGRES.start(); // Inicia el contenedor una sola vez para toda la JVM
    }
}