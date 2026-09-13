# Software Requirements Specification
## For Payment Gateway Engine

Version 1.1
Prepared by Kaulin Mindiola
2026-09-11

## Table of Contents
<!-- TOC -->
- [Software Requirements Specification](#software-requirements-specification)
  - [For Payment Gateway Engine](#for-payment-gateway-engine)
  - [Table of Contents](#table-of-contents)
  - [Revision History](#revision-history)
  - [1. Introduction](#1-introduction)
    - [1.1 Document Purpose](#11-document-purpose)
    - [1.2 Product Scope](#12-product-scope)
    - [1.3 Definitions, Acronyms, and Abbreviations](#13-definitions-acronyms-and-abbreviations)
    - [1.4 References](#14-references)
    - [1.5 Document Overview](#15-document-overview)
  - [2. Product Overview](#2-product-overview)
    - [2.1 Product Perspective](#21-product-perspective)
    - [2.2 Product Functions](#22-product-functions)
    - [2.3 Product Constraints](#23-product-constraints)
    - [2.4 User Characteristics](#24-user-characteristics)
    - [2.5 Assumptions and Dependencies](#25-assumptions-and-dependencies)
    - [2.6 Apportioning of Requirements](#26-apportioning-of-requirements)
  - [3. Requirements](#3-requirements)
    - [3.1 External Interfaces](#31-external-interfaces)
      - [3.1.1 User Interfaces](#311-user-interfaces)
      - [3.1.2 Hardware Interfaces](#312-hardware-interfaces)
      - [3.1.3 Software Interfaces](#313-software-interfaces)
    - [3.2 Functional](#32-functional)
    - [3.3 Quality of Service](#33-quality-of-service)
      - [3.3.1 Performance](#331-performance)
      - [3.3.2 Security](#332-security)
      - [3.3.3 Reliability](#333-reliability)
      - [3.3.4 Availability](#334-availability)
      - [3.3.5 Observability](#335-observability)
    - [3.4 Compliance](#34-compliance)
    - [3.5 Design and Implementation](#35-design-and-implementation)
      - [3.5.1 Installation](#351-installation)
      - [3.5.2 Build and Delivery](#352-build-and-delivery)
      - [3.5.3 Distribution](#353-distribution)
      - [3.5.4 Maintainability](#354-maintainability)
      - [3.5.5 Reusability](#355-reusability)
      - [3.5.6 Portability](#356-portability)
      - [3.5.7 Cost](#357-cost)
      - [3.5.8 Deadline](#358-deadline)
      - [3.5.9 Proof of Concept](#359-proof-of-concept)
      - [3.5.10 Change Management](#3510-change-management)
    - [3.6 AI/ML](#36-aiml)
  - [4. Verification](#4-verification)
  - [5. Appendixes](#5-appendixes)
<!-- TOC -->

## Revision History

| Name        | Date       | Reason For Changes                                                                                                                   | Version |
|-------------|------------|----------------------------------------------------------------------------------------------------------------------------------------|---------|
| Kaulin Mindiola  | 2026-09-08 | Baseline inicial de la SRS, derivada del Context Maestro. Bloque de decisiones DEC-010–DEC-018 revisado y aceptado; Q-007 y Q-008 resueltas. | 1.0     |
| Kaulin Mindiola  | 2026-09-11 | Corrección de versión: `CON-001` y §1.2 actualizados de Spring Boot 3 a Spring Boot 4.1.x (`AQ-001` confirmado, ver Context Maestro §21 y `ADR-0002`, "Nota de implementación"). Versión de Java sin cambios — se evaluó subir a Java 25 y se decidió mantener Java 21 (LTS con soporte vigente hasta 2028+). No se reabre ningún `REQ-xxx`. | 1.1     |

## 1. Introduction

Este documento especifica los requisitos del **Payment Gateway Engine**, un backend transaccional que simula un core bancario. El propósito de este SRS es definir de forma verificable **qué** debe hacer el sistema y **qué propiedades** debe cumplir, sirviendo de base para las etapas siguientes: Architecture/SDD, Modelo de Datos, OpenAPI, Threat Model, Test Plan y Deployment. La Sección 2 da contexto de producto, la Sección 3 contiene los requisitos verificables, la Sección 4 define cómo se verifica cada uno, y la Sección 5 referencia material de apoyo.

### 1.1 Document Purpose

Este SRS define el comportamiento esperado y las propiedades de calidad del Payment Gateway Engine, sin prescribir su implementación interna salvo donde una decisión técnica ya fue fijada como restricción del proyecto (Sección 2.3). Su audiencia principal es quien desarrolla el sistema (rol de arquitecto/desarrollador) y, secundariamente, cualquier revisor técnico que evalúe el proyecto como pieza de portafolio. El documento fuente de descubrimiento es el `payment-gateway-engine-context-maestro.md`, del cual este SRS deriva y formaliza.

### 1.2 Product Scope

**Payment Gateway Engine**, versión inicial (v1). Es un backend en Java 21 / Spring Boot 4 cuyo propósito **no es ser un producto de negocio**, sino demostrar ingeniería de nivel producción en el dominio más intolerante a errores: dinero. El sistema permite crear cuentas, consultarlas, transferir dinero entre ellas de forma idempotente y consistente incluso bajo concurrencia y fallos parciales, y consultar el estado de una transacción.

**Incluye:** gestión mínima de cuentas, transferencias con idempotencia real, locking pesimista con orden anti-deadlock, integración resiliente con un proveedor de autorización externo simulado, auditoría inmutable, y observabilidad básica.

**Excluye explícitamente:** frontend, multi-moneda, integración financiera real, autenticación criptográfica (OAuth2/JWT), KYC/AML, reportería, microservicios distribuidos, endpoints de listado/paginación, rate limiting, y cambio de estado de cuenta vía API.

### 1.3 Definitions, Acronyms, and Abbreviations

| Term | Definition |
|---|---|
| ACID | Atomicity, Consistency, Isolation, Durability — propiedades garantizadas por la transacción de PostgreSQL en cada transferencia. |
| API | Application Programming Interface. |
| ArchUnit | Framework de test usado para verificar reglas arquitectónicas de forma automática (p. ej., que `domain/` no dependa de Spring/JPA). |
| BR | Business Rule — regla de negocio identificada en este documento y en el Context Maestro. |
| Circuit Breaker | Patrón de resiliencia que interrumpe temporalmente las llamadas a una dependencia que falla repetidamente. |
| Idempotency Key | Valor provisto por el cliente (`X-Idempotency-Key`) que garantiza que una misma operación de transferencia no se ejecute más de una vez. |
| Owner | Identificador (`owner_id`) del `X-User-Id` que creó o controla una cuenta. |
| Pessimistic Locking | Estrategia de concurrencia que bloquea filas de base de datos durante la transacción para prevenir condiciones de carrera. |
| RFC 7807 | Estándar IETF ("Problem Details for HTTP APIs") usado como formato uniforme de error en toda la API. |
| SRS | Software Requirements Specification. |
| UI | User Interface. |
| WireMock | Herramienta usada para simular el `authorization-provider` externo en tests y en el entorno Docker Compose. |

### 1.4 References

| Referencia | Tipo | Ubicación |
|---|---|---|
| `payment-gateway-engine-context-maestro.md` | Normativa (fuente de descubrimiento y decisiones) | Repositorio del proyecto |
| `srs-template.md` | Normativa (estructura de este documento) | Repositorio del proyecto |
| RFC 7807 — Problem Details for HTTP APIs | Normativa (formato de error, `DEC-011`) | https://www.rfc-editor.org/rfc/rfc7807 |
| Documentación de Resilience4j | Informativa (configuración de Circuit Breaker/Retry, `DEC-012`) | https://resilience4j.readme.io |
| Documentación de ArchUnit | Informativa (verificación arquitectónica, `DEC-018`) | https://www.archunit.org |

### 1.5 Document Overview

La Sección 2 describe el producto (perspectiva, funciones de alto nivel, restricciones, usuarios, supuestos). La Sección 3 contiene los requisitos verificables organizados por interfaces externas, funcionalidad, calidad de servicio, cumplimiento y diseño/implementación; la Sección 3.6 (AI/ML) se mantiene por estructura de template pero no aplica a este producto. La Sección 4 mapea requisitos a método de verificación. La Sección 5 referencia material de apoyo sin duplicarlo.

## 2. Product Overview

### 2.1 Product Perspective

El Payment Gateway Engine es un producto **nuevo**, independiente, sin relación con una familia de productos previa. No forma parte de un sistema más grande ni tiene dependencias de negocio upstream/downstream reales. Es propiedad y responsabilidad de un único desarrollador (contexto de portafolio); no existen SLAs productivos formales (`CON-007`). Su única dependencia externa es un `authorization-provider` simulado (WireMock), consumido a través de un puerto de dominio (`AuthorizationPort`) según arquitectura hexagonal.

### 2.2 Product Functions

- Creación de cuentas con saldo inicial y estado `ACTIVE`.
- Consulta de cuenta restringida al propietario.
- Transferencia de dinero entre cuentas con idempotencia real (Redis + respaldo en PostgreSQL).
- Validación de reglas de negocio antes de mover dinero (ownership, estado de cuenta, saldo suficiente, no auto-transferencia).
- Ejecución atómica de débito, crédito y registro de transacción.
- Integración resiliente con un proveedor de autorización externo (Circuit Breaker + Retry + timeouts).
- Consulta del estado de una transacción por su propietario de origen o destino.
- Auditoría inmutable del ciclo de vida de cada transacción.
- Observabilidad operacional (salud de dependencias, métricas, logs correlacionados).

Los comportamientos detallados de cada función se especifican en la Sección 3.

### 2.3 Product Constraints

- **CON-001:** El sistema debe implementarse con Java 21, Spring Boot 4.1.x, JPA, PostgreSQL, Redis, Resilience4j, JUnit 5, Testcontainers y Docker. Versión de Spring Boot confirmada en `AQ-001` (Context Maestro); la integración de Resilience4j se realiza vía sus módulos core con composición funcional, no vía el starter `-spring-bootN` (inexistente para Boot 4 al cierre de este documento) — ver `ADR-0002`, "Nota de implementación".
- **CON-002:** El sistema debe seguir Arquitectura Hexagonal/Clean; el paquete `domain` no debe depender de ningún framework.
- **CON-003:** La estrategia principal de control de concurrencia debe ser Pessimistic Locking.
- **CON-004:** El sistema no debe incluir frontend, soporte multi-moneda, autenticación compleja, arquitectura de microservicios, ni gestión completa de usuarios/KYC/AML.
- **CON-005:** El sistema no debe usar `float`/`double` para representar valores monetarios.
- **CON-006:** `X-User-Id` es un identificador simulado; el sistema no debe implementar ni asumir autenticación criptográfica real.
- **CON-007:** Los requisitos de rendimiento y disponibilidad de este documento son indicativos de un proyecto de portafolio, no SLAs productivos.
- **CON-008:** El sistema no debe exponer endpoints de listado/paginación de colecciones (`GET /accounts`, `GET /transactions`); cada recurso se accede únicamente por ID.
- **CON-009:** El sistema no debe implementar rate limiting ni throttling.
- **CON-010:** El sistema no debe exponer un endpoint para cambiar `AccountStatus`; los estados `BLOCKED`/`CLOSED` solo se fijan vía fixtures de test o acceso directo a base de datos.

### 2.4 User Characteristics

| Clase de usuario | Descripción | Nivel de acceso | Frecuencia/objetivo |
|---|---|---|---|
| Cliente API | Consumidor técnico (desarrollador/integrador) que invoca la API vía HTTP, identificado por `X-User-Id`. No existe una persona final con interfaz gráfica. | Acceso restringido por ownership sobre sus propias cuentas y transacciones. | Crear cuentas, transferir dinero, consultar estado — uso programático, no interactivo. |
| Operador/DevOps | Rol operacional que monitorea el sistema en ejecución. | Acceso a endpoints de observabilidad (`/actuator/*`), sin acceso a datos de negocio vía esos endpoints. | Verificar salud del sistema y sus dependencias. |

No existen requisitos de accesibilidad o localización de UI, dado que el sistema no expone interfaz gráfica (`CON-004`).

### 2.5 Assumptions and Dependencies

| ID | Supuesto/Dependencia | Impacto si resulta falso |
|---|---|---|
| ASM-001 | Moneda única implícita; no existe campo `currency`. | Requeriría rediseño del modelo de `Transaction` y de las reglas de conversión. |
| ASM-002 | El estado `REVERSED` en `transaction_logs` está reservado para extensibilidad futura; ningún caso de uso de este alcance lo dispara. | Si se requiriera reversión, se necesitaría un nuevo caso de uso y reglas de negocio asociadas. |
| ASM-003 | El campo `amount` de una transferencia debe ser estrictamente positivo. | Sin esta validación, montos cero o negativos podrían invertir el sentido económico de una transferencia. |
| ASM-004 | La ausencia de `X-User-Id` siempre produce `400 Bad Request`. | Cambiaría el contrato de error para todos los endpoints autenticados. |
| ASM-005 | Toda cuenta nueva se crea en estado `ACTIVE`. | Afectaría el flujo de creación de cuentas y las pruebas de aceptación de UC-01. |
| ASM-006 | `X-User-Id` no se valida más allá de "no vacío" (sin formato específico). | Podría requerir validación adicional si se introdujera un esquema de identidad más estricto. |
| DEP-001 | Disponibilidad de PostgreSQL como única fuente de verdad de datos persistentes (`BR-005`). | Sin PostgreSQL disponible, el sistema no puede operar de forma consistente. |
| DEP-002 | Disponibilidad de Redis como acelerador de idempotencia (no como fuente de verdad). | Su ausencia degrada el sistema a un modo de respaldo vía constraint único en PostgreSQL (`NFR-AVAIL-01`), no lo detiene. |
| DEP-003 | El `authorization-provider` (WireMock) se comporta según los stubs versionados y compartidos entre entorno de test y `docker-compose` (`RISK-007`). | Un drift entre ambos entornos invalidaría resultados de test respecto al comportamiento real simulado. |

### 2.6 Apportioning of Requirements

Este SRS cubre una única entrega (no hay fases/iteraciones planificadas ni requisitos diferidos a una versión futura). Los elementos fuera de alcance (Sección 1.2, `CON-004`, `CON-008`, `CON-009`, `CON-010`) son exclusiones deliberadas de este producto, no funcionalidad pospuesta.

## 3. Requirements

Convención de identificadores: `REQ-[AREA]-[NNN]`, donde `AREA` ∈ {FUNC, INT, PERF, SEC, REL, OBS, INST, MAINT, PORT}. Los IDs son únicos e inmutables; cualquier cambio de fondo incrementa una versión y se registra en el Revision History. Cada requisito referencia, donde corresponde, la regla de negocio (`BR-xxx`), restricción (`CON-xxx`) o decisión (`DEC-xxx`) del Context Maestro de la que deriva.

### 3.1 External Interfaces

#### 3.1.1 User Interfaces

N/A — el sistema no expone interfaz gráfica. El "usuario" del sistema es un Cliente API que interactúa exclusivamente vía HTTP/REST, cuyas convenciones se especifican en 3.1.3.

#### 3.1.2 Hardware Interfaces

N/A — el sistema no interactúa con hardware específico.

#### 3.1.3 Software Interfaces

**(a) API provista por el sistema**

- ID: REQ-INT-001
- Title: Convenciones de la API expuesta a Cliente API
- Statement: El sistema shall exponer sus endpoints vía REST/HTTP y JSON, requerir el header `X-User-Id` en todo endpoint autenticado, requerir adicionalmente `X-Idempotency-Key` en `POST /api/v1/payments/transfer`, y responder todo error usando el formato RFC 7807 (`{type, title, status, detail, instance, traceId}`).
- Rationale: Provee un contrato de error uniforme y trazable (`DEC-011`) y un mecanismo estándar de identidad simulada (`CON-006`) e idempotencia (`BR-002`).
- Acceptance Criteria: Toda respuesta de error 4xx/5xx del sistema cumple el esquema RFC 7807 e incluye un `traceId` correlacionable con logs.
- Verification Method: Test
- More Information: Referencia `DEC-011`, `CON-006`.

**(b) Proveedor de autorización externo (consumido)**

- ID: REQ-INT-002
- Title: Integración con `authorization-provider`
- Statement: El sistema shall invocar al `authorization-provider` durante una transferencia enviando `{sourceAccountId, targetAccountId, amount, idempotencyKey}` y shall interpretar como resultados válidos `APPROVED`, `DECLINED`, o una falla de red/HTTP equivalente a `TIMEOUT`/`UNAVAILABLE`.
- Rationale: Aísla al dominio de los detalles del proveedor externo vía `AuthorizationPort` (`DEC-009`).
- Acceptance Criteria: El dominio no referencia directamente WireMock ni detalles HTTP del proveedor; toda comunicación pasa por `AuthorizationPort`.
- Verification Method: Test, Inspection
- More Information: El esquema exacto de request/response (campos adicionales, forma de seleccionar escenario en WireMock) queda **TBD** y se resuelve formalmente en la etapa OpenAPI (`Q-008`, prioridad P2, no bloqueante para esta SRS).

### 3.2 Functional

**Gestión de cuentas (UC-01, UC-02)**

- ID: REQ-FUNC-001
- Title: Crear cuenta
- Statement: El sistema shall crear una `Account` con `owner_id = X-User-Id`, `status = ACTIVE` y `balance` igual al saldo inicial recibido, cuando el header `X-User-Id` esté presente y el saldo inicial sea ≥ 0.
- Rationale: UC-01, `ASM-005`.
- Acceptance Criteria: Dado un `X-User-Id` válido y saldo inicial ≥ 0, la respuesta es `201 Created` con la cuenta persistida.
- Verification Method: Test
- More Information: UC-01.

- ID: REQ-FUNC-002
- Title: Rechazar creación de cuenta sin identidad
- Statement: El sistema shall responder `400 Bad Request` si `X-User-Id` está ausente en `POST /api/v1/accounts`.
- Rationale: `ASM-004`.
- Acceptance Criteria: Request sin `X-User-Id` recibe `400` con cuerpo RFC 7807.
- Verification Method: Test
- More Information: UC-01.

- ID: REQ-FUNC-003
- Title: Rechazar saldo inicial negativo
- Statement: El sistema shall responder `400 Bad Request` si el saldo inicial de la cuenta es negativo.
- Rationale: `BR-001` (el balance nunca puede ser negativo).
- Acceptance Criteria: Saldo inicial < 0 produce `400`; no se persiste ninguna cuenta.
- Verification Method: Test
- More Information: UC-01.

- ID: REQ-FUNC-004
- Title: Consultar cuenta propia
- Statement: El sistema shall responder `200 OK` con saldo y estado de la cuenta cuando `GET /api/v1/accounts/{id}` sea invocado por su propietario (`owner_id == X-User-Id`).
- Rationale: UC-02, `BR-008`.
- Acceptance Criteria: Propietario recibe `200` con `balance` y `status` actuales.
- Verification Method: Test
- More Information: UC-02.

- ID: REQ-FUNC-005
- Title: Rechazar consulta de cuenta ajena
- Statement: El sistema shall responder `403 Forbidden` cuando `X-User-Id` no coincida con el `owner_id` de la cuenta consultada.
- Rationale: `BR-008`. Riesgo aceptado de enumeración de cuentas vía `403` (`RISK-006`).
- Acceptance Criteria: Consulta de cuenta ajena produce `403`, nunca `200`.
- Verification Method: Test
- More Information: UC-02.

- ID: REQ-FUNC-006
- Title: Consultar cuenta inexistente
- Statement: El sistema shall responder `404 Not Found` cuando la cuenta solicitada no exista.
- Rationale: UC-02.
- Acceptance Criteria: ID de cuenta inexistente produce `404`.
- Verification Method: Test
- More Information: UC-02.

**Transferencia de dinero (UC-03)**

- ID: REQ-FUNC-007
- Title: Validar idempotencia antes de procesar transferencia
- Statement: El sistema shall consultar el estado de `X-Idempotency-Key` en Redis antes de ejecutar cualquier lógica de transferencia, clasificándola como nueva, en curso, o en estado terminal.
- Rationale: `BR-002`, diseño de idempotencia (Sección 12 del Context Maestro).
- Acceptance Criteria: Ninguna transferencia se ejecuta dos veces con la misma `idempotency_key` en estado terminal.
- Verification Method: Test
- More Information: UC-03, `DEC-002`, `DEC-003`.

- ID: REQ-FUNC-008
- Title: Rechazar solicitud duplicada en curso
- Statement: El sistema shall responder `409 Conflict` si `X-Idempotency-Key` está en estado `IN_PROGRESS`.
- Rationale: `BR-011` (409 reservado a conflicto de estado del idempotency key).
- Acceptance Criteria: Segunda request concurrente con la misma key `IN_PROGRESS` recibe `409`.
- Verification Method: Test
- More Information: UC-03.

- ID: REQ-FUNC-009
- Title: Devolver resultado cacheado para key en estado terminal
- Statement: El sistema shall devolver el resultado almacenado (sin re-ejecutar la transferencia) cuando `X-Idempotency-Key` esté en un estado terminal (`COMPLETED`/`FAILED`).
- Rationale: `BR-002`.
- Acceptance Criteria: Reenvío de una key ya terminal devuelve exactamente el mismo `httpStatus` y `responseBody` que la ejecución original.
- Verification Method: Test
- More Information: UC-03.

- ID: REQ-FUNC-010
- Title: Validar ownership de la cuenta origen
- Statement: El sistema shall responder `403 Forbidden` si el llamador no es propietario de la cuenta origen de la transferencia.
- Rationale: `BR-006`.
- Acceptance Criteria: Transferencia desde cuenta ajena produce `403`; el destino puede pertenecer a cualquier usuario.
- Verification Method: Test
- More Information: UC-03.

- ID: REQ-FUNC-011
- Title: Validar estado activo de ambas cuentas
- Statement: El sistema shall responder `422 Unprocessable Entity` si la cuenta origen o destino no está en estado `ACTIVE`.
- Rationale: `BR-003`, `BR-011` (resolución `Q-007`: 422 para violaciones de regla de negocio).
- Acceptance Criteria: Transferencia con alguna cuenta `BLOCKED`/`CLOSED` produce `422` con `failure_reason` apropiado.
- Verification Method: Test
- More Information: UC-03.

- ID: REQ-FUNC-012
- Title: Rechazar auto-transferencia
- Statement: El sistema shall responder `422 Unprocessable Entity` cuando `source_account_id == target_account_id`.
- Rationale: `BR-007`.
- Acceptance Criteria: Transferencia con origen y destino idénticos produce `422`.
- Verification Method: Test
- More Information: UC-03.

- ID: REQ-FUNC-013
- Title: Validar saldo suficiente
- Statement: El sistema shall responder `422 Unprocessable Entity` si el saldo de la cuenta origen es menor al monto de la transferencia.
- Rationale: `BR-001`, `BR-011`.
- Acceptance Criteria: Transferencia con saldo insuficiente produce `422` con `failure_reason = INSUFFICIENT_FUNDS`; el saldo no se modifica.
- Verification Method: Test
- More Information: UC-03.

- ID: REQ-FUNC-014
- Title: Ejecutar débito, crédito y registro de forma atómica
- Statement: El sistema shall ejecutar el débito de la cuenta origen, el crédito de la cuenta destino y la creación del registro `Transaction` dentro de una única transacción ACID en PostgreSQL.
- Rationale: `BR-004`, `BR-005`.
- Acceptance Criteria: Ante cualquier fallo dentro de la operación, ningún cambio parcial queda persistido (rollback completo).
- Verification Method: Test
- More Information: UC-03.

- ID: REQ-FUNC-015
- Title: Adquirir locks en orden determinístico
- Statement: El sistema shall adquirir los locks pesimistas de las cuentas involucradas siempre en orden ascendente de `account.id`, independientemente de cuál sea origen y cuál destino.
- Rationale: `DEC-001` (ACCEPTED), previene deadlocks en transferencias cruzadas concurrentes A→B / B→A.
- Acceptance Criteria: Un test de concurrencia con transferencias cruzadas A→B y B→A simultáneas no produce deadlock ni resultado inconsistente.
- Verification Method: Test
- More Information: `RISK-001`.

- ID: REQ-FUNC-016
- Title: Invocar al proveedor de autorización
- Statement: El sistema shall invocar al `authorization-provider` como parte del flujo de transferencia, protegido por Circuit Breaker y Retry.
- Rationale: UC-03, `REQ-INT-002`.
- Acceptance Criteria: Toda transferencia que llega a la etapa de autorización genera exactamente una decisión final (`APPROVED`/`DECLINED`) o un resultado de indisponibilidad, nunca ambos ni ninguno.
- Verification Method: Test
- More Information: UC-03.

- ID: REQ-FUNC-017
- Title: Tratar `DECLINED` como resultado de negocio válido
- Statement: El sistema shall responder `201 Created` con `Transaction.status = FAILED` y `failure_reason = DECLINED` cuando el proveedor de autorización rechace la operación.
- Rationale: `BR-010`.
- Acceptance Criteria: Un `DECLINED` del proveedor nunca produce un error 5xx; siempre se persiste como transacción fallida válida.
- Verification Method: Test
- More Information: UC-03.

- ID: REQ-FUNC-018
- Title: Manejar indisponibilidad del proveedor tras reintentos
- Statement: El sistema shall responder `503 Service Unavailable` cuando el `authorization-provider` no esté disponible después de agotar la política de reintentos.
- Rationale: `BR-011`, `NFR-AVAIL-02`, `NFR-AVAIL-03`.
- Acceptance Criteria: Tras 3 intentos fallidos por timeout/5xx, la respuesta al cliente es `503`; nunca se reintenta sobre un resultado de negocio (`DECLINED`).
- Verification Method: Test
- More Information: UC-03, `RISK-002`.

- ID: REQ-FUNC-019
- Title: Registrar auditoría inmutable del ciclo de vida de la transacción
- Statement: El sistema shall crear una fila en `transaction_logs` por cada cambio relevante de estado de una `Transaction` (`PENDING`, `APPROVED`, `DECLINED`), sin permitir actualización ni borrado de filas existentes.
- Rationale: Alcance confirmado, `NFR-AUDIT-01`.
- Acceptance Criteria: `transaction_logs` no expone operación de update/delete; cada fila tiene `created_at` y ningún `updated_at`.
- Verification Method: Test, Inspection
- More Information: `ASM-002` (el estado `REVERSED` existe en el modelo pero ningún flujo de este alcance lo dispara).

**Consulta de transacción (UC-04)**

- ID: REQ-FUNC-020
- Title: Consultar transacción por ID
- Statement: El sistema shall responder `200 OK` con el detalle de la transacción cuando `GET /api/v1/transactions/{id}` sea invocado por el propietario de la cuenta origen o destino.
- Rationale: UC-04, `DEC-010` (ACCEPTED), `BR-009`.
- Acceptance Criteria: Propietario de origen o destino recibe `200`; cualquier otro usuario recibe `403`; ID inexistente recibe `404`.
- Verification Method: Test
- More Information: UC-04.

### 3.3 Quality of Service

#### 3.3.1 Performance

- ID: REQ-PERF-001
- Title: Referencia de rendimiento bajo concurrencia
- Statement: En un test local de 50 transferencias concurrentes sobre el mismo par de cuentas, el sistema shall procesarlas todas sin corrupción de saldo.
- Rationale: `CS-01`, `NFR-PERF-01`.
- Acceptance Criteria: Saldo final exacto, sin saldo negativo, sin transacciones duplicadas, de forma reproducible (no flaky).
- Verification Method: Test
- More Information: Indicativo, no SLA productivo (`CON-007`). El tiempo total se documenta como referencia, no como objetivo de aceptación.

#### 3.3.2 Security

- ID: REQ-SEC-001
- Title: Validación de entrada previa al dominio
- Statement: El sistema shall validar toda entrada externa con Bean Validation (JSR-380) antes de que llegue a la capa de dominio.
- Rationale: `NFR-SEC-01`.
- Acceptance Criteria: Ningún objeto de dominio se construye a partir de datos no validados.
- Verification Method: Test, Inspection
- More Information: —

- ID: REQ-SEC-002
- Title: Sin secretos en código fuente ni imágenes
- Statement: El sistema shall obtener credenciales (DB, Redis) exclusivamente vía variables de entorno o `.env` no versionado.
- Rationale: `NFR-SEC-02`, `DEC-017`.
- Acceptance Criteria: Ningún secreto aparece en el repositorio ni en la imagen Docker construida.
- Verification Method: Inspection
- More Information: —

- ID: REQ-SEC-003
- Title: Gestión de versiones de dependencias
- Statement: El sistema should gestionar dependencias con versiones fijas (BOM) y opcionalmente ejecutar OWASP Dependency-Check.
- Rationale: `NFR-SEC-03`, prioridad P3, no bloqueante.
- Acceptance Criteria: El build declara versiones explícitas de todas las dependencias directas.
- Verification Method: Inspection
- More Information: —

- ID: REQ-SEC-004
- Title: Autorización por ownership
- Statement: El sistema shall restringir el acceso a `GET /accounts/{id}` y `GET /transactions/{id}` exclusivamente a los usuarios relacionados con ese recurso, según `BR-008` y `BR-009`.
- Rationale: `CON-006` (identidad simulada, no autenticación criptográfica).
- Acceptance Criteria: Ver `REQ-FUNC-005`, `REQ-FUNC-021`.
- Verification Method: Test
- More Information: Riesgo aceptado conscientemente: devolver `403` en vez de `404` permite inferir existencia de una cuenta ajena (`RISK-006`), aceptable para este alcance de demostración.

#### 3.3.3 Reliability

- ID: REQ-REL-001
- Title: Configuración de Circuit Breaker
- Statement: El sistema shall proteger las llamadas al `authorization-provider` con un Circuit Breaker configurado con `slidingWindowSize=10`, `failureRateThreshold=50%`, `waitDurationInOpenState=10s`, `permittedNumberOfCallsInHalfOpenState=3`.
- Rationale: `NFR-AVAIL-02`, `DEC-012` (ACCEPTED).
- Acceptance Criteria: Tras alcanzar el umbral de fallas configurado, el circuito abre y deja de invocar al proveedor durante la ventana configurada.
- Verification Method: Test
- More Information: Parámetros configurables vía properties.

- ID: REQ-REL-002
- Title: Política de reintentos
- Statement: El sistema shall reintentar hasta 3 veces, con backoff exponencial desde 200ms (multiplicador x2), únicamente ante errores transitorios (timeout, 5xx), y nunca ante un resultado de negocio (`DECLINED`).
- Rationale: `NFR-AVAIL-03`, `DEC-012` (ACCEPTED).
- Acceptance Criteria: Un `DECLINED` nunca genera un segundo intento; un timeout sí, hasta el máximo configurado.
- Verification Method: Test
- More Information: —

- ID: REQ-REL-003
- Title: Timeouts explícitos hacia el proveedor externo
- Statement: El sistema shall aplicar `connectTimeout=1s` y `readTimeout=2s` en toda llamada al `authorization-provider`.
- Rationale: `NFR-AVAIL-04`, `DEC-013` (ACCEPTED).
- Acceptance Criteria: Ninguna llamada al proveedor externo permanece pendiente más allá de los timeouts configurados.
- Verification Method: Test
- More Information: —

- ID: REQ-REL-004
- Title: Respaldo de idempotencia ante caída de Redis
- Statement: El sistema shall seguir aceptando transferencias cuando Redis no esté disponible, apoyándose en el constraint `UNIQUE` de `idempotency_key` en PostgreSQL como red de seguridad.
- Rationale: `NFR-AVAIL-01`, `DEC-002` (ACCEPTED).
- Acceptance Criteria: Con Redis caído, una misma `idempotency_key` no puede producir dos transacciones persistidas, aunque se pierda la respuesta cacheada instantánea.
- Verification Method: Test
- More Information: `RISK-003`.

- ID: REQ-REL-005
- Title: Reproducibilidad de comportamiento bajo concurrencia
- Statement: El sistema shall producir resultados reproducibles (no flaky) en los tests de concurrencia de 20 y 50 hilos.
- Rationale: `CS-01`, `CS-02`, `RISK-005`.
- Acceptance Criteria: Ejecuciones repetidas del mismo test de concurrencia producen el mismo resultado (saldo final, cantidad de transacciones).
- Verification Method: Test
- More Information: Sincronización explícita vía `CountDownLatch` u equivalente.

#### 3.3.4 Availability

N/A como objetivo de uptime formal — este proyecto no define un SLA de disponibilidad (`CON-007`). La resiliencia ante fallos parciales se cubre mediante los requisitos de 3.3.3 Reliability (degradación controlada, no garantía de tiempo de actividad).

#### 3.3.5 Observability

- ID: REQ-OBS-001
- Title: Indicadores de salud de dependencias
- Statement: El sistema shall exponer `/actuator/health` con indicadores custom para PostgreSQL, Redis y el estado del Circuit Breaker.
- Rationale: `NFR-OBS-01`, `DEC-014` (ACCEPTED).
- Acceptance Criteria: `/actuator/health` refleja `DOWN` cuando cualquiera de esas dependencias falla.
- Verification Method: Test, Demonstration
- More Information: —

- ID: REQ-OBS-002
- Title: Logging estructurado con correlación
- Statement: El sistema shall emitir logs en formato JSON incluyendo `traceId`/`correlationId` propagado por request, presente también en cada respuesta de error.
- Rationale: `NFR-OBS-02`, `DEC-014` (ACCEPTED), `REQ-INT-001`.
- Acceptance Criteria: El `traceId` de una respuesta de error coincide exactamente con el de las líneas de log generadas durante esa request.
- Verification Method: Inspection, Demonstration
- More Information: —

### 3.4 Compliance

N/A — este proyecto no maneja datos financieros ni usuarios reales, por lo que no está sujeto a regulación financiera o de protección de datos real (`NFR-AUDIT-01`). No se retienen ni purgan datos por requisito regulatorio; ver `NFR-AUDIT-01` en Sección 3.3.5/Apéndice.

### 3.5 Design and Implementation

#### 3.5.1 Installation

- ID: REQ-INST-001
- Title: Levantamiento del entorno con un solo comando
- Statement: El sistema shall poder levantarse completo (aplicación + PostgreSQL + Redis + `authorization-provider` simulado) con un único comando `docker compose up`.
- Rationale: `CS-04`, `DEC-005` (ACCEPTED, 4 servicios).
- Acceptance Criteria: Tras `docker compose up`, los 4 servicios quedan operativos sin pasos manuales adicionales.
- Verification Method: Demonstration
- More Information: —

- ID: REQ-INST-002
- Title: Configuración externalizada por entorno
- Statement: El sistema shall recibir su configuración (conexión a DB/Redis, parámetros de Resilience4j, TTL de idempotencia) vía variables de entorno.
- Rationale: `REQ-SEC-002`, `DEC-003`.
- Acceptance Criteria: Ningún valor de configuración de entorno está hardcodeado en el código fuente.
- Verification Method: Inspection
- More Information: —

#### 3.5.2 Build and Delivery

**TBD.** No se definieron aún requisitos de pipeline de build/empaquetado/promoción de artefactos más allá del uso de Docker Compose para el entorno de ejecución (`DEC-005`). Se resolverá en la etapa de Deployment Documentation si el alcance de portafolio lo justifica.

#### 3.5.3 Distribution

N/A — despliegue local de un solo nodo vía `docker-compose`; no hay topología distribuida ni multi-región en este alcance. `NFR-SCALE-01` (statelessness a nivel de proceso) deja la puerta abierta a escalado horizontal futuro, pero no es un requisito de esta versión.

#### 3.5.4 Maintainability

- ID: REQ-MAINT-001
- Title: Verificación automática de Arquitectura Hexagonal
- Statement: El sistema shall verificar automáticamente, como parte del test suite, que el paquete `domain/` no tiene ninguna dependencia de Spring ni JPA.
- Rationale: `CS-03`, `NFR-MAINT-01`, `DEC-018` (ACCEPTED).
- Acceptance Criteria: Un test ArchUnit falla el build si se introduce una dependencia de framework en `domain/`.
- Verification Method: Test
- More Information: `CON-002`.

- ID: REQ-MAINT-002
- Title: Cobertura mínima de tests en capas core
- Statement: El sistema shall mantener cobertura de línea ≥80% en `domain` + `application` (JaCoCo), con el 100% de los casos de error de negocio de `BR-011` cubiertos por tests.
- Rationale: `NFR-TEST-01`, `DEC-015` (ACCEPTED).
- Acceptance Criteria: El reporte de JaCoCo confirma ≥80% de cobertura en esos módulos; cada código de error de `BR-011` tiene al menos un test dedicado.
- Verification Method: Test, Analysis
- More Information: —

#### 3.5.5 Reusability

N/A — el Payment Gateway Engine es un proyecto de demostración de propósito único; no está diseñado como librería o componente reutilizable en otros contextos.

#### 3.5.6 Portability

- ID: REQ-PORT-001
- Title: Runtime containerizado y agnóstico de proveedor cloud
- Statement: El sistema shall ejecutarse como imagen Docker sobre cualquier host compatible con Docker, sin depender de servicios propietarios de un proveedor cloud específico.
- Rationale: Alcance de portafolio; sin restricción de proveedor cloud confirmada.
- Acceptance Criteria: El `docker-compose.yml` no referencia servicios administrados específicos de un proveedor (p. ej., RDS, ElastiCache).
- Verification Method: Inspection
- More Information: —

#### 3.5.7 Cost

N/A — proyecto personal de portafolio sin restricciones presupuestarias definidas.

#### 3.5.8 Deadline

**TBD.** No se definieron hitos ni fecha de entrega por parte del dueño del producto. No bloqueante (prioridad P3); puede añadirse en cualquier momento sin invalidar los requisitos ya definidos.

#### 3.5.9 Proof of Concept

N/A — no se definió una fase de POC separada. El propio proceso de descubrimiento documentado en el Context Maestro cumplió la función de reducción de riesgo previa a la implementación.

#### 3.5.10 Change Management

**ASSUMED** (bajo riesgo, proyecto de un solo desarrollador): los cambios se controlan vía historial de commits de git y versionado semántico informal (tags); no se define un flujo formal de aprobación dado el contexto de propietario único. Si el proyecto se abriera a colaboradores externos, este supuesto debe revisarse.

### 3.6 AI/ML

N/A — el Payment Gateway Engine no incorpora componentes de machine learning ni IA en este alcance. Esta sección se mantiene por consistencia con la estructura de la plantilla.

## 4. Verification

| Requirement ID | Verification Method | Test/Artifact Link | Status | Evidence |
|---|---|---|---|---|
| REQ-FUNC-001 a REQ-FUNC-006 | Test | TBD | Pending | — |
| REQ-FUNC-007 a REQ-FUNC-009 | Test | TBD | Pending | — |
| REQ-FUNC-010 a REQ-FUNC-013 | Test | TBD | Pending | — |
| REQ-FUNC-014, REQ-FUNC-015 | Test | TBD (test de concurrencia 20/50 hilos) | Pending | — |
| REQ-FUNC-016 a REQ-FUNC-018 | Test | TBD (integración con WireMock) | Pending | — |
| REQ-FUNC-019 | Test, Inspection | TBD | Pending | — |
| REQ-FUNC-020 | Test | TBD | Pending | — |
| REQ-PERF-001 | Test | TBD | Pending | — |
| REQ-SEC-001, REQ-SEC-004 | Test | TBD | Pending | — |
| REQ-SEC-002, REQ-SEC-003 | Inspection | TBD | Pending | — |
| REQ-REL-001 a REQ-REL-004 | Test | TBD | Pending | — |
| REQ-REL-005 | Test | TBD (test de concurrencia dedicado) | Pending | — |
| REQ-OBS-001, REQ-OBS-002 | Test, Demonstration | TBD | Pending | — |
| REQ-INST-001 | Demonstration | TBD | Pending | — |
| REQ-INST-002 | Inspection | TBD | Pending | — |
| REQ-MAINT-001 | Test (ArchUnit) | TBD | Pending | — |
| REQ-MAINT-002 | Test, Analysis (JaCoCo) | TBD | Pending | — |
| REQ-PORT-001 | Inspection | TBD | Pending | — |
| REQ-INT-001, REQ-INT-002 | Test, Inspection | TBD | Pending | — |

No se registran resultados de evidencia todavía: el test suite se implementará en la etapa de Testing del roadmap. Este documento no debe leerse como si esas verificaciones ya se hubieran ejecutado.

## 5. Appendixes

- **Context Maestro** (`payment-gateway-engine-context-maestro.md`): fuente completa de descubrimiento — decisiones (`DEC-001` a `DEC-018`, todas ACCEPTED), riesgos (`RISK-001` a `RISK-008`), supuestos, y preguntas abiertas (`Q-007`, `Q-008`, ambas resueltas en este SRS). No se duplica su contenido aquí; se referencia.
- **Diagrama de arquitectura de capas** y **topología de Docker Compose**: ver Secciones 18 y 19 del Context Maestro.
- **Modelo de datos borrador** (`accounts`, `transactions`, `transaction_logs`): ver Sección 11 del Context Maestro; se formaliza en la etapa de Modelo de Datos del roadmap.
