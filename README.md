# Payment Gateway Engine
[![CI](https://github.com/kaulinmindiola/payment-gateway-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/kaulinmindiola/payment-gateway-engine/actions/workflows/ci.yml)
Motor de transferencias monetarias (core bancario simulado) que demuestra ingeniería de nivel producción en concurrencia, idempotencia y resiliencia.

## Desarrollo local (temporal, hasta Fase 13)

Hasta que exista `docker-compose.yml`, levanta Postgres y Redis manualmente:

\`\`\`bash
docker run -d --name pge-postgres -e POSTGRES_DB=payment_gateway \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=changeme \
  -p 5432:5432 postgres:16

docker run -d --name pge-redis -p 6379:6379 redis:7
\`\`\`

Luego:

\`\`\`bash
./mvnw spring-boot:run
\`\`\`

## API — Cuentas (Fase 4)

Requiere un usuario ya sembrado en `users` (no hay endpoint de creación de usuarios — ver "Qué NO hace este proyecto").

### Crear cuenta

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "X-User-Id: <UUID de un usuario existente>" \
  -H "Content-Type: application/json" \
  -d '{"initialBalance": 250.00}'
```

Respuesta `201 Created`:
```json
{"id": "...", "ownerId": "...", "balance": 250.00, "status": "ACTIVE"}
```

### Consultar cuenta

```bash
curl http://localhost:8080/api/v1/accounts/<ACCOUNT_ID> \
  -H "X-User-Id: <UUID del owner>"
```

`200 OK` si eres el owner; `403 Forbidden` si no; `404 Not Found` si la cuenta no existe.

## API — Transferencias y consulta (Fase 5)

### Transferir dinero (INTERNAL)

```bash
curl -X POST http://localhost:8080/api/v1/payments/transfer \
  -H "X-User-Id: <UUID owner de la cuenta origen>" \
  -H "X-Idempotency-Key: <clave única por intento de transferencia>" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountId": "...", "transferType": "INTERNAL", "targetAccountId": "...", "amount": 150.00}'
```

Respuesta `201 Created` con la `Transaction` creada (`status: COMPLETED`).

> **Limitación de fase**: `transferType: "EXTERNAL"` responde `400` -- se implementa en Fase 8.

### Consultar transacción

```bash
curl http://localhost:8080/api/v1/transactions/<TRANSACTION_ID> \
  -H "X-User-Id: <UUID del owner de ORIGEN o DESTINO>"
```

`200 OK`, `403 Forbidden` si no eres owner de ninguna de las dos cuentas, `404 Not Found` si no existe.

**Idempotencia** (Fase 6): reenviar la misma request con el mismo `X-Idempotency-Key`:
- Si la primera aún está en curso → `409 Conflict`.
- Si la primera ya terminó (éxito o fallo de negocio) → se devuelve la respuesta original exacta, sin re-ejecutar.

## API — Catálogo de bancos externos (Fase 7)

Sin autenticación (`X-User-Id` no requerido) — dato de referencia público.

### Listar catálogo completo

```bash
curl http://localhost:8080/api/v1/external-banks
```

Respuesta `200 OK`, array denormalizado (incluye `providerCode` inline, sin paginación — excepción justificada, ver `ADR-0010`).

### Consultar un banco específico

```bash
curl http://localhost:8080/api/v1/external-banks/<EXTERNAL_BANK_ID>
```

`200 OK` si existe; `404 Not Found` si no.

### Datos demo sembrados (perfil `docker`)

Ejecutando con `-Dspring-boot.run.profiles=docker` (o vía `docker compose`, Fase 13), el catálogo viene pre-sembrado:

| Provider | Bank ID | Código | País |
|---|---|---|---|
| `SWIFT-demo` (reliable) | `20000000-0000-0000-0000-000000000001` | DE-DEMO-001 | DE |
| `RAILS-flaky` (flaky, para Fase 8) | `20000000-0000-0000-0000-000000000002` | ES-DEMO-001 | ES |

### Transferir dinero (EXTERNAL) — Fase 8

Requiere el proveedor de autorización simulado (WireMock) en `localhost:8089`.
Hasta Fase 13 (`docker compose`), se levanta manualmente reutilizando los
mismos stubs versionados que usan los tests:

```bash
docker run -d --name pge-wiremock -p 8089:8080 \
  -v "$(pwd)/wiremock:/home/wiremock" wiremock/wiremock:3.5.4
```

```bash
curl -X POST http://localhost:8080/api/v1/payments/transfer \
  -H "X-User-Id: <owner de la cuenta origen>" \
  -H "X-Idempotency-Key: <clave única>" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountId":"...","transferType":"EXTERNAL","targetProviderId":"...","targetBankId":"...","targetExternalReference":"...","amount":100.00}'
```

| Escenario (stubs demo) | Resultado |
|---|---|
| `SWIFT-demo`, referencia normal | `201`, `COMPLETED` |
| `SWIFT-demo`, referencia terminada en `-DECLINE` | `201`, `FAILED` / `DECLINED` (resultado de negocio, sin reintento) |
| `RAILS-flaky` | `201`, `COMPLETED` tras 503 → timeout → aprobado (reintentos internos) |
| Proveedor caído / circuito abierto | `503`: no se ejecutó nada y se puede reintentar con la misma `X-Idempotency-Key` |

## API — Historial de transacciones (Fase 9)

```bash
curl "http://localhost:8080/api/v1/accounts/<ACCOUNT_ID>/transactions?page=0&size=20&status=COMPLETED&transferType=INTERNAL&dateFrom=2026-01-01T00:00:00Z&dateTo=2026-02-01T00:00:00Z" \
  -H "X-User-Id: <UUID del owner de la cuenta>"
```

| Parámetro | Descripción |
|---|---|
| `page` | Índice base 0 (default `0`) |
| `size` | Default `20`, máximo `100` (valores fuera de rango → `400`) |
| `status` | `PENDING`, `COMPLETED`, `FAILED` |
| `transferType` | `INTERNAL`, `EXTERNAL` |
| `dateFrom` | ISO-8601 UTC, **inclusivo** |
| `dateTo` | ISO-8601 UTC, **exclusivo** |

Incluye las transacciones donde la cuenta participa como **origen o destino**, ordenadas de la más reciente a la más antigua. Respuesta:

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 3, "totalPages": 1 }
```

Solo el owner de la cuenta puede consultar su historial (`403` en otro caso; `404` si la cuenta no existe). No existe un listado global `GET /api/v1/transactions`.

## Errores y trazabilidad (Fase 10)

Todos los errores responden en RFC 7807 (`application/problem+json`) con
`type`, `title`, `status`, `detail`, `instance` y `traceId`. Cada respuesta
incluye el header `X-Trace-Id`: si envías uno válido se respeta; si no, se
genera. El mismo valor aparece en los logs de la request. Ejemplos reales
por código: [`docs/api/error-examples.md`](docs/api/error-examples.md).

## Observabilidad (Fase 11)

| Endpoint | Descripción |
|---|---|
| `GET /actuator/health` | Estado agregado y por componente: `db` (PostgreSQL), `redis`, `circuitBreakers` (proveedor de autorización). Sin detalles internos. |
| `GET /actuator/prometheus` | Métricas en formato Prometheus (JVM, HTTP, Resilience4j), con el tag `application="payment-gateway-engine"`. |

```bash
curl -s http://localhost:8080/actuator/health
```

Si cualquier dependencia falla (Postgres o Redis caídos, circuito abierto), el estado agregado es `DOWN` y la respuesta es `HTTP 503`.

> **Nota de diseño**: el health indica si las **dependencias** están sanas, no si el sistema puede atender tráfico. Con Redis caído, las transferencias siguen siendo correctas (fallback a PostgreSQL, ADR-0003); con el circuito abierto, las transferencias INTERNAL siguen funcionando. Ver `ADR-0005`.

## Calidad y cobertura (Fase 12)

```bash
./mvnw verify                          # tests + ArchUnit + gate de cobertura
./mvnw jacoco:check@coverage-gate      # solo el gate (tras verify)
```

Gate: >= 80 % de líneas en `domain` + `application`, medido solo con tests unitarios. Reporte en `target/site/jacoco/index.html`. Análisis de las líneas no cubiertas: [`docs/testing/coverage-analysis.md`](docs/testing/coverage-analysis.md).

## Quickstart (Docker Compose)

Requisito: Docker con Docker Compose v2.

```bash
cp .env.example .env
docker compose up --build -d
curl http://localhost:8080/actuator/health     # {"status":"UP", ...}
```

Levanta 4 servicios: la aplicación, PostgreSQL 16, Redis 7 y un proveedor de autorización simulado (WireMock) con los mismos stubs que usan los tests. El perfil `docker` siembra datos de demo:

| Dato demo | UUID |
|---|---|
| Usuario | `99999999-9999-9999-9999-999999999999` |
| Provider `SWIFT-demo` (fiable) / banco DE | `10000000-...-000000000001` / `20000000-...-000000000001` |
| Provider `RAILS-flaky` (503 → timeout → aprobado) / banco ES | `10000000-...-000000000002` / `20000000-...-000000000002` |

Flujo de ejemplo:

```bash
BASE=http://localhost:8080/api/v1
USER=99999999-9999-9999-9999-999999999999

# Crear una cuenta (anota el "id" devuelto)
curl -X POST $BASE/accounts -H "X-User-Id: $USER" -H "Content-Type: application/json" \
  -d '{"initialBalance": 500.00}'

# Transferencia EXTERNAL al proveedor fiable
curl -X POST $BASE/payments/transfer -H "X-User-Id: $USER" \
  -H "X-Idempotency-Key: demo-1" -H "Content-Type: application/json" \
  -d '{"sourceAccountId":"<ID>","transferType":"EXTERNAL","targetProviderId":"10000000-0000-0000-0000-000000000001","targetBankId":"20000000-0000-0000-0000-000000000001","targetExternalReference":"ES9121000418450200051332","amount":50.00}'
```

Reiniciar la demo desde cero: `docker compose down -v`.

### Problemas frecuentes

- **Cambiaste las credenciales en `.env` y la app no autentica**: Postgres solo aplica las credenciales al inicializar un volumen vacío. Ejecuta `docker compose down -v`.
- **Puerto 8080 u 8089 ocupado**: detén la aplicación local u otros contenedores que usen esos puertos (`SERVER_PORT` en `.env` cambia el puerto publicado de la app).
- **No ejecutes `docker compose config` sin `--quiet`**: imprime la configuración resuelta, incluida la contraseña.

### Ejecución local sin Docker para la aplicación

```bash
set -a; source .env; set +a      # Spring Boot no lee .env por sí solo
./mvnw spring-boot:run
```
## CI/CD (Fase 14)

Cada pull request y cada merge a `main` ejecutan el workflow [`ci.yml`](.github/workflows/ci.yml):

| Job | Cuándo | Qué verifica |
|---|---|---|
| `build-and-test` | PR y `main` | `./mvnw verify` (unitarios, ArchUnit, integración con Testcontainers, concurrencia CS-01, resiliencia CS-02) y gate de cobertura `jacoco:check@coverage-gate` |
| `docker-smoke` | PR y `main` | `docker compose up` desde el estado commiteado, `/actuator/health` en `UP` y catálogo sembrado (CS-04) |
| `publish-image` | Solo tras merge a `main` | Publica la imagen en GHCR con los tags `latest` y el SHA del commit |

`main` está protegida: solo admite cambios mediante pull request, con `build-and-test` y `docker-smoke` en verde.

### Imagen publicada

```bash
docker pull ghcr.io/kaulinmindiola/payment-gateway-engine:latest