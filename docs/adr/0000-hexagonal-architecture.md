# ADR-0000: Hexagonal / Clean Architecture with Enforced Dependency Rule

## Status
Accepted

## Context
The project demands (`CON-002` of the technical context) a hexagonal/clean architecture with strict separation between `domain`, `application`, and `infrastructure`, where `domain` must not have knowledge of frameworks (Spring, JPA). This is a starting restriction of the project, not one of the 11 numbered decisions in Section 20 of the context (`ADR-0001`–`ADR-0011`), so it is documented here as `ADR-0000` — a preamble that chronologically and numerically precedes that list, avoiding numbering collision with `ADR-0001` (manual domain↔JPA mapping, Phase 3).

Without automatic verification, this separation relies on human discipline in code review and tends to degrade over time (`REQ-MAINT-001`).

## Decision
- Three root packages: `domain/`, `application/`, `infrastructure/`.
- Dependency rule: `infrastructure → application → domain`.
- `domain` cannot depend on `org.springframework.*` or `jakarta.persistence.*`.
- The rule is verified automatically with ArchUnit (`archunit-junit5:1.5.0`) in `src/test/java/.../architecture/ArchitectureTest.java`, executed on every `./mvnw verify`.

## Consequences
- Any dependency rule violation breaks the build, not just the code review.
- `domain/` remains free of framework annotations, easier to unit test without Spring context.
- Cost: additional discipline when writing adapters — any Spring/JPA type leaking into a method signature in `domain` breaks CI immediately (this is intentional, not an unwanted side effect).