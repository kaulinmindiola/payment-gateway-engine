ADR 002
status: "accepted"
date: 2026-09-08
decision-makers: "Principal Developer"
---

# Cliente HTTP y estrategia de resiliencia hacia authorization-provider

## Context and Problem Statement

`REQ-INT-002` requiere invocar al `authorization-provider` externo (WireMock) protegido por Circuit Breaker y Retry (`DEC-012`, `DEC-013`). `BR-010`/`REQ-FUNC-017` exige que un `DECLINED` del proveedor sea tratado como un resultado de negocio válido y **nunca** dispare un reintento (`RISK-002`, `RISK-010`). Es necesario decidir la tecnología de cliente HTTP y el patrón de integración que garantice esta separación de forma estructural, no solo por convención.

## Decision Drivers

* `AC-003` — resiliencia ante una dependencia externa no confiable.
* `DEC-009` — el dominio solo conoce `AuthorizationPort`, nunca detalles HTTP.
* `DEC-012`, `DEC-013` — configuración concreta de Circuit Breaker, Retry y timeouts ya aceptada.
* `BR-010` — `DECLINED` es un resultado de negocio, no un error técnico.
* `RISK-002`, `RISK-010` — duplicación por retry indebido / mezcla de canales técnico y de negocio.
* Supuesto adoptado (`ASSUMED`, sujeto a confirmación): Spring Boot 3.2+, lo que habilita `RestClient` nativo síncrono.

## Considered Options

* `RestClient` síncrono (Spring Boot 3.2+) + anotaciones `@CircuitBreaker`/`@Retry` de Resilience4j, con jerarquía de excepciones que separa fallos técnicos de resultados de negocio.
* `WebClient` reactivo con el mismo patrón de resiliencia.
* `RestTemplate` (API en modo mantenimiento).

## Decision Outcome

Chosen option: "`RestClient` síncrono + jerarquía de excepciones técnicas vs. resultado de negocio", porque el resto del sistema es síncrono/bloqueante (locking pesimista dentro de `@Transactional`), y modelar `DECLINED` como valor de retorno normal —nunca como excepción— permite que la configuración de `retryExceptions` de Resilience4j excluya estructuralmente ese resultado, reduciendo `RISK-010` a nivel de diseño en vez de depender solo de disciplina de código.

### Consequences

* Good, porque la separación tipo-de-excepción hace que `BR-010` se cumpla a nivel de configuración de Resilience4j, no solo por convención de código.
* Good, porque mantiene un único paradigma de concurrencia (bloqueante) en todo el sistema, evitando mezclar Reactor con transacciones JPA bloqueantes.
* Bad, porque requiere definir y mantener cuidadosamente la jerarquía de excepciones (`AuthorizationTimeoutException`, `AuthorizationUnavailableException`) para que ningún fallo técnico se filtre como si fuera un resultado de negocio, o viceversa.
* Neutral, porque la decisión depende del supuesto sobre la versión de Spring Boot (`AQ-001`); si no se confirma 3.2+, esta decisión debe revisarse hacia `RestTemplate`.

### Confirmation

Test de integración dedicado (con WireMock) que verifica que, ante una respuesta `DECLINED`, se produce exactamente una llamada al proveedor (cero reintentos) y el resultado se persiste como `Transaction.status = FAILED` (`REQ-FUNC-017`). Test adicional que verifica que un timeout o `5xx` sí agota la política de reintentos configurada antes de responder `503` (`REQ-FUNC-018`).

## Pros and Cons of the Options

### RestClient síncrono + jerarquía de excepciones

* Good, porque es coherente con el resto del sistema (bloqueante, transaccional).
* Good, porque `RestClient` es la API síncrona moderna recomendada por Spring, sin la sobrecarga histórica de `RestTemplate`.
* Neutral, porque requiere confirmar la versión mínima de Spring Boot del proyecto.
* Bad, porque exige diseñar con cuidado la jerarquía de excepciones — inversión pequeña, pero no trivial.

### WebClient reactivo

* Good, porque es la opción con más soporte a largo plazo para integración HTTP no bloqueante.
* Bad, porque introduce un paradigma reactivo aislado en un sistema por lo demás completamente síncrono, sin beneficio real de throughput dado el alcance del proyecto.
* Bad, porque complica innecesariamente el manejo de transacciones JPA (bloqueantes) alrededor de la llamada.

### RestTemplate

* Good, porque es ampliamente conocido y suficiente funcionalmente.
* Neutral, porque está en modo mantenimiento (no deprecado, pero sin nuevas mejoras de Spring).
* Bad, porque no aporta ninguna ventaja sobre `RestClient` si la versión de Spring Boot ya lo soporta.

## More Information

Depende de `AQ-001` (versión de Spring Boot objetivo) — si el supuesto resulta incorrecto, esta decisión se revisa hacia `RestTemplate` sin cambiar el resto del diseño (la jerarquía de excepciones es independiente del cliente HTTP elegido). Relacionado con `DEC-009`, `DEC-012`, `DEC-013`, `BR-010`, `RISK-002`, `RISK-010`. Identificador de discovery: `MADR-002`.

### Nota de implementación (2026-09-11)

Al confirmar la versión real de Spring Boot en Fase 1 se resolvió `AQ-001`: se adopta Spring Boot 4.1.x (línea activa) en lugar de 3.5.x (sin soporte OSS gratuito desde 2026-06). Esto no cambia la decisión de fondo de este ADR — la refuerza, ya que en Spring Framework 7 `RestTemplate` queda deprecado en favor de `RestClient`.

Introduce una desviación de implementación: no existe (a 2026-09) un artefacto `resilience4j-spring-boot4` (issue abierto sin resolver: `resilience4j/resilience4j#2371`). La integración de Circuit Breaker + Retry (`DEC-012`) se implementa con los módulos core de Resilience4j (`resilience4j-circuitbreaker`, `resilience4j-retry`, `resilience4j-micrometer`), independientes de la versión de Spring Boot, componiendo la llamada HTTP de forma funcional en `AuthorizationHttpAdapter` (`Decorators.ofSupplier(...).withCircuitBreaker(...).withRetry(...)`) en vez de vía anotaciones `@CircuitBreaker`/`@Retry`. Los valores de `DEC-012` no cambian. El health indicator del Circuit Breaker (`REQ-OBS-001`), ya previsto como custom en Fase 12, pasa de ser una opción a ser la única vía posible.

Esta nota documenta una desviación descubierta durante la Fase 1; no reabre la decisión (Principio 1, Implementation Plan). Ver `AQ-001` en el Context Maestro (Sección 21) y `CON-001` (Sección 9).
