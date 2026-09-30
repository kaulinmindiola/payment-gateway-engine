# ADR-0008: User as a minimal identity, seeded by migrations

## Status
Accepted (updated in Phase 15)

## Context
Accounts need an owner (BR-012: creating an account for a non-existent
user returns 404), and ownership rules need a stable identity
(ADR-0007). Full user management (sign-up, profile editing, deletion) is
out of scope: it adds no value to the core problem of the project, which
is moving money correctly.

## Decision
- `users` is a minimal entity: `id`, `email`, `name`, `status`.
- The domain `User` has no creation factory, only `reconstitute`: users
  are never created by application code.
- Users are inserted by migrations (docker seed profile) or by test
  fixtures, the same pattern as the reference catalog (ADR-0010).
- `UserRepositoryPort` exposes only what the use cases need:
  `existsById` (BR-012) and `findById` (see update below).
- There is no endpoint to create, edit, delete or list users.

## Update (Phase 15): read-only self endpoint
`GET /api/v1/users/me` returns the identity behind `X-User-Id` (id, name,
email, status). It is read-only and scoped to the caller, so it is not
user management. Motivation: in the Swagger UI guided tour, a reviewer can
verify who they are acting as instead of trusting a UUID copied from the
documentation. The docker seed adds a second user (Bob) and fixed-id
accounts so the ownership rules (403) can be demonstrated in the tour.

## Consequences
- The identity model is enough for every business rule and for the demo,
  with no management surface to secure or test.
- New users require a migration; acceptable for a portfolio project with
  seeded demo data.