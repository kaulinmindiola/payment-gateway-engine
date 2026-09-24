# ADR-0002: Resilience4j vía anotaciones sobre AuthorizationHttpAdapter

## Status
Accepted

## Context
Las transferencias EXTERNAL dependen de un proveedor de autorización
remoto que puede fallar técnicamente (timeout, 5xx). Sin mecanismos de
resiliencia, cada fallo transitorio se convertiría en un error para el
cliente, y un proveedor caído seguiría recibiendo tráfico, agotando
hilos y conexiones. Al mismo tiempo, un reintento mal acotado podría
duplicar una autorización (`RISK-002`, `CS-02`).

## Decision
- `@CircuitBreaker` y `@Retry` (instancia `authorizationProvider`) se
  aplican como anotaciones sobre `AuthorizationHttpAdapter.authorize()`.
  No se usa composición funcional manual (`Decorators.ofSupplier`).
  `domain/` y `application/` no conocen Resilience4j.
- Valores (Sección 11 del contexto): circuit breaker COUNT_BASED,
  `slidingWindowSize=10`, `minimumNumberOfCalls=10` (explícito: el default
  de la librería es 100), `failureRateThreshold=50`,
  `waitDurationInOpenState=10s`, `permittedNumberOfCallsInHalfOpenState=3`.
  Retry: `maxAttempts=3`, `waitDuration=200ms`, backoff exponencial x2.
- Timeouts del cliente HTTP (independientes de Resilience4j): connect 1s,
  read 2s.
- Solo se reintentan fallos TÉCNICOS: `retryExceptions` lista únicamente
  `AuthorizationTechnicalException` (padre de timeout y 5xx).
  APPROVED/DECLINED son valores de retorno, nunca excepciones, así que un
  DECLINED jamás se reintenta (`BR-010`).
- Circuito abierto: `CallNotPermittedException` se lanza desde el
  aspecto, fuera del método. Un `fallbackMethod` tipado solo para esa
  excepción la traduce a `AuthorizationUnavailableException`, de modo que
  `TransferMoney` la trate como cualquier fallo técnico (liberar la
  idempotency key y responder 503).
- Orden de aspectos: se mantiene el default de Resilience4j (Retry por
  fuera de CircuitBreaker). El circuito contabiliza cada intento
  individual. Los tests verifican el comportamiento observable (el
  circuito se abre y el proveedor deja de recibir llamadas), no el número
  exacto de transferencias necesario para abrirlo.
- Un 4xx inesperado del proveedor no se maneja explícitamente (extensión
  futura, Sección 22 del plan).
- `X-Trace-Id` se genera como UUID por llamada: placeholder hasta Fase 10
  (TraceIdFilter + MDC).

## Consequences
- `CS-02` verificado por `ExternalTransferResilienceIT` contra stubs
  WireMock reales: DECLINED produce 1 llamada; 5xx y timeout persistentes
  agotan 3 intentos sin persistir nada; tras un fallo técnico, un
  reintento del cliente con la misma key se ejecuta exactamente una vez;
  con el circuito abierto, el proveedor no recibe nuevas llamadas.
- Las anotaciones requieren AOP activo (`spring-boot-starter-aop`). Sin
  él compilan y no hacen nada. Por eso los tests afirman el número real
  de llamadas recibidas por WireMock y no solo el resultado final.
- Coste: los tests de resiliencia esperan timeouts reales (~2s por
  intento) y alargan la suite de integración.