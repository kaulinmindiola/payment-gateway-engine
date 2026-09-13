ADR 006
status: "accepted"
date: 2026-09-08
decision-makers: "Principal Developer"
---

# Topología Docker Compose con healthchecks y configuración externalizada

## Context and Problem Statement

`DEC-005` (`ACCEPTED`) ya fija una topología de 4 servicios (aplicación, PostgreSQL, Redis, `authorization-provider` simulado). `REQ-INST-001` exige que el entorno completo quede operativo con un único comando `docker compose up`, sin pasos manuales adicionales (`CS-04`). Es necesario definir el orden de arranque, los healthchecks y el mecanismo de configuración externalizada (`REQ-INST-002`, `DEC-017`).

## Decision Drivers

* `AC-008` — reproducibilidad del entorno completo para evaluación de portafolio.
* `DEC-005` — topología de 4 servicios ya aceptada, este ADR la implementa.
* `DEC-017` — secretos vía variables de entorno, nunca en el repositorio.

## Considered Options

* `depends_on` con `condition: service_healthy` para PostgreSQL, Redis y WireMock; la aplicación espera a que los tres estén saludables antes de arrancar; configuración vía `.env` (no versionado) más un bloque `environment` referenciando `${VAR}`; perfil `application-docker.yml` para overrides específicos de contenedor.
* Sin healthchecks, confiando en la lógica de reintento de conexión de Spring Boot al arrancar.

## Decision Outcome

Chosen option: "`depends_on` con `service_healthy` + `.env` + perfil `docker`", porque produce un arranque determinístico: la aplicación nunca intenta conectarse a una dependencia que todavía no está lista, eliminando una fuente común de flakiness (crash-loops) en la demostración del proyecto (`CS-04`).

### Consequences

* Good, porque el orden de arranque es explícito y verificable, en vez de depender implícitamente de la velocidad relativa de arranque de cada contenedor.
* Good, porque separa configuración de código fuente sin excepciones (`DEC-017`, `REQ-SEC-002`).
* Bad, porque añade verbosidad al `docker-compose.yml` (bloques `healthcheck` por servicio).
* Neutral, porque el healthcheck de WireMock depende de que exponga un endpoint administrativo alcanzable al arrancar (p. ej. `/__admin/mappings`), lo cual es una capacidad estándar de la herramienta.

### Confirmation

Demostración: ejecutar `docker compose up` desde un estado limpio deja los 4 servicios en estado saludable sin intervención manual (criterio de aceptación de `REQ-INST-001`). Inspección del repositorio confirma que ningún valor de configuración de entorno está hardcodeado en el código fuente (`REQ-INST-002`).

## Pros and Cons of the Options

### `depends_on` + `service_healthy` + `.env`

* Good, porque es la forma estándar y soportada nativamente por Docker Compose de expresar dependencias de arranque saludables.
* Good, porque los healthchecks son baratos de escribir para las tecnologías involucradas (todas exponen algún mecanismo de verificación de salud).
* Neutral, porque requiere mantener los healthchecks sincronizados si cambian las versiones de las imágenes base.

### Sin healthchecks (retry de Spring Boot)

* Good, porque simplifica el `docker-compose.yml`.
* Bad, porque es menos determinístico: existe riesgo real de que la aplicación falle su primer intento de conexión y entre en un ciclo de reinicio antes de que las dependencias estén listas, contradiciendo el objetivo de arranque reproducible de `CS-04`.

## More Information

Relacionado con `DEC-005`, `DEC-017`, `REQ-SEC-002`, `CS-04`, Sección 19 del Context Maestro ("Despliegue — topología Docker Compose"). Identificador de discovery: `MADR-006`.
