# ADR-0009: Strategy Pattern for Transfer Handlers by Transfer Type

## Status
Accepted

## Context
The project supports two types of transfers with very distinct rules and dependencies: INTERNAL (two internal accounts, no external calls, no Resilience4j) and EXTERNAL (one internal account + a simulated external provider via HTTP, with circuit breaker/retries). Mixing both flows in a single method with nested conditionals would quickly degrade readability and make it difficult to reason about the rules of each branch in isolation.

## Decision
- `TransferHandler` (`application/handler/`) defines the common contract: `Transaction handle(TransferCommand command)`.
- `InternalTransferHandler` (Phase 5) implements the INTERNAL branch: validates `BR-003/006/007`, applies `LockOrderPolicy`, and executes debit+credit+log in a single ACID transaction (`BR-004`). No `AuthorizationPort`.
- `ExternalTransferHandler` (Phase 8) will implement the EXTERNAL branch with the same contract, adding `AuthorizationPort` + Resilience4j.
- `TransferMoney` (`application/usecase/`) remains the single entry point for the use case. Since Spring cannot unambiguously resolve a generic `TransferHandler` injection when two concrete implementations exist, `TransferMoney` injects each handler by its CONCRETE TYPE (`InternalTransferHandler` since Phase 5; `ExternalTransferHandler` added as a second parameter in Phase 8) and manually dispatches via `switch` on `TransferType` -- `Map<TransferType, TransferHandler>` or dynamic resolution by bean name is not used, avoiding unjustified complexity for only two variants.
- While `ExternalTransferHandler` does not exist, `transfer_type=EXTERNAL` returns `400` via `UnsupportedTransferTypeException` (temporary scaffolding, not a business rule -- see Phase 5, Step 4).

## Consequences
- Each handler is testable in isolation with its own fakes (`InternalTransferHandlerTest`), without needing to simulate rules from the other branch.
- Adding a third `TransferType` in the future (currently out of scope) would require: a new `TransferHandler` implementation, a new constructor parameter in `TransferMoney`, and a new branch in the `switch` -- a localized change, not a full flow refactoring.
- Accepted cost: `TransferMoney` knows the concrete types of the handlers (not just the interface), a minor deviation from the "pure" Strategy pattern -- justified by having only two fixed, predetermined variants (not an extensible plugin system).

## Update (Phase 8)
- `TransferCommand` becomes a `sealed interface` with two records (`Internal`, `External`). Each variant validates its own fields upon construction, just like the XOR invariant of `Transaction`.
- `ExternalTransferHandler` implemented; `TransferMoney` receives it as a second constructor parameter, as anticipated.
- `TransferMoneyCommand.of(...)` is replaced by `forInternal(...)` and `forExternal(...)`.
- `UnsupportedTransferTypeException` (temporary scaffolding) is removed along with its handler.