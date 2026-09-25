# ADR-0005: traceId con Servlet Filter + MDC y logging JSON, sin tracing distribuido

## Status
Accepted

## Context
BR-011 exige que cada error RFC 7807 incluya un traceId que coincida
exactamente con los logs de esa request, y el proveedor de autorización
(Sección 7.1) recibe X-Trace-Id para correlacionar sus logs con los
nuestros. El sistema es un monolito de un solo nodo, sin grafo de
servicios que justifique tracing distribuido (Micrometer Tracing, OTel).

## Decision
- `TraceIdFilter` (máxima precedencia) genera o respeta el `X-Trace-Id`
  entrante, lo pone en el MDC durante toda la request, lo devuelve en el
  header de TODAS las respuestas y lo limpia en `finally` (los hilos de
  Tomcat se reutilizan).
- Un valor entrante solo se respeta si cumple `^[A-Za-z0-9-]{1,64}$`: es
  input no confiable que termina en los logs (prevención de log injection).
- `TraceContext` es el punto único de acceso: `GlobalExceptionHandler` lo
  añade a cada ProblemDetail y `AuthorizationHttpAdapter` lo propaga al
  proveedor. Funciona porque todo el procesamiento es síncrono en el mismo
  hilo; código asíncrono futuro necesitaría propagar el MDC explícitamente.
- Logs: texto legible con `[traceId=...]` fuera del perfil docker; JSON
  (logstash-logback-encoder 7.4) en el perfil docker, exponiendo solo la
  clave `traceId` del MDC.
- Errores conocidos se registran en WARN sin stack trace; errores
  desconocidos (catch-all → 500) en ERROR con stack trace. Al cliente solo
  llega un `detail` seguro; los mensajes internos van al log, saneados.
- Conflicto BR-002 vs BR-011: una respuesta replicada por idempotencia
  devuelve el body cacheado EXACTO (BR-002 prevalece). Ese body no lleva
  el traceId de la request actual; la correlación se mantiene por el
  header `X-Trace-Id` y los logs.

## Consequences
- Correlación verificada automáticamente: `TraceIdLogCorrelationTest`
  compara el traceId generado de la respuesta con el MDC del evento de log.
- Contrato por código verificado en `ErrorContractTest`; ejemplos reales
  en `docs/api/error-examples.md`, regenerables con un script.
- Sin trazas entre procesos: si el sistema se dividiera en servicios,
  habría que revisar esta decisión.

  ## Update (Fase 11): health y métricas
- Actuator expone únicamente `health` y `prometheus` (endpoints públicos,
  sin autenticación, Sección 6 del contexto). `env`, `beans`, etc. no se
  exponen porque pueden filtrar configuración.
- `/actuator/health` muestra el estado de cada componente (`db`, `redis`,
  `circuitBreakers`) pero no sus detalles (`show-details: never`): sin
  versiones ni estado interno del circuito.
- Circuito OPEN -> componente `circuitBreakers` DOWN
  (`allowHealthIndicatorToFail: true`, explícito). HALF_OPEN -> UNKNOWN,
  que no degrada el estado agregado.
- Semántica del agregado: cualquier dependencia caída -> DOWN (HTTP 503),
  según el contexto. El health responde "¿están sanas mis dependencias?",
  NO "¿puedo atender tráfico?": sin Redis el sistema sigue siendo correcto
  (ADR-0003) y con el circuito abierto las transferencias INTERNAL siguen
  funcionando. Separar liveness/readiness solo tendría sentido con un
  orquestador (fuera de alcance: Docker Compose, ADR-0006).
- Métricas vía Micrometer + Prometheus, con el tag común
  `application=payment-gateway-engine`. Las métricas de Resilience4j llegan
  por `resilience4j-micrometer`. No se añaden métricas de negocio propias.
- Reconfirmado: sin tracing distribuido (RISK-011).
- En tests, Spring Boot desactiva la exportación de métricas por defecto;
  `HealthEndpointOutageIT` la reactiva con `@AutoConfigureObservability`.