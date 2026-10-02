# ADR-0004: Pessimistic Locking with Isolated LockOrderPolicy

## Status
Accepted

## Context
INTERNAL transfers (Phase 5) require modifying two accounts (debit + credit) atomically under high concurrency (`CS-01`). Two strategies are possible: optimistic locking (via `Account.version`, already reserved in the model since Phase 2) or pessimistic locking (explicit row lock before reading). With optimistic locking, under high contention on the same pair of accounts, most concurrent transactions would fail with `OptimisticLockException` and require application-level retries -- added complexity not justified for the contention volume that `CS-01` itself demands testing (20/50 threads on the same pair).

Additionally, without a deterministic lock acquisition order, two concurrent transfers in opposite directions on the same account pair (simultaneous A->B and B->A) would produce a classic deadlock: each transaction locks one account and waits indefinitely for the other (`RISK-001`).

## Decision
- Single strategy: pessimistic locking via JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`AccountJpaRepository.findByIdForUpdate`, Phase 3), inside a single `@Transactional` method (`InternalTransferHandler.handle`, Phase 5).
- `LockOrderPolicy` (`domain/policy/`) resolves the ascending acquisition order by `UUID.compareTo()`, invoked ONCE per transfer, before any `findByIdForUpdate()`. It is a pure domain class, without framework annotations -- Spring registers it as a bean explicitly via `infrastructure/config/DomainPolicyConfig`, without `domain/` knowing about Spring.
- `Account.version` (optimistic locking) remains reserved, unused (Phase 2, Plan Principle 6) -- not combined with pessimistic locking in this scope.
- Native SQL (`nativeQuery`) is not used for `FOR UPDATE` -- it delegates to Spring Data JPA's `@Lock` annotation, leaving Hibernate responsible for correctly translating it to the configured dialect.

## Consequences
- **Empirical verification note** (Phase 3, Step 4): On PostgreSQL 16 with Hibernate 7, `PESSIMISTIC_WRITE` translates to `FOR NO KEY UPDATE`, not literal `FOR UPDATE` -- a native Postgres optimization to reduce contention on FK chains. It was explicitly verified (capturing the actual emitted SQL, not just assuming text context) that this variant preserves the required mutual exclusion: no other system path requests `FOR KEY SHARE`/`FOR SHARE` on `accounts`, so two concurrent `FOR NO KEY UPDATE` queries on the same row still mutually block each other, just as `FOR UPDATE` would.
- `CS-01` verified with a concurrency test (20/50 threads, same account pair) reproducible across 10 consecutive runs.
- `RISK-001` verified with cross-concurrency test (simultaneous A->B/B->A, 40 threads) -- no deadlocks, exact net balance.
- Cost: each INTERNAL transfer retains a row lock for the entire duration of the transaction -- acceptable for the volume declared by the project (Context Section 1: portfolio, no real production SLA, `CON-007`).

## Update (Phase 9)
Correction: with the actual stack (ADR-0012, Spring Boot 3.3.0) the version is Hibernate 6.x, not 7. The verified behavior remains unchanged: on PostgreSQL 16, `PESSIMISTIC_WRITE` is emitted as `FOR NO KEY UPDATE`.