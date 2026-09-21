# ADR-0003: Idempotencia dual (Redis + Postgres UNIQUE)

## Status
Accepted

## Context
`BR-002` exige que una segunda petición con la misma `X-Idempotency-Key`
nunca re-ejecute una transferencia ya procesada, incluso bajo timeout,
retry del cliente, o caída parcial de infraestructura (`RISK-002`,
`RISK-003`). Depender de un único mecanismo tiene un punto de fallo real:
si solo existiera Redis, su caída eliminaría la garantía de idempotencia
por completo, no solo su velocidad.

## Decision
- Intercepción a nivel de caso de uso: `TransferMoney.execute()` reclama
  la key vía `IdempotencyPort.tryBegin()` (Redis `SET NX`, TTL 24h)
  ANTES de invocar el handler correspondiente (`InternalTransferHandler`
  hoy; `ExternalTransferHandler` en Fase 8, mismo punto de entrada único).
- Tres resultados posibles de `tryBegin()`: `ACQUIRED` (ejecutar),
  `IN_PROGRESS` (409, vía `IdempotencyConflictException`), o terminal
  `COMPLETED`/`FAILED` (devolver la respuesta cacheada exacta sin
  re-ejecutar -- `TransferOutcome.Replayed`).
- Backstop en Postgres: `transactions.idempotency_key UNIQUE` (Fase 3).
  Si Redis no responde (excepción capturada en `RedisIdempotencyAdapter`,
  logueada como `WARN`, nunca propagada), `tryBegin()` devuelve
  `acquired()` de fallback -- el flujo se ejecuta igual, confiando en que
  Postgres rechazará un duplicado real con `DataIntegrityViolationException`.
  `TransferMoney` captura esa excepción y recupera la `Transaction`
  original vía `TransactionRepositoryPort.findByIdempotencyKey()`.
- La respuesta cacheada en Redis se serializa como JSON plano (formato
  interno delimitado por `|` para el sobre húngaro: `status|httpStatus|body`)
  usando tipos propios de `application/usecase/` (`CachedTransferPayload`,
  `CachedProblemPayload`) que duplican deliberadamente la forma de los DTOs
  de `infrastructure/web/` -- `application/` no puede depender de
  `infrastructure/web/` (regla de capas de ArchUnit), así que la
  duplicación es estructural, no un descuido.
- Solo excepciones de negocio (`DomainException`) se cachean como
  resultado `FAILED`. Excepciones de programación/scaffolding temporal
  (`IllegalArgumentException`, `UnsupportedTransferTypeException`) no se
  cachean -- una key reclamada en ese camino queda `IN_PROGRESS` hasta
  expirar por TTL, aceptado como limitación menor y acotada (ver Fase 6,
  discusión de gap en Paso 2).

## Consequences
- `RISK-003` verificado con test dedicado (`RedisOutageIdempotencyIT`):
  Redis completamente detenido a mitad de secuencia, dos intentos con la
  misma key, exactamente una fila en `transactions`, balance reflejando
  un solo débito.
- La corrección del sistema NUNCA depende de que Redis esté disponible --
  solo su velocidad de respuesta ante un duplicado (con Redis caído, un
  duplicado real cuesta un ciclo completo de ejecución+rollback en vez de
  un rechazo instantáneo en memoria, pero el resultado final es idéntico).
- Costo: dos mecanismos que mantener sincronizados conceptualmente (mismo
  criterio de qué es un "duplicado"), y una duplicación de forma entre
  `application/usecase/Cached*Payload` e `infrastructure/web/*Response` que
  debe actualizarse en ambos lugares si el contrato de transferencia cambia.