# ADR-0011: AuthorizationPort HTTP Contract

## Status
Accepted

## Context
The system integrates with a simulated external authorization provider to process EXTERNAL transfers (`AuthorizationPort`). The integration must define a clear, resilient HTTP contract to handle technical failures appropriately without confusing them with business rejections.

## Decision
- The integration uses a single endpoint: `POST /v1/authorizations`.
- Routing to specific providers is managed via the `X-Provider-Code` HTTP header.
- The business result (e.g., APPROVED or DECLINED) is **always** returned in the `status` field of the JSON body alongside an HTTP `200 OK` response code.
- HTTP response codes other than 200 (such as 4xx or 5xx) are never used to communicate business outcomes. They are strictly treated as technical channel failures (triggering retries and resilience policies per ADR-0002).

## Consequences
- Absolute clarity between a provider rejecting a transfer (business outcome, no retry) and the provider being unreachable or timing out (technical failure, eligible for retries and circuit breaking).
- A unified approach that simplifies the fallback and exception handling logic within `ExternalTransferHandler`.