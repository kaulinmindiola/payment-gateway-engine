# ADR-0003: Dual Idempotency (Redis + Postgres UNIQUE)

## Status
Accepted

## Context
`BR-002` demands that a second request with the same `X-Idempotency-Key` never re-executes an already processed transfer, even under timeout, client retry, or partial infrastructure failure (`RISK-002`, `RISK-003`). Relying on a single mechanism poses a real point of failure: if only Redis existed, its outage would entirely remove the idempotency guarantee, not just its speed.

## Decision
- Use case level interception: `TransferMoney.execute()` claims the key via `IdempotencyPort.tryBegin()` (Redis `SET NX`, 24h TTL) BEFORE invoking the corresponding handler (`InternalTransferHandler` today; `ExternalTransferHandler` in Phase 8, same single entry point).
- Three possible results from `tryBegin()`: `ACQUIRED` (execute), `IN_PROGRESS` (409, via `IdempotencyConflictException`), or terminal `COMPLETED`/`FAILED` (return the exact cached response without re-executing -- `TransferOutcome.Replayed`).
- Postgres backstop: `transactions.idempotency_key UNIQUE` (Phase 3). If Redis does not respond (exception caught in `RedisIdempotencyAdapter`, logged as `WARN`, never propagated), `tryBegin()` returns a fallback `acquired()` -- the flow executes anyway, trusting that Postgres will reject a real duplicate with `DataIntegrityViolationException`. `TransferMoney` catches that exception and retrieves the original `Transaction` via `TransactionRepositoryPort.findByIdempotencyKey()`.
- The cached response in Redis is serialized as plain JSON (internal envelope format separated by `|`: `status|httpStatus|body`) using custom types from `application/usecase/` (`CachedTransferPayload`, `CachedProblemPayload`) that deliberately mirror the DTOs from `infrastructure/web/` -- `application/` cannot depend on `infrastructure/web/` (ArchUnit layer rule), so the structural duplication is intentional, not an oversight.
- Only business exceptions (`DomainException`) are cached as `FAILED`. Programming/temporary scaffolding exceptions (`IllegalArgumentException`, `UnsupportedTransferTypeException`) are not cached -- a key claimed in that path remains `IN_PROGRESS` until TTL expiration, accepted as a minor and bounded limitation (see Phase 6, gap discussion in Step 2).

## Consequences
- `RISK-003` verified with a dedicated test (`RedisOutageIdempotencyIT`): Redis completely stopped mid-sequence, two attempts with the same key result in exactly one row in `transactions`, and the balance reflects a single debit.
- System correctness NEVER depends on Redis availability -- only its response speed to a duplicate (with Redis down, a real duplicate costs a full execute+rollback cycle instead of an instant in-memory rejection, but the final outcome is identical).
- Cost: two mechanisms to keep conceptually synchronized (same criteria for what constitutes a "duplicate"), and structural duplication between `application/usecase/Cached*Payload` and `infrastructure/web/*Response` that must be updated in both places if the transfer contract changes.

## Update (Phase 8)
`IdempotencyPort` gains `release(key)`. When `TransferMoney` catches an `AuthorizationTechnicalException` (timeout, 5xx, or open circuit translated by ADR-0002 fallback), it releases the key instead of leaving it `IN_PROGRESS` until TTL expires. Thus, a legitimate client retry after a 503 executes normally without receiving a 409. The original limitation remains only for programming exceptions (`IllegalArgumentException`), which are practically unreachable because Bean Validation filters them first. `CS-02` holds: if the retry persists the transaction, the `idempotency_key` UNIQUE constraint remains the backstop.

## Update (Phase 11): Redis Client Timeouts
The Postgres fallback during a Redis outage is only useful if activated quickly. Lettuce defaults to waiting up to 60s for an unresponsive Redis (network partition, hung server); a stopped container, however, rejects connections instantly, which is why `RedisOutageIdempotencyIT` didn't catch the timeout issue. `spring.data.redis.timeout` and `connect-timeout` are now explicitly set to 500 ms. Manually verified: with Redis paused, `/actuator/health` responds in ~0.6 s.