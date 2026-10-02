# ADR-0006: Docker Compose as a Complete Single-Node Runtime Environment

## Status
Accepted

## Context
CS-04 demands that the complete system, including the simulated external provider, is spun up with a single command and without manual configuration. This is a portfolio project: it must be runnable on any machine with Docker, without proprietary cloud accounts or services.

## Decision
- `docker-compose.yml` with 4 services: `application` (docker profile), `postgres:16`, `redis:7`, and `authorization-provider` (WireMock 3.5.4). Image versions match those in Testcontainers: what is tested is what is run.
- `depends_on: condition: service_healthy` on all 3 dependencies (`pg_isready`, `redis-cli ping`, `GET /__admin/health`): deterministic startup, no application crash-loops.
- WireMock mounts the exact same `wiremock/` directory used by tests (RISK-007): a single set of stubs for all environments.
- Configuration: `.env` (unversioned) is the single source of truth for credentials, with `.env.example` acting as a functional template. Compose demands credentials with `${VAR:?}` and refuses to start if missing. Topology (service hostnames) is fixed in the `environment:` block of Compose itself.
- Only the application (8080) and WireMock (8089, for demos) are exposed to the host. Postgres and Redis remain on the internal network.
- Application image: multi-stage Dockerfile (JDK to build, JRE only at runtime), unprivileged user, tests excluded from build (CI responsibility).
- Demo data seeding occurs only in the `docker` profile (ADR-0010).
- No references to proprietary cloud services (RDS, ElastiCache, or equivalents).

## Consequences
- CS-04 verified from a clean `git clone` using `.env.example` as is: the repository is self-sufficient.
- Postgres credentials are only applied when initializing an empty volume: changing `.env` requires `docker compose down -v`.
- Single node, no orchestrator: no horizontal scaling or liveness/readiness separation (see ADR-0005). The stateless nature of the process leaves the door open to scale in the future, but it is not a requirement.