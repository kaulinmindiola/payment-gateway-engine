# ADR-0000: Hexagonal / Clean Architecture con regla de dependencia enforced

## Status
Accepted

## Context
El proyecto exige (`CON-002` del contexto técnico) una arquitectura hexagonal/clean
con separación estricta entre `domain`, `application` e `infrastructure`, donde
`domain` no debe tener conocimiento de frameworks (Spring, JPA). Esta es una
restricción de partida del proyecto, no una de las 11 decisiones numeradas en
la Sección 20 del contexto (`ADR-0001`–`ADR-0011`), por lo que se documenta
aquí como `ADR-0000` — un preámbulo que antecede cronológica y numéricamente
a esa lista, evitando colisión de numeración con `ADR-0001` (mapeo manual
dominio↔JPA, Fase 3).

Sin verificación automática, esta separación depende de disciplina humana en
code review y tiende a degradarse con el tiempo (`REQ-MAINT-001`).

## Decision
- Tres paquetes raíz: `domain/`, `application/`, `infrastructure/`.
- Regla de dependencia: `infrastructure → application → domain`.
- `domain` no puede depender de `org.springframework.*` ni `jakarta.persistence.*`.
- La regla se verifica automáticamente con ArchUnit (`archunit-junit5:1.5.0`)
  en `src/test/java/.../architecture/ArchitectureTest.java`, ejecutado en
  cada `./mvnw verify`.

## Consequences
- Cualquier violación de la regla de dependencia rompe el build, no solo el
  code review.
- `domain/` queda libre de anotaciones de framework, más fácil de testear
  unitariamente sin contexto de Spring.
- Costo: disciplina adicional al escribir adapters — cualquier tipo de
  Spring/JPA que se filtre a una firma de método en `domain` rompe CI
  inmediatamente (esto es intencional, no un efecto secundario indeseado).