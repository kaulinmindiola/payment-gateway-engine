# Payment Gateway Engine

Backend transaccional (Java 21 / Spring Boot 3) que simula un core bancario,
con foco en consistencia bajo concurrencia, idempotencia real e integración
resiliente con un proveedor de autorización externo simulado.

Este proyecto **no busca ser un producto de negocio**: es una pieza de
portafolio orientada a demostrar ingeniería de nivel producción en el dominio
más intolerante a errores — dinero.

## Estado del proyecto

En construcción, siguiendo el roadmap de `IMPLEMENTATION_PLAN.md`.
Actualmente en **Fase 1 — Repositorio y esqueleto ejecutable**.

## Cómo ejecutar

_(pendiente — se completa progresivamente a medida que avanzan las fases del
`IMPLEMENTATION_PLAN.md`; el entorno completo con `docker compose up` llega
en la Fase 15)_

## Documentación

- [Context Maestro](docs/payment-gateway-engine-context-maestro.md) — descubrimiento, decisiones de producto
- [SRS](docs/srs-payment-gateway-engine.md) — requisitos verificables
- [SDD](docs/msdd-payment-gateway-engine.md) — diseño técnico
- [ADR](docs/madr-payment-gateway-engine.md) — decisiones de arquitectura (ADR-0001 a ADR-0007). Se separarán en `docs/adr/` como parte de esta misma fase.

## Licencia

[MIT](LICENSE)
