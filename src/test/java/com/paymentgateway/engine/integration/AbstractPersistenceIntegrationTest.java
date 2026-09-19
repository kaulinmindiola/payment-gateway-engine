package com.paymentgateway.engine.integration;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;
/**
 * Base para tests de persistencia (Fase 3+). Levanta un Postgres real vía
 * Testcontainers — @ServiceConnection conecta automáticamente el DataSource
 * del slice de test a este contenedor, sin tocar application.yml.
 * Flyway corre las 6 migraciones contra este contenedor efímero en cada
 * arranque de contexto (mismo comportamiento que contra pge-postgres local).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
public abstract class AbstractPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
}