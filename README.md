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