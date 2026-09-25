# ADR-0012: Downgrade del stack Spring Boot/Testcontainers respecto a CON-001

## Status
Accepted

## Context
`CON-001` (contexto técnico, Sección 2) fija el stack como Spring Boot
4.1.1 / Spring Framework 7.0.8, sin rangos abiertos. Durante la
implementación (Fase 3 en adelante), surgieron incompatibilidades
recurrentes entre esa versión y el ecosistema de herramientas de testing
(nombres de artifact de Testcontainers, ubicación de paquete de
`TestEntityManager`, versión de `spring-boot-testcontainers`) contra
Java 21 en el entorno real de desarrollo (WSL). El coste de resolver cada
incompatibilidad de forma aislada, fase tras fase, superaba el beneficio
de mantener la versión exacta fijada por `CON-001` -- especialmente
considerando que este es un proyecto de portafolio (Sección 1 del
contexto: "sin SLAs productivos reales", `CON-007`), no un producto con
un contrato de versión externo que cumplir.

## Decision
- El `<parent>` real del proyecto es `spring-boot-starter-parent:3.3.0`
  (degradado deliberadamente desde 4.1.1 durante la Fase 6, decisión del
  desarrollador ante problemas de estabilidad).
- `CON-001` se trata como la intención de diseño original, no como el
  estado final implementado -- esta es la desviación formal y documentada
  que la reemplaza.
- No se revierte el downgrade en fases posteriores: el coste de
  re-verificar 8 fases ya cerradas (42 commits, 130+ tests) contra una
  versión distinta supera el beneficio de cumplir la letra de `CON-001`.
- Política adoptada para el resto del proyecto: toda dependencia nueva se
  verifica con `./mvnw dependency:resolve`/`dependency:tree` ANTES de
  asumir su artifactId/versión -- el compilador y el build real son la
  fuente de verdad, no la documentación de versiones anteriores del
  ecosistema.

## Consequences
- El README (Fase 15, consolidación final) documentará las versiones
  REALES del stack, no las de `CON-001` sin corregir.
- Cualquier entrevistador que revise el repo puede ver, vía este ADR, un
  ejemplo real de gestión consciente de deuda técnica de dependencias:
  identificar el problema, evaluar el coste de revertir vs. continuar, y
  documentar la decisión en vez de dejarla implícita en el historial de
  commits.
- Riesgo aceptado: el proyecto ya no demuestra literalmente "domino
  Spring Boot 4.1.1" -- demuestra algo más valioso para una entrevista:
  "sé diagnosticar y gestionar incompatibilidades de versión bajo
  presión de entrega".

  ## Update (Fase 8): versiones reales y coste posterior
Versiones efectivas verificadas con `dependency:tree`:
`spring-boot-starter-parent` 3.3.0, Testcontainers 1.19.8,
`resilience4j-spring-boot3` 2.2.0 (no `resilience4j-spring-boot4` como
indicaba CON-001), `spring-boot-starter-aop` 3.3.0,
`wiremock-standalone` 3.5.4 (test).

Coste posterior del downgrade: Docker Desktop se actualizó a Engine 29,
que rechaza clientes con API antigua (400 Bad Request). El docker-java
embebido en Testcontainers 1.19.8 no la negociaba correctamente. Se
mitiga con `src/test/resources/docker-java.properties`
(`api.version=1.44`). Si una futura versión de Docker sube otra vez el
mínimo, habrá que ajustar este valor o subir Testcontainers.

Durante el mismo troubleshooting apareció una dependencia oculta:
`contextLoads` usaba un Postgres local en 5432. Se migró a
`PaymentGatewayEngineApplicationIT` sobre Testcontainers, con lo que la
suite completa ya no depende de infraestructura local.

## Update (Fase 10): divergencia entre entorno local y estado commiteado
`maven-failsafe-plugin` (convención `*IT`, Fase 3) existió solo en el
`pom.xml` local hasta el commit `d6c27a0` (Fase 9). La verificación de
CS-01/CS-02 fue real en el entorno local (56 ITs ejecutados), pero los
commits anteriores no reproducen la ejecución de tests de integración.
En el mismo periodo, un incidente de copy-paste revirtió archivos de test
ya corregidos. Se decide NO reescribir el historial (coste y riesgo
altos, beneficio marginal). Mitigación estructural: CI (Fase 14) ejecuta
`./mvnw verify` sobre el estado commiteado, lo que detecta este tipo de
divergencia en cada push.