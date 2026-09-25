cat > README.md << 'EOF'
# Payment Gateway Engine

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