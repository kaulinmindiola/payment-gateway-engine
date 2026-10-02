# ADR-0008: Minimal User Identity without Management API

## Status
Accepted

## Context
The system requires a User concept to assign ownership to accounts and transactions (ADR-0007). However, exposing CRUD operations for users distracts from the core domain (payments engine) and violates the requirement to exclude non-essential endpoints (`CON-010`).

## Decision
- The User identity is modeled as a minimal entity strictly to enforce relational integrity and ownership rules.
- There is no exposed API (no controllers) for user creation, update, listing, or deletion.
- User records are strictly populated via database fixtures/migrations (Flyway), matching the pattern established for other reference data (`CON-010`).

## Consequences
- Reduces boilerplate and maintains focus strictly on the payment engine domain.
- The `X-User-Id` provided by the client in requests is assumed to exist via seeding. If it does not, the system safely returns standard domain validation errors (e.g., User Not Found / 404).

## Update (Phase 15): read-only self endpoint
`GET /api/v1/users/me` returns the identity behind `X-User-Id` (id, name, email, status). It is read-only and scoped to the caller, so it is not user management: users are still created only by migrations and there is no endpoint to create, edit, delete or list users. Motivation: the Swagger UI guided tour lets a reviewer verify who they are acting as, instead of trusting a UUID from the documentation. The docker seed adds a second user (Bob) and fixed-id accounts so the ownership rules (403) can be demonstrated in the tour.