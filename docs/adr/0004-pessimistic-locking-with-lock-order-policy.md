# ADR-0004: Pessimistic locking con LockOrderPolicy aislado

## Status
Accepted

## Context
Las transferencias INTERNAL (Fase 5) requieren modificar dos cuentas
(débito + crédito) de forma atómica bajo concurrencia alta (`CS-01`).
Dos estrategias son posibles: optimistic locking (vía `Account.version`,
ya reservado en el modelo desde Fase 2) o pessimistic locking (bloqueo
explícito de fila antes de leer). Con optimistic locking, bajo alta
contención sobre el mismo par de cuentas, la mayoría de las transacciones
concurrentes fallarían con `OptimisticLockException` y requerirían
reintento a nivel de aplicación -- una complejidad adicional no justificada
para el volumen de contención que el propio `CS-01` exige probar (20/50
hilos sobre el mismo par).

Adicionalmente, sin un orden determinista de adquisición de locks, dos
transferencias en direcciones opuestas sobre el mismo par de cuentas
(A->B y B->A simultáneas) producirían un deadlock clásico: cada
transacción bloquea una cuenta y espera indefinidamente la otra
(`RISK-001`).

## Decision
- Estrategia única: pessimistic locking vía JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)`
  (`AccountJpaRepository.findByIdForUpdate`, Fase 3), dentro de un único
  método `@Transactional` (`InternalTransferHandler.handle`, Fase 5).
- `LockOrderPolicy` (`domain/policy/`) resuelve el orden de adquisición
  ascendente por `UUID.compareTo()`, invocado UNA vez por transferencia,
  antes de cualquier `findByIdForUpdate()`. Es una clase de dominio pura,
  sin anotaciones de framework -- Spring la registra como bean
  explícitamente vía `infrastructure/config/DomainPolicyConfig`, sin que
  `domain/` conozca la existencia de Spring.
- `Account.version` (optimistic locking) permanece reservado, sin uso
  (Fase 2, Principio 6 del plan) -- no se combina con pessimistic locking
  en este alcance.
- No se usa SQL nativo (`nativeQuery`) para el `FOR UPDATE` -- se delega
  en la anotación `@Lock` de Spring Data JPA, dejando a Hibernate la
  responsabilidad de traducir correctamente contra el dialecto configurado.

## Consequences
- **Nota de verificación empírica** (Fase 3, Paso 4): sobre PostgreSQL 16
  con Hibernate 7, `PESSIMISTIC_WRITE` se traduce a `FOR NO KEY UPDATE`,
  no a `FOR UPDATE` literal -- una optimización nativa de Postgres para
  reducir contención en cadenas de FK. Se verificó explícitamente
  (capturando el SQL real emitido, no asumiendo el texto del contexto)
  que esta variante preserva la exclusión mutua requerida: ninguna otra
  ruta del sistema solicita `FOR KEY SHARE`/`FOR SHARE` sobre `accounts`,
  así que dos `FOR NO KEY UPDATE` concurrentes sobre la misma fila siguen
  bloqueándose mutuamente, igual que `FOR UPDATE` lo haría.
- `CS-01` verificado con test de concurrencia (20/50 hilos, mismo par de
  cuentas) reproducible en 10 ejecuciones consecutivas.
- `RISK-001` verificado con test de concurrencia cruzada (A->B/B->A
  simultáneas, 40 hilos) -- sin deadlock, balance neto exacto.
- Costo: cada transferencia INTERNAL retiene un lock de fila durante toda
  la duración de la transacción -- aceptable para el volumen que el
  proyecto declara (Sección 1 del contexto: portfolio, sin SLA productivo
  real, `CON-007`).