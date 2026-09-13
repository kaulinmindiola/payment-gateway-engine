# Payment Gateway Engine — Context Maestro

> **Documento vivo.** Fuente inicial de verdad antes del desarrllo de las etapas posteriores de Arquitectura, Diseño Técnico (SDD), Modelo de Datos, OpenAPI, Seguridad, Testing y Deployment.
> Última actualización: **2026-09-11**
> Estado general del proyecto: **Product Definition cerrada · Requirements (SRS) cerrada · Architecture cerrada**

> **Nota de actualización (2026-09-11):** el proyecto se implementa sobre **Spring Boot 4.1.x** (línea activa), no sobre Spring Boot 3.x como se asumía originalmente. Motivo: la línea 3.5.x dejó de recibir parches de seguridad gratuitos desde 2026-06. Detalle completo, alternativas evaluadas y mitigación de la única fricción real (ausencia de starter `resilience4j-spring-boot4`) en `adr/0002-*.md`, sección "Nota de implementación". Ver `CON-001` (Sección 9) y `AQ-001` (Sección 21).

---

## 0. Cómo leer este documento

Cada afirmación está clasificada según su nivel de certeza:

| Etiqueta | Significado |
|---|---|
| **CONFIRMED** | Proporcionado o aprobado explícitamente por el dueño del producto. |
| **INFERRED** | Inferido razonablemente a partir de lo confirmado. |
| **PROPOSED** | Recomendado por el copiloto de ingeniería; requiere aprobación. |
| **ASSUMED** | Supuesto temporal, de bajo riesgo, usado para no bloquear el avance. |
| **PENDING** | Desconocido, a definir. |

Cada decisión técnica (`DEC-xxx`) tiene un estado: **ACCEPTED** (aprobada) o **PROPOSED** (pendiente de revisión). Ninguna decisión `PROPOSED` debe tratarse como definitiva en etapas posteriores sin haber sido revisada primero.

---

## 1. Resumen ejecutivo

**Payment Gateway Engine** es un backend en Java 21 / Spring Boot 4 que simula un core transaccional bancario. Su propósito **no es ser un producto de negocio**, sino desarrollar ingeniería de nivel producción en el dominio más intolerante a errores: dinero. El proyecto prioriza correctitud, consistencia transaccional, control de concurrencia, idempotencia real y comportamiento ante fallos parciales, por encima de la cantidad de funcionalidades.

---

## 2. Problema y propuesta de valor

**Problema (CONFIRMED):** construir un sistema que procese transferencias entre cuentas sin perder, duplicar o alterar incorrectamente un centavo, incluso bajo concurrencia alta, fallos de red y reintentos.

**Propuesta de valor (CONFIRMED):** un backend de referencia que demuestre, con evidencia verificable (tests reales, no mocks superficiales), que esos problemas fueron resueltos deliberadamente y no evitados.

**Distinción problema/solución:** el problema es *"garantizar consistencia financiera bajo concurrencia y fallos"*; Pessimistic Locking, Redis, Resilience4j, etc. son **soluciones propuestas**, no el problema en sí — se registran como `CON-001`/`CON-003` porque ya fueron fijadas como restricciones tecnológicas del proyecto, no porque sean la única solución posible.

---

## 3. Actores

| Actor | Rol |
|---|---|
| Cliente API | Llamador identificado por `X-User-Id` (identidad simulada, sin sistema de usuarios real). |
| Cuenta (origen/destino) | Recurso de dominio, no un actor humano. |
| Proveedor externo de autorización | Sistema externo simulado (WireMock) que aprueba/rechaza/falla la autorización de una transferencia. |
| Operador/DevOps | Consume endpoints de observabilidad (`/actuator/health`, métricas) — actor operacional, no de negocio. |

---

## 4. Objetivos y criterios de éxito

**Objetivos (CONFIRMED):** Construir el sistema con buenas practicas de ingenieria usando de Java 21, Spring Boot 4, Clean/Hexagonal Architecture, PostgreSQL, locking pesimista, concurrencia, Redis, idempotencia, Resilience4j, testing de integración/concurrencia con Testcontainers, y Docker.

**Criterios de éxito (PROPOSED — no existían en el pitch original):**

- CS-01: El test de concurrencia (20 y 50 hilos) produce saldo final exacto, sin saldo negativo y sin transacciones duplicadas, de forma reproducible (no flaky).
- CS-02: Ninguna combinación de timeout + retry + circuit breaker produce una transferencia duplicada, verificado con un test de integración dedicado.
- CS-03: El dominio (`domain/`) no tiene ninguna dependencia de Spring/JPA, verificado automáticamente (ArchUnit, `DEC-018`).
- CS-04: El entorno completo (incluyendo el proveedor externo simulado) se levanta con un solo `docker compose up`.

---

## 5. Alcance

### 5.1 Dentro de alcance (CONFIRMED)

- Crear cuenta, consultar cuenta, transferir dinero entre cuentas.
- Consultar el estado de una transacción por ID (`DEC-010`, nuevo).
- Idempotencia vía Redis + `X-Idempotency-Key`.
- Locking pesimista + orden determinístico anti-deadlock.
- Transacciones ACID en PostgreSQL.
- Integración HTTP simulada con Resilience4j (Circuit Breaker + Retry).
- Auditoría inmutable (`transaction_logs`).
- Identidad mínima simulada (`X-User-Id` + `owner_id`) con autorización por ownership.
- Observabilidad básica (Actuator, logging estructurado).

### 5.2 Fuera de alcance (CONFIRMED + ampliado)

- Frontend, apps móviles, multi-moneda, conversión de divisas.
- Integración real con proveedores financieros.
- OAuth2/JWT, gestión completa de usuarios, KYC/AML.
- Reportes, exportaciones masivas, sistema contable completo.
- Microservicios distribuidos.
- **(Nuevo, `CON-008`)** Endpoints de listado/paginación (`GET /accounts`, `GET /transactions` como colección) — cada recurso se consulta únicamente por ID.
- **(Nuevo, `CON-009`)** Rate limiting / throttling.
- **(Nuevo, `CON-010`)** Endpoint para cambiar `AccountStatus` — `BLOCKED`/`CLOSED` solo se fijan vía fixtures de test o acceso directo a BD.

---

## 6. Casos de uso

### UC-01 — Crear cuenta
- **Actor:** Cliente API.
- **Precondición:** header `X-User-Id` presente.
- **Flujo principal:** el cliente envía saldo inicial → se crea `Account` con `owner_id = X-User-Id`, `status = ACTIVE`, `balance = saldo inicial`.
- **Flujos alternativos:** `X-User-Id` ausente → 400. Saldo inicial negativo → 400.
- **Postcondición:** cuenta persistida, `201 Created`.

### UC-02 — Consultar cuenta
- **Actor:** Cliente API (debe ser owner).
- **Precondición:** header `X-User-Id` presente.
- **Flujo principal:** `GET /accounts/{id}` → si `owner_id == X-User-Id` → `200 OK` con saldo y estado.
- **Flujos alternativos:** cuenta ajena → `403`. Cuenta inexistente → `404`. Header ausente → `400`.
- **Regla de negocio:** `BR-008`.

### UC-03 — Transferir dinero
- **Actor:** Cliente API (debe ser owner de la cuenta origen).
- **Precondición:** `X-User-Id`, `X-Idempotency-Key` presentes; cuentas origen y destino existentes.
- **Flujo principal:**
  1. Validar idempotencia en Redis (nuevo / duplicado / en curso).
  2. Validar ownership de origen (`BR-006`), estados `ACTIVE` (`BR-003`), `source ≠ target` (`BR-007`), saldo suficiente.
  3. Adquirir locks en orden determinístico (`DEC-001`).
  4. Ejecutar débito + crédito + registro de `Transaction` en una transacción ACID.
  5. Invocar `authorization-provider` (protegido por Resilience4j).
  6. Persistir resultado final y actualizar Redis.
- **Flujos alternativos:** saldo insuficiente (`422`), cuenta inactiva (`422`), self-transfer (`422`), idempotency key `IN_PROGRESS` (`409`), idempotency key en estado terminal (devuelve resultado guardado), proveedor externo `DECLINED` (`201`, `status=FAILED`, `BR-010`), proveedor externo no disponible tras reintentos (`503`).
- **Postcondición:** `Transaction` + `transaction_logs` persistidos de forma consistente con el saldo real.

### UC-04 — Consultar transacción *(nuevo, `DEC-010`, PROPOSED)*
- **Actor:** Cliente API (owner de origen o destino).
- **Justificación:** sin esto, la auditoría (`transaction_logs`) es efectivamente inalcanzable vía API, y un cliente no tiene forma estándar de reconciliar un timeout de red del lado del cliente.
- **Flujo principal:** `GET /transactions/{id}` → si `X-User-Id` coincide con owner de origen o destino → `200 OK`.
- **Flujos alternativos:** sin relación con la transacción → `403`. Inexistente → `404`.
- **Regla de negocio:** `BR-009`.

---

## 7. Funcionalidades / superficie de API (resumen)

| Método | Ruta | Auth | Notas |
|---|---|---|---|
| POST | `/api/v1/accounts` | `X-User-Id` | UC-01 |
| GET | `/api/v1/accounts/{id}` | `X-User-Id` (ownership) | UC-02 |
| POST | `/api/v1/payments/transfer` | `X-User-Id` + `X-Idempotency-Key` | UC-03 |
| GET | `/api/v1/transactions/{id}` | `X-User-Id` (ownership) | UC-04 — **PROPOSED** |
| GET | `/actuator/health` | ninguna | Operacional, no de negocio |
| GET | `/actuator/prometheus` | ninguna | Métricas — **PROPOSED**, `DEC-014` |

Formato de error estándar (**PROPOSED**, `DEC-011`): RFC 7807 Problem Details — `{type, title, status, detail, instance, traceId}` en toda la API, incluyendo `traceId` para correlacionar con logs.

---

## 8. Reglas de negocio

| ID | Regla | Estado |
|---|---|---|
| BR-001 | El balance de una cuenta nunca puede ser negativo. | CONFIRMED (inferido) |
| BR-002 | Idempotency key en estado terminal ⇒ se devuelve el resultado almacenado, sin re-ejecutar. | CONFIRMED |
| BR-003 | Ambas cuentas (origen y destino) deben estar `ACTIVE` para participar en una transferencia. | CONFIRMED |
| BR-004 | Débito, crédito y registro de transacción son atómicos (todo o nada). | CONFIRMED |
| BR-005 | PostgreSQL es la única fuente de verdad; Redis nunca la reemplaza. | CONFIRMED |
| BR-006 | El llamador solo puede transferir desde una cuenta de la que es owner; el destino puede pertenecer a cualquiera. | CONFIRMED |
| BR-007 | `source_account_id == target_account_id` ⇒ transferencia rechazada. | CONFIRMED |
| BR-008 | `GET /accounts/{id}` solo accesible por su owner. | CONFIRMED |
| BR-009 | `GET /transactions/{id}` solo accesible por el owner de origen o destino. | PROPOSED |
| BR-010 | `DECLINED` del proveedor externo es un resultado de negocio válido: `201 Created`, `Transaction.status = FAILED`, `reason = DECLINED`. | PROPOSED |
| BR-011 | Convención de errores HTTP: `400` input inválido · `403` ownership · `404` no encontrado · `409` idempotency key `IN_PROGRESS` · `422` regla de negocio violada (saldo insuficiente, cuenta inactiva, self-transfer) · `503` dependencia externa no disponible. | PROPOSED |

---

## 9. Restricciones

| ID | Restricción | Estado |
|---|---|---|
| CON-001 | Stack fijo: Java 21, Spring Boot 4.1.x, JPA, PostgreSQL, Redis, Resilience4j, JUnit 5, Testcontainers, Docker. Versión de Spring Boot confirmada en `AQ-001` (revisa la suposición original de `3.2+`); integración de Resilience4j vía módulos core con composición funcional, no vía starter `-spring-bootN` (no existe aún para Boot 4) — ver `adr/0002-*.md`, "Nota de implementación". | CONFIRMED |
| CON-002 | Arquitectura Hexagonal/Clean; domain sin dependencias de framework. | CONFIRMED |
| CON-003 | Concurrencia: Pessimistic Locking como estrategia principal. | CONFIRMED |
| CON-004 | Sin frontend, multi-moneda, auth compleja, microservicios, gestión de usuarios, KYC/AML. | CONFIRMED |
| CON-005 | Nunca `float`/`double` para dinero. | CONFIRMED |
| CON-006 | `X-User-Id` es un identificador simulado, no autenticación criptográfica real. | CONFIRMED |
| CON-007 | Proyecto de alcance portafolio; NFRs de performance/disponibilidad son indicativos, no SLAs productivos. | ASSUMED |
| CON-008 | Sin endpoints de listado/paginación. | PROPOSED |
| CON-009 | Sin rate limiting / throttling. | PROPOSED |
| CON-010 | Sin endpoint para cambiar `AccountStatus`. | PROPOSED |

---

## 10. Atributos de calidad / Requisitos no funcionales

Cada NFR incluye un criterio verificable — se evita explícitamente todo término ambiguo sin métrica.

**Performance**
- NFR-PERF-01 (PROPOSED, indicativo — ver `CON-007`): en un test local de 50 transferencias concurrentes sobre el mismo par de cuentas, el sistema procesa todas sin corrupción de saldo; el tiempo total se mide y se documenta como referencia, no como SLA.

**Escalabilidad**
- NFR-SCALE-01 (PROPOSED): la aplicación es *stateless* a nivel de proceso — ningún estado de negocio (locks, resultados de idempotencia) vive en memoria local; todo persiste en PostgreSQL o Redis, permitiendo múltiples réplicas sin sticky sessions. Verificable por revisión de código / ArchUnit.

**Disponibilidad / Resiliencia**
- NFR-AVAIL-01 (CONFIRMED, ya implícito): ante caída temporal de Redis, el sistema sigue aceptando transferencias apoyándose en el `UNIQUE` constraint de `idempotency_key` en PostgreSQL (`DEC-002`) como red de seguridad.
- NFR-AVAIL-02 (PROPOSED, `DEC-012`): Circuit Breaker — `slidingWindowSize=10`, `failureRateThreshold=50%`, `waitDurationInOpenState=10s`, `permittedNumberOfCallsInHalfOpenState=3` (configurable vía properties).
- NFR-AVAIL-03 (PROPOSED, `DEC-012`): Retry — `maxAttempts=3`, backoff exponencial desde `200ms` (multiplicador x2), aplicado solo a errores transitorios (timeout, 5xx), nunca a resultados de negocio (`DECLINED`).
- NFR-AVAIL-04 (PROPOSED, `DEC-013`): timeouts explícitos hacia `authorization-provider`: `connectTimeout=1s`, `readTimeout=2s`.

**Seguridad**
- NFR-SEC-01 (PROPOSED): toda entrada se valida con Bean Validation (JSR-380) antes de llegar al dominio.
- NFR-SEC-02 (PROPOSED, `DEC-017`): sin secretos en código fuente ni imágenes Docker; credenciales vía variables de entorno / `.env` no versionado.
- NFR-SEC-03 (ASSUMED): dependencias gestionadas con versiones fijas (BOM); opcionalmente `OWASP Dependency-Check` — prioridad P3, no bloqueante.

**Observabilidad**
- NFR-OBS-01 (PROPOSED, `DEC-014`): Spring Boot Actuator con health indicators custom para PostgreSQL, Redis y estado del Circuit Breaker.
- NFR-OBS-02 (PROPOSED, `DEC-014`): logging estructurado (JSON) con `traceId`/`correlationId` propagado por request en cada log line y en cada respuesta de error.

**Mantenibilidad / Testabilidad**
- NFR-MAINT-01 (PROPOSED, `DEC-018`): `domain/` sin anotaciones de Spring/JPA, verificado automáticamente con **ArchUnit** como parte del test suite.
- NFR-TEST-01 (PROPOSED, `DEC-015`): cobertura de línea ≥80% en `domain` + `application` (JaCoCo); 100% de los casos de error de negocio de `BR-011` cubiertos por tests.

**Auditabilidad / Retención**
- NFR-AUDIT-01 (PROPOSED, `DEC-016`): `transactions` y `transaction_logs` no se purgan (sin TTL de borrado) — dataset de demostración, no sujeto a regulación real.

---

## 11. Modelo de datos (borrador — se formaliza en la etapa DATA)

**accounts**

| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| owner_id | VARCHAR, indexado | identificador simulado, sin FK a tabla de usuarios |
| balance | NUMERIC(19,2) | nunca negativo (`BR-001`) |
| status | VARCHAR / ENUM | `ACTIVE`, `BLOCKED`, `CLOSED` |
| version | BIGINT | reservado para optimistic locking secundario / auditoría de cambios |
| created_at / updated_at | TIMESTAMP | |

**transactions**

| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| source_account_id | UUID (FK → accounts) | |
| target_account_id | UUID (FK → accounts) | |
| amount | NUMERIC(19,2) | > 0 |
| idempotency_key | VARCHAR, **UNIQUE** | `DEC-002` — segunda barrera además de Redis |
| status | VARCHAR / ENUM | `PENDING`, `COMPLETED`, `FAILED` |
| failure_reason | VARCHAR, nullable | `INSUFFICIENT_FUNDS`, `DECLINED`, `EXTERNAL_TIMEOUT`, `EXTERNAL_UNAVAILABLE`, ... |
| created_at / updated_at | TIMESTAMP | |

**transaction_logs** (append-only, inmutable)

| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| transaction_id | UUID (FK → transactions) | puede haber múltiples filas por transacción (ciclo de vida) |
| status | VARCHAR / ENUM | `PENDING`, `APPROVED`, `DECLINED`, `REVERSED` |
| detail | TEXT, nullable | |
| created_at | TIMESTAMP | sin `updated_at` — es inmutable |

> **Nota de consistencia:** `REVERSED` existe en `transaction_logs` para extensibilidad futura, pero **no hay ningún caso de uso ni endpoint en este alcance que lo dispare** (`ASM-002`, ver backlog). Se documenta para que no se lea como una funcionalidad implementada.

---

## 12. Idempotencia — diseño Redis

- **Clave:** `idempotency:transfer:{X-Idempotency-Key}`
- **Valor (JSON):** `{status: IN_PROGRESS|COMPLETED|FAILED, transactionId, httpStatus, responseBody, createdAt}`
- **TTL:** 24h, configurable (`DEC-003`)
- **Atomicidad:** `SET key value NX` para reclamar `IN_PROGRESS`; al finalizar, se sobrescribe con el resultado terminal.
- **Concurrencia:** si la clave ya está `IN_PROGRESS`, la segunda request recibe `409 Conflict` (`BR-011`).
- **Fallback:** si Redis no está disponible, el `UNIQUE` constraint de PostgreSQL (`DEC-002`) evita la duplicación real, aunque se pierda la respuesta cacheada instantánea (`NFR-AVAIL-01`).

---

## 13. Concurrencia y locking

- Estrategia principal: `@Lock(LockModeType.PESSIMISTIC_WRITE)` dentro de un método `@Transactional` (`CON-003`).
- **Orden anti-deadlock (`DEC-001`, ACCEPTED):** los locks se adquieren siempre en orden ascendente de `account.id`, sin importar cuál es origen y cuál destino. Esto resuelve determinísticamente transferencias cruzadas A→B / B→A concurrentes.
- `version` en `Account` queda reservado para optimistic locking como mecanismo secundario/futuro, no como estrategia principal.

---

## 14. Resiliencia — configuración Resilience4j (PROPOSED, `DEC-012`)

```
circuitbreaker:
  slidingWindowType: COUNT_BASED
  slidingWindowSize: 10
  failureRateThreshold: 50
  waitDurationInOpenState: 10s
  permittedNumberOfCallsInHalfOpenState: 3

retry:
  maxAttempts: 3
  waitDuration: 200ms
  backoffMultiplier: 2
  retryOnExceptions: [timeout, 5xx]
  # nunca reintenta sobre una respuesta de negocio (DECLINED)
```

---

## 15. Seguridad

- Autorización por *ownership*, no autenticación real (`CON-006`).
- Matriz de control: ver `BR-006`, `BR-008`, `BR-009`, `BR-011`.
- Validación de entrada con Bean Validation (`NFR-SEC-01`).
- Secretos vía variables de entorno, nunca en el repo (`NFR-SEC-02`).
- **Riesgo aceptado conscientemente (`RISK-006`):** devolver `403` en vez de `404` para una cuenta ajena permite inferir que el ID existe (account enumeration). Aceptable para un proyecto de demostración sin superficie de ataque real.

---

## 16. Observabilidad (PROPOSED, `DEC-014`)

- Actuator habilitado: `/actuator/health` (con indicadores custom de DB, Redis, Circuit Breaker), métricas vía Micrometer.
- Logging estructurado (JSON) con `traceId` en cada línea y en cada respuesta de error (RFC 7807, `DEC-011`).

---

## 17. Estrategia de testing (resumen — se detalla en la etapa TESTING)

- JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL y Redis reales).
- Test de concurrencia obligatorio: 20 y 50 hilos, saldo final exacto, sin duplicados.
- Cobertura mínima `domain`+`application` ≥80% (`NFR-TEST-01`).
- Verificación arquitectónica automática con ArchUnit (`DEC-018`).
- `authorization-provider`: WireMock in-process (JUnit5 extension) en tests de integración; WireMock standalone en `docker-compose` para el entorno "real" — mismos stub mappings versionados en el repo para evitar drift (`RISK-007`).

---

## 18. Arquitectura (dirección ya decidida — detalle formal en etapa ARCHITECTURE)

```
Payment Gateway Engine (Spring Boot)
   domain/          -> lógica pura, sin dependencias de framework
   application/      -> casos de uso (CreateAccount, GetAccount, TransferMoney, GetTransaction)
   infrastructure/    -> controllers, JPA, Redis, cliente HTTP, Resilience4j, config

   AuthorizationPort (puerto de dominio)
        |
        HTTP Adapter
        |
        v
   Authorization Provider (WireMock) — servicio externo simulado
```

El dominio no conoce la existencia de WireMock; solo conoce `AuthorizationPort` (`DEC-009`).

---

## 19. Despliegue — topología Docker Compose

```
application  ──┬─→ PostgreSQL
               ├─→ Redis
               └─→ authorization-provider (WireMock)
```

4 servicios (`DEC-005`, ACCEPTED — supera el alcance original de 3 servicios del pitch inicial).

---

## 20. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| RISK-001 | Deadlocks por orden inconsistente de locks. | `DEC-001` |
| RISK-002 | Duplicación por timeout+retry tras procesamiento externo real. | `BR-002`, timeouts explícitos (`DEC-013`) |
| RISK-003 | Redis caído ⇒ doble ejecución. | `DEC-002` |
| RISK-004 | Scope creep. | `CON-004`, `CON-008`, `CON-009`, `CON-010` |
| RISK-005 | Tests de concurrencia flaky. | Sincronización explícita (`CountDownLatch`) |
| RISK-006 | `403` revela existencia de cuenta ajena (enumeration). | Aceptado conscientemente |
| RISK-007 | Drift entre WireMock de test y de docker-compose. | Stub mappings compartidos/versionados |
| RISK-008 | Sobre-especificar SLAs no realistas. | `CON-007` — NFRs de performance marcados como indicativos |

---

## 21. Engineering Backlog — estado completo

### Decisiones

| ID | Decisión | Estado |
|---|---|---|
| DEC-001 | Orden de locks por `account.id` ascendente. | ACCEPTED |
| DEC-002 | `UNIQUE` constraint sobre `idempotency_key`. | ACCEPTED |
| DEC-003 | `BigDecimal(19,2)` HALF_EVEN + TTL Redis 24h. | ACCEPTED |
| DEC-004 | Ownership mínimo vía `owner_id` + `X-User-Id`. | ACCEPTED |
| DEC-005 | Docker Compose con 4 servicios. | ACCEPTED |
| DEC-006 | `AccountStatus` = ACTIVE/BLOCKED/CLOSED. | ACCEPTED |
| DEC-007 | `GET /accounts/{id}` valida ownership. | ACCEPTED |
| DEC-008 | Transfer requiere ownership solo de origen. | ACCEPTED |
| DEC-009 | `authorization-provider` = WireMock standalone. | ACCEPTED |
| DEC-010 | Nuevo endpoint `GET /transactions/{id}`. | **PROPOSED** |
| DEC-011 | Formato de error RFC 7807. | **PROPOSED** |
| DEC-012 | Config concreta Resilience4j. | **PROPOSED** |
| DEC-013 | Timeouts HTTP hacia `authorization-provider`. | **PROPOSED** |
| DEC-014 | Observabilidad (Actuator + logging + traceId). | **PROPOSED** |
| DEC-015 | Cobertura mínima de tests (JaCoCo ≥80%). | **PROPOSED** |
| DEC-016 | Retención de datos sin purga. | **PROPOSED** |
| DEC-017 | Gestión de secretos vía variables de entorno. | **PROPOSED** |
| DEC-018 | ArchUnit para verificar Hexagonal Architecture. | **PROPOSED** |

### Open Questions restantes (no bloqueantes)

| ID | Pregunta | Prioridad |
|---|---|---|
| Q-007 | ¿Confirmar `422` como código para violaciones de regla de negocio (vs. `409`)? | P2 |
| Q-008 | Contrato exacto request/response con `authorization-provider` para seleccionar escenario (APPROVED/DECLINED/TIMEOUT/TEMPORARY_FAILURE). | P2 — se resuelve en la etapa API/OpenAPI |

### Assumptions

| ID | Supuesto |
|---|---|
| AQ-001 | Versión de Spring Boot del proyecto. **Confirmado 2026-09-11:** Spring Boot 4.1.x (línea activa, soporte OSS vigente hasta 2027-07). Descarta 3.5.x por fin de soporte gratuito desde 2026-06. Reemplaza el supuesto original (`ASSUMED`, "3.2+") registrado en `ADR-0002`. Ver `adr/0002-*.md`, "Nota de implementación", para la mitigación asociada (ausencia de starter `resilience4j-spring-boot4`). |
| ASM-001 | Moneda única implícita, sin campo `currency`. |
| ASM-002 | `REVERSED` en `transaction_logs` reservado para extensibilidad futura; sin endpoint que lo dispare en este alcance. |
| ASM-003 | `amount` de transferencia debe ser estrictamente positivo. |
| ASM-004 | `X-User-Id` ausente ⇒ `400 Bad Request`. |
| ASM-005 | Toda cuenta nueva se crea en estado `ACTIVE`. |
| ASM-006 | `X-User-Id` sin validación de formato más allá de "no vacío". |

---

## 22. Roadmap de artefactos siguientes

```
Context Maestro (este documento)
   → SRS (REQ-xxx formales, trazables a UC/BR/CON/NFR)
   → ADRs formales para DEC-010..DEC-018 (una vez revisadas)
   → SDD/Architecture (contenedores, componentes, diagramas C4)
   → Data Model definitivo + DDL
   → OpenAPI (accounts, payments/transfer, transactions)
   → Threat Model
   → Test Plan (REQUIREMENT → ACCEPTANCE CRITERIA → TEST)
   → Deployment Documentation (docker-compose.yml, README)
```

**Siguiente paso sugerido:** revisar en bloque `DEC-010` a `DEC-018` (o decir "continúa" para avanzar igualmente, dejándolas explícitamente como *PROPOSED — pendiente de ADR* en el SRS) y luego decir **"genera SRS"**.
