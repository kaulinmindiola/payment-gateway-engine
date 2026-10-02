# ADR-0002: Resilience4j via Annotations on AuthorizationHttpAdapter

## Status
Accepted

## Context
EXTERNAL transfers depend on a remote authorization provider that can experience technical failures (timeout, 5xx). Without resilience mechanisms, every transient failure would result in a client error, and an unavailable provider would continue receiving traffic, exhausting threads and connections. At the same time, an unbounded retry strategy could duplicate an authorization (`RISK-002`, `CS-02`).

## Decision
- `@CircuitBreaker` and `@Retry` (instance `authorizationProvider`) are applied as annotations on `AuthorizationHttpAdapter.authorize()`. Manual functional composition (`Decorators.ofSupplier`) is not used. The `domain/` and `application/` layers remain unaware of Resilience4j.
- Configuration values (Context Section 11): circuit breaker is COUNT_BASED, `slidingWindowSize=10`, `minimumNumberOfCalls=10` (explicitly set; library default is 100), `failureRateThreshold=50`, `waitDurationInOpenState=10s`, `permittedNumberOfCallsInHalfOpenState=3`. Retry: `maxAttempts=3`, `waitDuration=200ms`, exponential backoff multiplier x2.
- HTTP client timeouts (independent of Resilience4j): connect 1s, read 2s.
- Only TECHNICAL failures are retried: `retryExceptions` exclusively lists `AuthorizationTechnicalException` (parent class for timeout and 5xx errors). APPROVED/DECLINED are return values, never exceptions, meaning a DECLINED response is never retried (`BR-010`).
- Open circuit handling: `CallNotPermittedException` is thrown by the aspect, outside the method execution. A `fallbackMethod` strictly typed for this exception translates it to `AuthorizationUnavailableException`. This allows `TransferMoney` to handle it like any other technical failure (releasing the idempotency key and returning a 503).
- Aspect execution order: Resilience4j default is maintained (Retry wraps CircuitBreaker). The circuit breaker records each individual attempt. Tests verify the observable behavior (the circuit opens and the provider stops receiving traffic) rather than the exact number of transfer attempts required to open it.
- Unexpected 4xx responses from the provider are not explicitly handled at this time (planned for future extension, Plan Section 22).
- `X-Trace-Id` is generated as a UUID per call: this acts as a placeholder until Phase 10 (TraceIdFilter + MDC).

## Consequences
- `CS-02` successfully verified by `ExternalTransferResilienceIT` using real WireMock stubs: DECLINED results in exactly 1 call; persistent 5xx and timeouts exhaust 3 attempts without persisting data; following a technical failure, a client retry with the same key executes exactly once; when the circuit is open, the provider receives no new calls.
- Annotations require active AOP (`spring-boot-starter-aop`). Without it, the code compiles but ignores the resilience rules. For this reason, tests assert the actual number of requests received by WireMock, not just the final return value.
- Trade-off: Resilience tests wait for real timeouts (~2s per attempt), which increases the overall execution time of the integration test suite.

## Update (Phase 15): responses that violate the provider contract
A 200 response that breaks the ADR-0011 contract (empty body, unknown
`status`, APPROVED without `providerReference`, DECLINED without `reason`,
unreadable JSON) is a technical failure of the channel, not a business
result. It raises `AuthorizationProtocolException`, a subtype of
`AuthorizationTechnicalException`, and therefore inherits retries, key
release and the 503 mapping. JSON parsing errors are classified before
generic I/O errors because `JsonProcessingException` is an `IOException`.
Unexpected 4xx responses remain out of scope.
## Update (Phase 15): Responses that Violate the Provider Contract
A 200 response that breaks the ADR-0011 contract (empty body, unknown `status`, APPROVED without `providerReference`, DECLINED without `reason`, unreadable JSON) is a technical failure of the channel, not a business result. It raises `AuthorizationProtocolException`, a subtype of `AuthorizationTechnicalException`, and therefore inherits retries, key release and the 503 mapping. JSON parsing errors are classified before generic I/O errors because `JsonProcessingException` is an `IOException`. Unexpected 4xx responses remain out of scope.
