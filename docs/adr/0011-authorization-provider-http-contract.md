# ADR-0011: HTTP contract with the external authorization provider

## Status
Accepted (updated in Phase 15)

## Context
EXTERNAL transfers need an authorization from a simulated external
provider. Several providers exist (reliable, flaky), and the contract
must let the application tell apart a business result (approved or
declined) from a technical failure (timeout, server error), because only
technical failures may be retried (RISK-010).

## Decision
- A single endpoint for every provider: `POST /v1/authorizations`.
  Routing is done with the `X-Provider-Code` header, so the adapter stays
  provider-agnostic and one versioned set of WireMock stubs covers every
  provider (`wiremock/mappings/`, shared by tests and Docker Compose,
  RISK-007).
- Request headers: `X-Provider-Code`, `X-Idempotency-Key` (the same key
  the client sent, so provider-side retries are safe) and `X-Trace-Id`
  (correlation with our logs, ADR-0005).
- Request body: `sourceAccountId`, `targetBankId`,
  `targetExternalReference`, `amount` and `currency`. `amount` travels as
  a string (`"100.00"`) to avoid floating-point loss (CON-005).
- The business result is ALWAYS in the body of an HTTP 200:
  `status` = `APPROVED` (with `providerReference`) or `DECLINED` (with
  `reason`), plus `processedAt`. It is never encoded in the HTTP status.
- Technical failures are only transport-level: timeouts and 5xx
  responses. They map to `AuthorizationTechnicalException`, which is the
  only type Resilience4j retries (ADR-0002).

## Update (Phase 15): contract violations
A 200 that breaks this contract (empty body, unknown `status`, APPROVED
without `providerReference`, DECLINED without `reason`, unreadable JSON)
raises `AuthorizationProtocolException`, a technical failure: retried,
idempotency key released, 503 to the client.

## Consequences
- DECLINED can never trigger a retry: the channel separates business
  from technical outcomes by construction (verified by
  `ExternalTransferResilienceIT` counting provider calls).
- Adding a provider only requires new stubs and catalog rows, not code.
- Unexpected 4xx responses from the provider are not handled explicitly
  (out of scope).