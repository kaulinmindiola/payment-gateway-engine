# ADR-0009: Strategy pattern para handlers de transferencia por transfer_type

## Status
Accepted

## Context
El proyecto soporta dos tipos de transferencia con reglas y dependencias
muy distintas: INTERNAL (dos cuentas propias del sistema, sin llamada
externa, sin Resilience4j) y EXTERNAL (una cuenta propia + un proveedor
externo simulado vía HTTP, con circuit breaker/retry). Mezclar ambos
flujos en un único método con condicionales anidados degradaría
rápidamente la legibilidad y dificultaría razonar sobre las reglas de
cada rama de forma aislada.

## Decision
- `TransferHandler` (`application/handler/`) define el contrato común:
  `Transaction handle(TransferCommand command)`.
- `InternalTransferHandler` (Fase 5) implementa la rama INTERNAL: valida
  `BR-003/006/007`, aplica `LockOrderPolicy`, ejecuta débito+crédito+registro
  en una única transacción ACID (`BR-004`). Sin `AuthorizationPort`.
- `ExternalTransferHandler` (Fase 8) implementará la rama EXTERNAL con el
  mismo contrato, añadiendo `AuthorizationPort` + Resilience4j.
- `TransferMoney` (`application/usecase/`) sigue siendo el único punto de
  entrada del caso de uso. Como Spring no puede resolver de forma
  ambigua una inyección genérica de `TransferHandler` cuando existan dos
  implementaciones concretas, `TransferMoney` inyecta cada handler por
  su TIPO CONCRETO (`InternalTransferHandler` desde Fase 5;
  `ExternalTransferHandler` se añade como segundo parámetro en Fase 8) y
  despacha manualmente vía `switch` sobre `TransferType` -- no se usa
  `Map<TransferType, TransferHandler>` ni resolución dinámica por nombre
  de bean, evitando complejidad no justificada para solo dos variantes.
- Mientras `ExternalTransferHandler` no exista, `transfer_type=EXTERNAL`
  responde `400` vía `UnsupportedTransferTypeException` (scaffolding
  temporal, no una regla de negocio -- ver Fase 5, Paso 4).

## Consequences
- Cada handler es testeable de forma aislada con sus propios fakes
  (`InternalTransferHandlerTest`), sin necesidad de simular reglas de la
  otra rama.
- Añadir un tercer `TransferType` en el futuro (fuera de alcance actual)
  requeriría: una nueva implementación de `TransferHandler`, un nuevo
  parámetro de constructor en `TransferMoney`, y una nueva rama en el
  `switch` -- cambio localizado, no un refactor de todo el flujo.
- Costo aceptado: `TransferMoney` conoce los tipos concretos de los
  handlers (no solo la interfaz), una desviación menor del Strategy
  pattern "puro" -- justificada por ser solo dos variantes fijas y
  conocidas de antemano (no un plugin system extensible).