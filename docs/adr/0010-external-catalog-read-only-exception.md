# ADR-0010: Providers/External Banks Catalog as a Read-Only Exception

## Status
Accepted

## Context
The project explicitly mandates (`CON-008`) not to expose CRUD management endpoints without a real business need -- consistent with the lack of endpoints for creating users (`ADR-0008`) or changing `AccountStatus` (`CON-010`). However, `ExternalTransferHandler` (Phase 8) needs to validate `BR-013` (active target bank/provider) against real catalog data, and this catalog must be queryable by an external client before attempting a transfer (to build the request with valid `targetProviderId`/`targetBankId`).

## Decision
- `GET /api/v1/external-banks` and `GET /api/v1/external-banks/{id}` are read-only, without authentication (reference data, not a user resource) -- an explicit and scoped exception to `CON-008`.
- The response is denormalized: it includes `providerCode` inline (resolved in `application/usecase/ExternalBankView`, without altering `domain/model/ExternalBank`, which continues to reference `Provider` solely by id). This prevents the client from making a second query to a non-existent `providers` endpoint -- `GET /providers` is not exposed separately, as no use case requires it in isolation (the same "no capacity without a real consumer" criteria applied in previous phases).
- No pagination: the catalog is bounded reference data (simulated providers/banks for demo), not an unbounded transactional resource like `Transaction` (which is paginated, Phase 9).
- `currency` in the response is purely informative metadata -- it does not participate in any `amount` validation (multi-currency strictly remains out of scope, Context Section 19).
- No filtering by `status` in the query: an `INACTIVE` bank/provider remains visible in the catalog. Rejection due to inactive status occurs in `BR-013` (Phase 8) when attempting a real transfer -- not during the read-only query.
- Both tables are seeded via isolated Flyway migrations (`db/seed/docker/`, outside the tree scanned by the default profile) -- never via API, using the same pattern as `users` (`ADR-0008`).

## Consequences
- `ExternalTransferHandler` (Phase 8) can directly reuse `ProviderRepositoryPort`/`ExternalBankRepositoryPort` for `BR-013`, without needing a new read port.
- Accepted cost: the full catalog is loaded in a single, unbounded response -- acceptable given that it is a fixed, small demo dataset (not a real-scale requirement, Context Section 3).