ADR 001
status: "accepted"
date: 2026-09-08
decision-makers: "Principal Developer"
---

# Mapeo manual entre el dominio puro y las entidades JPA

## Context and Problem Statement

`CON-002` exige que el paquete `domain/` no dependa de ningún framework (Spring, JPA), verificado automáticamente con ArchUnit (`DEC-018`, `REQ-MAINT-001`). Sin embargo, la persistencia real de `Account`, `Transaction` y `TransactionLog` requiere JPA/Hibernate sobre PostgreSQL. Es necesario decidir cómo se traduce entre el modelo de dominio puro y el modelo persistente sin comprometer la pureza arquitectónica exigida por `CS-03`. El alcance de esta decisión es la frontera entre `domain/` y `infrastructure/` (adapters de persistencia).

## Decision Drivers

* `CON-002` — `domain/` sin dependencias de framework (constraint confirmado, no negociable).
* `REQ-MAINT-001` / `DEC-018` — verificación automática de esta regla vía ArchUnit.
* `CS-03` — criterio de éxito explícito sobre pureza del dominio.
* Tamaño reducido del modelo (solo 3 agregados/entidades) — no justifica tooling adicional de mapeo.

## Considered Options

* Modelos JPA separados del dominio, con mapeo manual en los adapters de infraestructura.
* Anotar directamente las clases de dominio con `@Entity`/`@Id` (modelo compartido).
* Modelos JPA separados con mapeo generado por MapStruct.

## Decision Outcome

Chosen option: "Modelos JPA separados del dominio, con mapeo manual en los adapters de infraestructura", porque es la única opción que satisface `CON-002` sin excepción y, dado el tamaño reducido del modelo (3 entidades), no justifica el coste de una dependencia de generación de código adicional (MapStruct) solo para evitar escribir mapeos explícitos.

### Consequences

* Good, porque el dominio permanece verificable como libre de dependencias de framework mediante ArchUnit (`CS-03`), sin excepciones ni justificaciones caso por caso.
* Good, porque el código de mapeo es explícito y visible para cualquier revisor — no hay generación de código que inspeccionar.
* Bad, porque introduce boilerplate manual (mapeo campo a campo) que crecerá linealmente si el modelo se amplía en el futuro.
* Neutral, porque el volumen actual de entidades (3) hace que este boilerplate sea manejable sin herramientas adicionales.

### Confirmation

Un test ArchUnit, ejecutado como parte del test suite (`DEC-018`), falla el build si se introduce cualquier import de `org.springframework.*` o `jakarta.persistence.*` dentro del paquete `domain/`. Adicionalmente, revisión de código verifica que los adapters de infraestructura —no el dominio— son los únicos responsables del mapeo bidireccional.

## Pros and Cons of the Options

### Modelos JPA separados, mapeo manual

* Good, porque preserva `CON-002` sin ambigüedad.
* Good, porque no añade dependencias de build (procesadores de anotaciones) para un modelo pequeño.
* Neutral, porque el mapeo manual es más verboso que una alternativa generada, pero perfectamente legible.
* Bad, porque cualquier cambio de campo requiere tocar el mapeo en dos lugares (entidad JPA y objeto de dominio).

### Anotar directamente las clases de dominio (`@Entity` compartido)

* Good, porque elimina el código de mapeo por completo.
* Bad, porque viola `CON-002` de forma directa e inmediata — no es una alternativa arquitectónicamente viable para este proyecto.
* Bad, porque acopla el modelo de dominio al ciclo de vida de Hibernate (proxies, lazy loading), contaminando la lógica de negocio con preocupaciones de persistencia.

### Modelos JPA separados con MapStruct

* Good, porque reduce el boilerplate de mapeo frente a la opción manual.
* Neutral, porque el ahorro es marginal dado que solo existen 3 entidades.
* Bad, porque añade una dependencia y un procesador de anotaciones en el build por un beneficio pequeño en este alcance — complejidad no justificada por el tamaño del modelo.

## More Information

Relacionado con: `CON-002`, `DEC-018`, `REQ-MAINT-001`, `CS-03` (Context Maestro / SRS). Afecta directamente el Design View de tipo *Composition*/*Logical* del MSDD (estructura de paquetes `domain/application/infrastructure`, `DG-001`). Puede revisarse si el número de agregados crece significativamente en una versión futura del sistema. Identificador de discovery: `MADR-001`.
