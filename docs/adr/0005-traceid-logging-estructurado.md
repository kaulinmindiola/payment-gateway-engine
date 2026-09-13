ADR 005
status: "accepted"
date: 2026-09-08
decision-makers: "Principal Developer"
---

# Propagación de traceId y logging estructurado vía Filter + MDC

## Context and Problem Statement

`DEC-011` (formato de error RFC 7807) y `DEC-014` (observabilidad) exigen que cada respuesta de error incluya un `traceId` que se pueda correlacionar exactamente con las líneas de log generadas durante esa misma request (`REQ-OBS-002`). Es necesario decidir el mecanismo concreto de generación y propagación de ese identificador.

## Decision Drivers

* `AC-005` — trazabilidad de errores end-to-end (API ↔ logs).
* `DEC-011`, `DEC-014` — decisiones ya aceptadas que este ADR debe implementar.
* `CON-007` — proyecto de alcance portafolio; evitar sobre-ingeniería no justificada.

## Considered Options

* Servlet Filter que genera un UUID por request (o respeta uno entrante si existe), lo coloca en el MDC de SLF4J; el manejador global de excepciones lo lee del MDC al construir el cuerpo RFC 7807; Logback configurado con `logstash-logback-encoder` para salida JSON incluyendo los campos del MDC.
* Micrometer Tracing + Brave/OpenTelemetry (tracing distribuido completo, con spans).

## Decision Outcome

Chosen option: "Servlet Filter + MDC + Logback JSON", porque el sistema es un monolito de un solo nodo con una única dependencia HTTP saliente (`authorization-provider`); no existe un grafo de servicios que justifique la complejidad de un sistema de tracing distribuido con spans y contexto propagado entre procesos (`CON-007`, principio de no sobrearquitectura).

### Consequences

* Good, porque satisface exactamente lo que exige `REQ-OBS-002` con una implementación mínima (un Filter y una dependencia de encoder JSON).
* Good, porque no introduce infraestructura de tracing (colector, backend de trazas) que no tendría consumidor real en este alcance.
* Bad, porque si el sistema evolucionara hacia múltiples servicios, este mecanismo ad-hoc debería reemplazarse por tracing distribuido real — no escala como está diseñado.
* Neutral, porque el `traceId` es un identificador de alcance de request, no un contexto de traza distribuida con relaciones padre/hijo entre spans.

### Confirmation

Test de inspección/demostración que verifica que el `traceId` presente en el cuerpo de una respuesta de error RFC 7807 coincide exactamente con el `traceId` de las líneas de log JSON generadas durante esa misma request (criterio de aceptación de `REQ-OBS-002`).

## Pros and Cons of the Options

### Servlet Filter + MDC + Logback JSON

* Good, porque es la solución más simple que satisface el requisito conocido.
* Good, porque no depende de infraestructura externa adicional (colector de trazas).
* Bad, porque no provee visibilidad de "spans" internos si en el futuro se quisiera medir tiempos de sub-operaciones dentro de una misma request.

### Micrometer Tracing + Brave/OpenTelemetry

* Good, porque es el estándar de facto para observabilidad en sistemas distribuidos y escalaría naturalmente si el sistema creciera a múltiples servicios.
* Bad, porque introduce dependencias, configuración y (típicamente) un backend de recolección de trazas sin consumidor real en un despliegue de un solo nodo — sobre-ingeniería frente a `CON-007`.

## More Information

Relacionado con `DEC-011`, `DEC-014`, `CON-007`, `RISK-011`. Identificador de discovery: `MADR-005`.
