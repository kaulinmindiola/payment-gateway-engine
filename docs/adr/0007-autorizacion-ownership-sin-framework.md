ADR 007
status: "accepted"
date: 2026-09-08
decision-makers: "Principal Developer"
---

# Autorización por ownership resuelta como lógica de aplicación, sin framework de seguridad

## Context and Problem Statement

`CON-004` y `CON-006` ya excluyen autenticación criptográfica real y gestión completa de usuarios; el `X-User-Id` es un identificador simulado. El sistema necesita, aun así, restringir `GET /accounts/{id}` y `GET /transactions/{id}` a los usuarios relacionados con ese recurso (`BR-008`, `BR-009`). Es necesario decidir si esta comprobación de ownership se resuelve como lógica de aplicación simple o si se introduce un framework de seguridad (Spring Security) para gestionarla.

## Decision Drivers

* `AC-007` — autorización simulada sin sobre-construir seguridad.
* `CON-004` — sin autenticación compleja ni gestión completa de usuarios.
* `CON-006` — `X-User-Id` es un identificador simulado, no autenticación criptográfica real.

## Considered Options

* Bean Validation (JSR-380) para la forma de la entrada, más comparación explícita `X-User-Id` vs. `owner_id` en la capa de aplicación (interceptor o chequeo directo en el caso de uso).
* Spring Security con un `AuthenticationProvider`/`Filter` custom que mapea `X-User-Id` a un principal simulado.

## Decision Outcome

Chosen option: "Bean Validation + comparación explícita en la capa de aplicación", porque `CON-004`/`CON-006` ya excluyen explícitamente autenticación compleja; introducir un framework de seguridad completo (con su cadena de filtros, contexto de seguridad y proveedores de autenticación) para comparar un valor de header contra un campo de base de datos sería complejidad no justificada por el problema real, dado que solo existen dos recursos protegidos por ownership en todo el sistema.

### Consequences

* Good, porque mantiene la superficie de código mínima y directamente proporcional al problema real (dos endpoints protegidos).
* Good, porque es coherente con `CON-004`/`CON-006`, que ya descartan de raíz un modelo de autenticación robusto.
* Bad, porque el chequeo de ownership queda como código explícito en lugar de una declaración declarativa (p. ej. `@PreAuthorize`), lo cual sería menos escalable si el número de recursos protegidos creciera significativamente.
* Neutral, porque esta decisión no afecta el riesgo ya aceptado de enumeración de cuentas vía `403` (`RISK-006`), que es independiente del mecanismo elegido aquí.

### Confirmation

Tests dedicados: `REQ-FUNC-005` (rechazo de consulta de cuenta ajena con `403`) y su equivalente para `GET /transactions/{id}` bajo `REQ-FUNC-020`/`BR-009`.

## Pros and Cons of the Options

### Bean Validation + comparación explícita en la capa de aplicación

* Good, porque es proporcional al tamaño real del problema (2 recursos protegidos).
* Good, porque no introduce una dependencia ni un modelo conceptual (contexto de seguridad, cadena de filtros) que el proyecto no necesita.
* Bad, porque no se beneficia de las abstracciones declarativas de un framework de seguridad si el sistema creciera.

### Spring Security con `AuthenticationProvider` custom

* Good, porque ofrece un modelo más extensible si en el futuro se añadieran más recursos protegidos o reglas de autorización más complejas.
* Bad, porque introduce una dependencia y un modelo conceptual completo (principal, contexto de seguridad, cadena de filtros) para resolver una comparación de dos strings — desproporcionado frente a `CON-004`/`CON-006`.

## More Information

Relacionado con `CON-004`, `CON-006`, `REQ-SEC-004`, `RISK-006` (riesgo de enumeración de cuentas vía `403`, aceptado de forma independiente a esta decisión). Identificador de discovery: `MADR-007`. A diferencia de los demás ADR de este conjunto, esta decisión tiene bajo peso arquitectónico — se documenta como registro formal a solicitud del proceso, pero también se resume en la Sección 4 (Decisions) del MSDD.
