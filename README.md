# Payment Gateway Engine

Backend transaccional (Java 21 / Spring Boot 4) que simula un core bancario,
con foco en consistencia bajo concurrencia, idempotencia real e integración
resiliente con un proveedor de autorización externo simulado.

Este proyecto **no busca ser un producto de negocio**: es una pieza de
portafolio orientada a demostrar ingeniería de nivel producción en el dominio
más intolerante a errores — dinero.

## Estado del proyecto

**Fase 1 completada** — esqueleto ejecutable funcionando (Milestone H1, ver
`IMPLEMENTATION_PLAN.md`, Sección 18): la aplicación arranca, conecta a
PostgreSQL y Redis, y `/actuator/health` responde `200` con `{"status":"UP"}`.

Sin funcionalidad de negocio todavía — ni entidades, ni endpoints propios, ni
migraciones reales. Eso arranca en la **Fase 2** (modelo de dominio puro +
regla arquitectónica automática con ArchUnit), que es donde continúa el
proyecto ahora.

## Requisitos previos

- Java 21 (LTS)
- Docker + Docker Compose (plugin `docker compose`, no el binario standalone `docker-compose` v1)

No hace falta instalar Maven: el proyecto incluye su propio wrapper (`./mvnw`).

## Cómo ejecutar

1. Clonar el repositorio y copiar la plantilla de configuración:

```bash
   cp .env.example .env
```

   Completar `.env` con valores de desarrollo reales (en particular
   `POSTGRES_PASSWORD`, que no tiene default por diseño — `REQ-SEC-002`).
   `.env` nunca se versiona.

2. Levantar PostgreSQL 16 y Redis 7 en contenedores:

```bash
   docker compose -f docker-compose.dev.yml up -d
```

3. Exportar las variables de `.env` al shell (Spring Boot no lee `.env`
   automáticamente — a diferencia de Docker Compose, que sí lo hace de forma
   nativa):

```bash
   set -a; source .env; set +a
```

4. Arrancar la aplicación:

```bash
   ./mvnw spring-boot:run
```

5. Verificar que todo está en pie:

```bash
   curl -i http://localhost:8080/actuator/health
   # esperado: HTTP/1.1 200, {"status":"UP", ...}
```

El entorno completo (aplicación + Postgres + Redis + `authorization-provider`
simulado, todo con un único `docker compose up`, sin pasos manuales) llega en
la Fase 15.

## Documentación

- [Context Maestro](docs/payment-gateway-engine-context-maestro.md) — descubrimiento, decisiones de producto
- [SRS](docs/srs-payment-gateway-engine1.md) — requisitos verificables
- [SDD](docs/msdd-payment-gateway-engine.md) — diseño técnico
- ADR — decisiones de arquitectura, una por archivo en [`docs/adr/`](docs/adr/):
  - [0001 — Mapeo domain↔JPA](docs/adr/0001-mapeo-domain-jpa.md)
  - [0002 — Cliente HTTP y resiliencia hacia authorization-provider](docs/adr/0002-cliente-resiliencia-authorization-provider.md)
  - [0003 — Idempotencia dual Redis + PostgreSQL](docs/adr/0003-idempotencia-redis-postgresql.md)
  - [0004 — Locking pesimista y orden anti-deadlock](docs/adr/0004-locking-pesimista-orden-antideadlock.md)
  - [0005 — TraceId y logging estructurado](docs/adr/0005-traceid-logging-estructurado.md)
  - [0006 — Topología Docker Compose](docs/adr/0006-topologia-docker-compose.md)
  - [0007 — Autorización por ownership sin framework](docs/adr/0007-autorizacion-ownership-sin-framework.md)

## Licencia

[MIT](LICENSE)
