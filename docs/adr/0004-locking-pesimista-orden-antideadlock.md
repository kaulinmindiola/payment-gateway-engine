ADR 004
status: "accepted"
date: 2026-09-08
decision-makers: "Principal Developer"
---

# Formalización del locking pesimista con orden anti-deadlock

## Context and Problem Statement

`DEC-001` (`ACCEPTED`) ya fija que los locks se adquieren siempre en orden ascendente de `account.id`, independientemente de cuál cuenta es origen y cuál destino, para prevenir deadlocks en transferencias cruzadas concurrentes (A→B / B→A). Falta formalizar cómo se implementa esto a nivel de código y de límites transaccionales, de forma que sea testeable de forma aislada y verificable bajo concurrencia real (`REQ-FUNC-014`, `REQ-FUNC-015`).

## Decision Drivers

* `AC-001` — consistencia fuerte bajo concurrencia (sin saldo negativo, sin duplicados).
* `CON-003` — Pessimistic Locking como estrategia principal de concurrencia (constraint confirmado).
* `DEC-001` — orden ascendente de locks (decisión ya `ACCEPTED`, este ADR la implementa).

## Considered Options

* Spring Data JPA con `@Lock(LockModeType.PESSIMISTIC_WRITE)`; el caso de uso `TransferMoney` ordena `[sourceId, targetId]` mediante un objeto de política aislado (`LockOrderPolicy`) antes de dos llamadas secuenciales "find for update" dentro de un único método `@Transactional`.
* SQL nativo `SELECT ... FOR UPDATE` vía `JdbcTemplate`, evitando JPA para esta operación específica.
* Optimistic locking con reintentos en lugar de pessimistic locking.

## Decision Outcome

Chosen option: "Spring Data JPA con `@Lock(PESSIMISTIC_WRITE)` + `LockOrderPolicy` aislado", porque mantiene un único mecanismo de acceso a datos (JPA) en toda la capa de persistencia y permite testear la lógica de ordenamiento de locks de forma unitaria, sin necesitar una base de datos real para verificar esa parte del comportamiento.

### Consequences

* Good, porque evita mezclar dos tecnologías de acceso a datos (JPA + JDBC nativo) en el mismo módulo, reduciendo complejidad accidental.
* Good, porque `LockOrderPolicy` como componente aislado es testeable unitariamente y documenta explícitamente la regla anti-deadlock como una pieza de lógica nombrada, no dispersa en el caso de uso.
* Bad, porque depende de que Hibernate emita efectivamente `SELECT ... FOR UPDATE` al usar `@Lock(PESSIMISTIC_WRITE)` sobre el dialecto de PostgreSQL configurado — debe verificarse explícitamente, no asumirse.
* Neutral, porque el `version` reservado en `Account` (optimistic locking secundario, ver Sección 13 del Context Maestro) no se usa en esta decisión; queda disponible para un mecanismo futuro no cubierto por este alcance.

### Confirmation

Test de concurrencia dedicado con transferencias cruzadas A→B y B→A simultáneas (`REQ-FUNC-015`) que verifica ausencia de deadlock y resultado consistente. Test de concurrencia de 20 y 50 hilos sobre el mismo par de cuentas (`REQ-PERF-001`, `CS-01`) que verifica saldo final exacto, sin saldo negativo y sin transacciones duplicadas, de forma reproducible.

## Pros and Cons of the Options

### JPA `@Lock(PESSIMISTIC_WRITE)` + `LockOrderPolicy`

* Good, porque unifica el acceso a datos bajo JPA en todo el sistema.
* Good, porque aísla y nombra explícitamente la política anti-deadlock, facilitando su revisión y testeo.
* Neutral, porque requiere una verificación explícita del SQL generado por Hibernate (vía logging de SQL en tests) para confirmar el comportamiento esperado.

### SQL nativo `FOR UPDATE` vía JdbcTemplate

* Good, porque da control total y explícito sobre el SQL ejecutado, sin depender del comportamiento de Hibernate.
* Bad, porque introduce una segunda vía de acceso a datos junto a JPA, rompiendo la consistencia arquitectónica del resto de la capa de persistencia sin un beneficio claro que lo justifique.

### Optimistic locking con reintentos

* Good, porque evita mantener locks de fila durante toda la transacción.
* Bad, porque no es una alternativa real: `CON-003` ya fija pessimistic locking como estrategia principal — reabrir esta decisión está fuera del alcance de este ADR.

## More Information

Relacionado con `DEC-001`, `CON-003`, `RISK-001`, Sección 13 del Context Maestro ("Concurrencia y locking"). Identificador de discovery: `MADR-004`.
