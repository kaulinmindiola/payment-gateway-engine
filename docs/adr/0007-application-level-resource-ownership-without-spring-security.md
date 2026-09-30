# ADR-0007: Application-Level Resource Ownership without Spring Security

## Status
Accepted

## Context
The project dictates that accounts and transactions are scoped to a specific user, requiring ownership validation (403 Forbidden). However, `CON-007` and `ADR-0007` stipulate that real authentication is out of scope and ownership will be resolved strictly at the application layer without deploying Spring Security.

## Decision
- Simulated identity is utilized via an HTTP header (`X-User-Id`) passed directly into the web controllers. 
- Ownership is verified at the Application/Domain layer (e.g., Use Cases validate if the `X-User-Id` matches the owner of the source Account).
- Spring Security is intentionally excluded to avoid unnecessary complexity, security contexts, and filter chains for a purely simulated portfolio environment.

## Consequences
- Clean, lightweight controllers and services that explicitly accept a `UUID userId`.
- Straightforward unit and integration testing without mocking security contexts.
- If real authentication is needed in the future, an API Gateway or a simple filter can handle standard tokens (JWT) and map them to the `X-User-Id` downstream.