# ADR-0012: Downgrade of Spring Boot/Testcontainers Stack against CON-001

## Status
Accepted

## Context
`CON-001` (technical context, Section 2) hardcodes the stack as Spring Boot 4.1.1 / Spring Framework 7.0.8, without open ranges. During implementation (Phase 3 onwards), recurring incompatibilities arose between that exact version and the testing ecosystem (Testcontainers artifact names, `TestEntityManager` package location, `spring-boot-testcontainers` version) against Java 21 in the real development environment (WSL). The cost of resolving each incompatibility in isolation, phase after phase, outweighed the benefit of maintaining the exact version set by `CON-001` -- especially considering this is a portfolio project (Context Section 1: "no real production SLAs", `CON-007`), not a product with an external version contract to fulfill.

## Decision
- The actual `<parent>` of the project is `spring-boot-starter-parent:3.3.0` (deliberately downgraded from 4.1.1 during Phase 6, developer's decision addressing stability issues).
- `CON-001` is treated as the original design intent, not the final implemented state -- this is the formal and documented deviation replacing it.
- The downgrade is not reverted in later phases: the cost of re-verifying 8 closed phases (42 commits, 130+ tests) against a different version outweighs the benefit of complying with the letter of `CON-001`.
- Adopted policy for the rest of the project: every new dependency is verified with `./mvnw dependency:resolve`/`dependency:tree` BEFORE assuming its artifactId/version -- the compiler and actual build act as the source of truth, not documentation of older ecosystem versions.

## Consequences
- The README (Phase 15, final consolidation) will document the ACTUAL stack versions, not the uncorrected `CON-001` ones.
- Any interviewer reviewing the repo can see, via this ADR, a real example of conscious technical debt management regarding dependencies: identifying the problem, evaluating the cost of reverting vs. proceeding, and documenting the decision instead of leaving it implicit in the commit history.
- Accepted risk: the project no longer literally demonstrates "Spring Boot 4.1.1 mastery" -- it demonstrates something more valuable for an interview: "I can diagnose and manage version incompatibilities under delivery pressure".

## Update (Phase 8): Actual Versions and Subsequent Cost
Effective versions verified with `dependency:tree`: `spring-boot-starter-parent` 3.3.0, Testcontainers 1.19.8, `resilience4j-spring-boot3` 2.2.0 (not `resilience4j-spring-boot4` as indicated by CON-001), `spring-boot-starter-aop` 3.3.0, `wiremock-standalone` 3.5.4 (test).

Subsequent cost of the downgrade: Docker Desktop updated to Engine 29, which rejects clients using older APIs (400 Bad Request). The embedded docker-java in Testcontainers 1.19.8 failed to negotiate it correctly. Mitigated using `src/test/resources/docker-java.properties` (`api.version=1.44`). If a future Docker version raises the minimum again, this value must be adjusted or Testcontainers upgraded.

During this same troubleshooting, a hidden dependency surfaced: `contextLoads` relied on a local Postgres running on 5432. It was migrated to `PaymentGatewayEngineApplicationIT` over Testcontainers, making the full test suite entirely independent of local infrastructure.

## Update (Phase 10): Divergence between Local Environment and Committed State
`maven-failsafe-plugin` (convention `*IT`, Phase 3) existed only in the local `pom.xml` until commit `d6c27a0` (Phase 9). Verification of CS-01/CS-02 was real in the local environment (56 ITs executed), but previous commits do not reproduce the integration test execution. During the same period, a copy-paste incident reverted already corrected test files. A decision was made NOT to rewrite history (high cost and risk, marginal benefit). Structural mitigation: CI (Phase 14) executes `./mvnw verify` on the committed state, detecting this type of divergence on every push.