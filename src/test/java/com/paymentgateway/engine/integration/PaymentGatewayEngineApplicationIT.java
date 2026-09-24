package com.paymentgateway.engine.integration;

import org.junit.jupiter.api.Test;

/**
 * Carga completa del ApplicationContext contra Postgres + Redis efímeros
 * (Testcontainers). Antes dependía implícitamente de un Postgres local en
 * localhost:5432 (Fase 0) -- acoplamiento oculto que habría fallado en CI.
 */
class PaymentGatewayEngineApplicationIT extends AbstractApplicationIntegrationTest {

    @Test
    void contextLoads() {
    }
}