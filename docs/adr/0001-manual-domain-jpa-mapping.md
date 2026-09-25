# ADR-0001: Mapeo manual dominio↔JPA en adapters

## Status
Accepted

## Context
El dominio (`domain/model/`, Fase 2) debe permanecer libre de anotaciones de
framework (`CS-03`, verificado por ArchUnit). Sin embargo, la persistencia
real (Fase 3) requiere entidades JPA (`@Entity`) con las anotaciones propias
de Hibernate. Dos enfoques son posibles: (a) anotar directamente las clases
de dominio como entidades JPA, o (b) mantener dos modelos separados
(dominio puro + entidades JPA) con un mapeo explícito entre ambos.

La opción (a) violaría `CS-03` directamente. Una alternativa intermedia
sería usar una librería de mapeo automático (MapStruct) entre ambos modelos.

## Decision
- Se mantienen dos modelos completamente separados:
  `domain/model/*.java` (puro, sin framework) e
  `infrastructure/adapter/persistence/entity/*Entity.java` (anotado con JPA).
- El mapeo entre ambos es **manual y explícito**, implementado como métodos
  privados `toDomain()`/`toEntity()` dentro de cada adapter
  (`JpaAccountRepositoryAdapter`, `JpaTransactionRepositoryAdapter`).
- No se usa MapStruct ni ninguna otra librería de mapeo automático.
- Las entidades JPA reutilizan los enums del dominio directamente
  (`AccountStatus`, `TransactionStatus`, etc.) vía `@Enumerated(EnumType.STRING)`
  — esto no viola la regla de dependencia: `infrastructure → domain` es la
  dirección permitida por ArchUnit; lo prohibido es la dirección inversa.
- Los campos de auditoría (`createdAt`/`updatedAt`) existen solo en las
  entidades JPA (`@CreationTimestamp`/`@UpdateTimestamp`), nunca en el
  dominio — el dominio no necesita conocer cuándo se persistió una fila
  para decidir sobre sus propias invariantes de negocio.
- Para evitar que un `save()` ingenuo sobrescriba `createdAt` con `null` en
  una actualización (ID client-assigned vía `UUID.randomUUID()`, nunca
  autogenerado por la BD), cada adapter distingue explícitamente creación
  de actualización: `findById()` localiza la entidad ya gestionada por
  Hibernate y aplica cambios mutables vía un método `applyChangesFrom(...)`
  (dirty checking), en vez de reconstruir y persistir una entidad nueva.

## Consequences
- El dominio permanece 100% libre de anotaciones de framework, confirmado
  automáticamente por `ArchitectureTest` en cada build.
- El mapeo es explícito y fácil de razonar/debuggear — no hay "magia" de
  reflection ni generación de código en tiempo de compilación.
- Costo: cada campo nuevo en una clase de dominio requiere actualizar
  manualmente `toDomain()`/`toEntity()` en el adapter correspondiente — esto
  es intencional, no un descuido: hace visible en code review cualquier
  drift entre el modelo de dominio y el esquema de persistencia (`RISK-009`).
- El patrón create/update explícito (en vez de `Persistable<UUID>`) añade
  un `SELECT` extra en el camino de creación, aceptado porque esa ruta no
  es la que mide `CS-01` (transferencias entre cuentas ya existentes).

  ## Update (Fase 9): createdAt de Transaction como dato de negocio
Se reclasifica `Transaction.createdAt`: el momento en que ocurre un pago
es información de negocio (se muestra, se filtra y ordena el historial,
`BR-015`), no un metadato de persistencia. Lo genera el dominio en
`createInternal`/`createExternal`, con el mismo precedente que
`TransactionLog.createdAt` (Fase 2), truncado a microsegundos para
coincidir con la precisión de `TIMESTAMPTZ` y garantizar el mismo valor en
la respuesta del POST, en el replay idempotente y en cualquier GET.
`TransactionEntity` deja de usar `@CreationTimestamp` para esa columna.
El resto de la decisión original no cambia: `updatedAt` (todas las
entidades) y `createdAt` de `Account`/`User` siguen siendo metadatos
gestionados por Hibernate, fuera del dominio.