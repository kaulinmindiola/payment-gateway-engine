package com.paymentgatewayengine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentGatewayEngineApplicationTests {

    /**
     * Verifica únicamente que el ApplicationContext de Spring se construye sin
     * errores: JPA conecta a Postgres, el bean de Redis se instancia, y todos
     * los beans autoconfigurados resuelven correctamente sus dependencias.
     *
     * Deliberadamente sin aserciones de negocio — no hay dominio ni casos de
     * uso todavía. Las verticales funcionales (Fase 4 en adelante) traen sus
     * propios tests con REQ-FUNC-xxx referenciado explícitamente.
     *
     * Requiere Postgres/Redis corriendo (docker-compose.dev.yml) y las
     * variables de .env exportadas en el shell — se reemplaza por
     * Testcontainers en Fase 3, eliminando esta dependencia manual.
     */
    @Test
    void contextLoads() {
    }
}
