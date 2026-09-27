# ADR-0006: Docker Compose como entorno de ejecución completo de un solo nodo

## Status
Accepted

## Context
CS-04 exige que el sistema completo, incluido el proveedor externo
simulado, se levante con un solo comando y sin configuración manual. El
proyecto es de portafolio: debe poder ejecutarse en cualquier máquina con
Docker, sin cuentas ni servicios propietarios de un cloud.

## Decision
- `docker-compose.yml` con 4 servicios: `application` (perfil docker),
  `postgres:16`, `redis:7` y `authorization-provider` (WireMock 3.5.4).
  Las versiones de imagen coinciden con las de Testcontainers: lo que se
  prueba es lo que se ejecuta.
- `depends_on: condition: service_healthy` sobre las 3 dependencias
  (`pg_isready`, `redis-cli ping`, `GET /__admin/health`): arranque
  determinista, sin crash-loops de la aplicación.
- WireMock monta el mismo directorio `wiremock/` que usan los tests
  (RISK-007): un solo conjunto de stubs para todos los entornos.
- Configuración: `.env` (no versionado) es la única fuente de verdad de
  credenciales, con `.env.example` como plantilla funcional. Compose exige
  las credenciales con `${VAR:?}` y se niega a arrancar si faltan. La
  topología (hostnames de servicio) se fija en el bloque `environment:`
  del propio Compose.
- Solo se publican al host la aplicación (8080) y WireMock (8089, para la
  demo). Postgres y Redis quedan en la red interna.
- Imagen de la aplicación: Dockerfile multi-stage (JDK para compilar,
  solo JRE en runtime), usuario sin privilegios, tests fuera del build
  (son responsabilidad de CI).
- Seed de datos demo solo en el perfil `docker` (ADR-0010).
- Sin referencias a servicios propietarios de un cloud (RDS, ElastiCache
  o equivalentes).

## Consequences
- CS-04 verificado desde un `git clone` limpio usando `.env.example` tal
  cual: el repositorio es autosuficiente.
- Las credenciales de Postgres solo se aplican al inicializar un volumen
  vacío: cambiar `.env` exige `docker compose down -v`.
- Un solo nodo, sin orquestador: no hay escalado horizontal ni separación
  liveness/readiness (ver ADR-0005). La ausencia de estado en el proceso
  deja la puerta abierta a escalar en el futuro, pero no es un requisito.