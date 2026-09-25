# Ejemplos reales de errores (BR-011)

Generado con `scripts/capture-error-examples.sh` contra la aplicación en ejecución.
Todas las respuestas usan `application/problem+json` (RFC 7807) e incluyen `traceId`,
que coincide con el header `X-Trace-Id` y con los logs de esa request.

## 400 — Header X-User-Id ausente

```json
{
    "type": "https://payment-gateway-engine/errors/missing-required-header",
    "title": "Bad Request",
    "status": 400,
    "detail": "Required header 'X-User-Id' is missing.",
    "instance": "/api/v1/accounts/21cd6102-7cfe-4c51-8a21-92ecf9e1b6af",
    "traceId": "example-400"
}
```

## 403 — Violación de ownership

```json
{
    "type": "https://payment-gateway-engine/errors/ownership-violation",
    "title": "Forbidden",
    "status": 403,
    "detail": "Requester does not own resource: 21cd6102-7cfe-4c51-8a21-92ecf9e1b6af",
    "instance": "/api/v1/accounts/21cd6102-7cfe-4c51-8a21-92ecf9e1b6af",
    "traceId": "example-403"
}
```

## 404 — Cuenta inexistente

```json
{
    "type": "https://payment-gateway-engine/errors/account-not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Account not found: 00000000-0000-0000-0000-000000000000",
    "instance": "/api/v1/accounts/00000000-0000-0000-0000-000000000000",
    "traceId": "example-404"
}
```

## 409 — X-Idempotency-Key en estado IN_PROGRESS

```json
{
    "type": "https://payment-gateway-engine/errors/idempotency-conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "A transfer with idempotency key 'example-409' is already in progress",
    "instance": "/api/v1/payments/transfer",
    "traceId": "example-409"
}
```

## 422 — Regla de negocio (self-transfer INTERNAL, BR-007)

```json
{
    "type": "https://payment-gateway-engine/errors/business-rule-violation",
    "title": "Unprocessable Entity",
    "status": 422,
    "detail": "Source and target account must not be the same: 21cd6102-7cfe-4c51-8a21-92ecf9e1b6af",
    "instance": "/api/v1/payments/transfer",
    "traceId": "example-422"
}
```

## 503 — Proveedor externo no disponible tras agotar reintentos

```json
{
    "type": "https://payment-gateway-engine/errors/authorization-provider-unavailable",
    "title": "Service Unavailable",
    "status": 503,
    "detail": "External authorization provider is temporarily unavailable. The transfer was not executed and can be safely retried with the same X-Idempotency-Key.",
    "instance": "/api/v1/payments/transfer",
    "traceId": "example-503"
}
```

