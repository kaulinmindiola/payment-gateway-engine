# ADR-0010: Catálogo providers/external_banks como excepción de solo lectura

## Status
Accepted

## Context
El proyecto define explícitamente (`CON-008`) que no expone endpoints de
gestión CRUD sin necesidad real de negocio -- consistente con la ausencia
de endpoints de creación de usuarios (`ADR-0008`) o cambio de
`AccountStatus` (`CON-010`). Sin embargo, `ExternalTransferHandler`
(Fase 8) necesita validar `BR-013` (banco/proveedor destino activo)
contra datos de catálogo reales, y ese catálogo debe ser consultable
por un cliente externo antes de intentar una transferencia (para
construir el request con `targetProviderId`/`targetBankId` válidos).

## Decision
- `GET /api/v1/external-banks` y `GET /api/v1/external-banks/{id}` son
  de solo lectura, sin autenticación (dato de referencia, no un recurso
  del usuario) -- excepción explícita y acotada a `CON-008`.
- La respuesta es denormalizada: incluye `providerCode` inline
  (resuelto en `application/usecase/ExternalBankView`, sin alterar
  `domain/model/ExternalBank`, que sigue referenciando `Provider` solo
  por id). Evita que el cliente tenga que hacer una segunda consulta a
  un endpoint de `providers` que no existe -- no se expone
  `GET /providers` por separado, ya que ningún caso de uso lo requiere
  de forma aislada (mismo criterio de "sin capacidad sin consumidor
  real" aplicado en fases anteriores).
- Sin paginación: el catálogo es un dato de referencia acotado
  (providers/bancos simulados para demo), no un recurso transaccional
  de crecimiento no acotado como `Transaction` (que sí pagina, Fase 9).
- `currency` en la respuesta es metadato puramente informativo -- no
  participa en ninguna validación de `amount` (multi-moneda real sigue
  excluida, Sección 19 del contexto).
- Sin filtrado por `status` en la consulta: un banco/provider `INACTIVE`
  sigue siendo visible en el catálogo. El rechazo por estado inactivo
  ocurre en `BR-013` (Fase 8), al intentar una transferencia real -- no
  en la consulta de solo lectura.
- Ambas tablas se siembran vía migraciones Flyway aisladas
  (`db/seed/docker/`, fuera del árbol que escanea el perfil por
  defecto) -- nunca vía API, mismo patrón que `users` (`ADR-0008`).

## Consequences
- `ExternalTransferHandler` (Fase 8) puede reutilizar directamente
  `ProviderRepositoryPort`/`ExternalBankRepositoryPort` para `BR-013`,
  sin necesitar un nuevo puerto de lectura.
- Costo aceptado: el catálogo completo se carga en una sola respuesta
  sin límite -- aceptable dado que es un dataset de demo, fijo y
  pequeño (no un requisito de escala real, Sección 3 del contexto).