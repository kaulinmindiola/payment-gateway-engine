ADR 003
status: "accepted"
date: 2026-09-08
decision-makers: "Principal Developer"
---

# Mecanismo dual de idempotencia: Redis con degradación a constraint de PostgreSQL

## Context and Problem Statement

`DEC-002` (constraint `UNIQUE` sobre `idempotency_key`) y `DEC-003` (`SET NX` en Redis con TTL 24h) ya están aceptados como estrategia general de idempotencia. Falta definir el mecanismo concreto de implementación: cómo se realiza el check-and-set atómico en Redis, cómo se clasifica una solicitud (nueva / en curso / terminal), y —crucialmente— cómo decide el sistema seguir operando cuando Redis no responde, apoyándose en el constraint de PostgreSQL como red de seguridad (`REQ-REL-004`, `RISK-003`).

## Decision Drivers

* `AC-002` — idempotencia real con degradación controlada.
* `DEC-002`, `DEC-003` — decisiones ya aceptadas que este ADR debe implementar, no reabrir.
* `REQ-REL-004` — el sistema debe seguir aceptando transferencias con Redis caído.
* `RISK-003` — Redis caído no debe producir doble ejecución.

## Considered Options

* Intercepción a nivel de caso de uso: `SET NX` en Redis antes de ejecutar `TransferMoney`; si Redis falla la conexión, el flujo continúa sin caché y se apoya en el `UNIQUE` constraint de PostgreSQL, interpretando una violación de integridad al persistir como duplicado.
* Prescindir de Redis y usar solo PostgreSQL.
* Locks distribuidos con Redisson.

## Decision Outcome

Chosen option: "Intercepción a nivel de caso de uso con fallback por excepción de integridad", porque es la única opción que respeta `DEC-002`/`DEC-003` (ya `ACCEPTED` aguas arriba) e implementa exactamente el comportamiento de degradación exigido por `REQ-REL-004`: la ausencia de Redis degrada la experiencia (se pierde la respuesta cacheada instantánea) pero nunca compromete la corrección (el constraint de base de datos sigue previniendo duplicados reales).

### Consequences

* Good, porque satisface directamente `REQ-REL-004` sin introducir un mecanismo nuevo no contemplado en el Context Maestro.
* Good, porque no añade dependencias nuevas al stack ya fijado (`CON-001`).
* Bad, porque introduce dos caminos de código a testear exhaustivamente (Redis disponible / Redis caído), aumentando la superficie de test de integración.
* Neutral, porque depende de capturar correctamente la excepción de integridad específica del driver JDBC/Hibernate (p. ej. `DataIntegrityViolationException`) sin enmascarar otros errores de integridad no relacionados con idempotencia.

### Confirmation

Test de integración con Testcontainers que detiene el contenedor de Redis a mitad de una secuencia de transferencias y verifica que una misma `idempotency_key` nunca produce dos filas en `transactions`, aunque se pierda la respuesta cacheada instantánea (criterio de aceptación de `REQ-REL-004`).

## Pros and Cons of the Options

### Intercepción a nivel de caso de uso + fallback por excepción de integridad

* Good, porque cumple `REQ-REL-004` de forma directa y verificable.
* Good, porque reutiliza mecanismos ya decididos (`DEC-002`, `DEC-003`) sin introducir nuevos componentes.
* Bad, porque el manejo de la excepción de integridad debe ser preciso para no confundir un duplicado legítimo con otro tipo de fallo de base de datos.

### Solo PostgreSQL (sin Redis)

* Good, porque simplifica el diseño al eliminar una dependencia.
* Bad, porque no es una alternativa real: `DEC-002`/`DEC-003` ya están `ACCEPTED` en el Context Maestro y el SRS; reabrir esta decisión estaría fuera del alcance de este ADR.

### Redisson (locks distribuidos)

* Good, porque ofrece primitivas de locking distribuido más ricas que `SET NX`.
* Bad, porque `SET NX` nativo ya provee la atomicidad necesaria para este caso de uso — Redisson añade una dependencia no justificada por el problema real (`CON-001` fija el stack tecnológico).

## More Information

Relacionado con la Sección 12 del Context Maestro ("Idempotencia — diseño Redis"), `DEC-002`, `DEC-003`, `RISK-003`, `REQ-FUNC-007` a `REQ-FUNC-009`. Identificador de discovery: `MADR-003`.
