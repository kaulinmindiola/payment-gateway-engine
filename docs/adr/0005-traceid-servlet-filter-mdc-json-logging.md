# ADR-0005: traceId with Servlet Filter + MDC and JSON Logging, without Distributed Tracing

## Status
Accepted

## Context
BR-011 requires that every RFC 7807 error includes a traceId exactly matching the logs of that request, and the authorization provider (Section 7.1) receives `X-Trace-Id` to correlate its logs with ours. The system is a single-node monolith, lacking a service graph to justify distributed tracing (Micrometer Tracing, OTel).

## Decision
- `TraceIdFilter` (highest precedence) generates or respects the incoming `X-Trace-Id`, places it in the MDC for the entire request duration, returns it in the header of ALL responses, and clears it in a `finally` block (Tomcat threads are reused).
- An incoming value is only respected if it matches `^[A-Za-z0-9-]{1,64}$`: it is untrusted input that ends up in logs (log injection prevention).
- `TraceContext` is the single access point: `GlobalExceptionHandler` adds it to every ProblemDetail and `AuthorizationHttpAdapter` propagates it to the provider. This works because all processing is synchronous on the same thread; future async code would need to propagate MDC explicitly.
- Logs: readable text with `[traceId=...]` outside the docker profile; JSON (logstash-logback-encoder 7.4) in the docker profile, exposing only the `traceId` key from MDC.
- Known errors are logged as WARN without stack traces; unknown errors (catch-all → 500) as ERROR with stack traces. The client only receives a safe `detail`; internal messages go to the log, sanitized.
- BR-002 vs BR-011 conflict: a response replayed by idempotency returns the EXACT cached body (BR-002 prevails). That body does not contain the current request's traceId; correlation is maintained via the `X-Trace-Id` header and logs.

## Consequences
- Correlation verified automatically: `TraceIdLogCorrelationTest` compares the generated traceId in the response with the MDC in the log event.
- Code contract verified in `ErrorContractTest`; real examples in `docs/api/error-examples.md`, regenerable via script.
- No cross-process traces: if the system were split into microservices, this decision would need revision.

## Update (Phase 11): Health and Metrics
- Actuator exclusively exposes `health` and `prometheus` (public endpoints, no authentication, Context Section 6). `env`, `beans`, etc., are not exposed as they may leak configuration.
- `/actuator/health` shows component status (`db`, `redis`, `circuitBreakers`) but not details (`show-details: never`): no versions or internal circuit states.
- Circuit OPEN -> `circuitBreakers` component DOWN (`allowHealthIndicatorToFail: true`, explicit). HALF_OPEN -> UNKNOWN, which does not degrade the aggregated status.
- Aggregation semantics: any downed dependency -> DOWN (HTTP 503), per context. The health endpoint answers "are my dependencies healthy?", NOT "can I serve traffic?": without Redis, the system remains correct (ADR-0003), and with an open circuit, INTERNAL transfers still work. Separating liveness/readiness would only make sense with an orchestrator (out of scope: Docker Compose, ADR-0006).
- Metrics via Micrometer + Prometheus, with the common tag `application=payment-gateway-engine`. Resilience4j metrics arrive via `resilience4j-micrometer`. No custom business metrics are added.
- Reconfirmed: no distributed tracing (RISK-011).
- In tests, Spring Boot disables metric export by default; `HealthEndpointOutageIT` reactivates it using `@AutoConfigureObservability`.