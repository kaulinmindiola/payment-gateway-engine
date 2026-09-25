#!/usr/bin/env bash
# Genera docs/api/error-examples.md con respuestas REALES de la API para cada
# código de BR-011. Requiere la app con perfil docker, pge-redis y pge-wiremock.
# Uso: ACC=<cuenta del usuario demo> ./scripts/capture-error-examples.sh
set -euo pipefail

BASE="${BASE:-http://localhost:8080/api/v1}"
OWNER="99999999-9999-9999-9999-999999999999"
OTHER="44444444-4444-4444-4444-444444444444"
ACC="${ACC:?Exporta ACC con una cuenta del usuario demo}"
RAILS_PROVIDER="10000000-0000-0000-0000-000000000002"
RAILS_BANK="20000000-0000-0000-0000-000000000002"
OUT="docs/api/error-examples.md"

mkdir -p "$(dirname "$OUT")"
cat > "$OUT" <<'EOF'
# Ejemplos reales de errores (BR-011)

Generado con `scripts/capture-error-examples.sh` contra la aplicación en ejecución.
Todas las respuestas usan `application/problem+json` (RFC 7807) e incluyen `traceId`,
que coincide con el header `X-Trace-Id` y con los logs de esa request.

EOF

capture() {
  local title="$1"; shift
  {
    echo "## $title"
    echo
    echo '```json'
    curl -s "$@" | python3 -m json.tool
    echo '```'
    echo
  } >> "$OUT"
}

transfer_body() {  # source, target, amount
  printf '{"sourceAccountId":"%s","transferType":"INTERNAL","targetAccountId":"%s","amount":%s}' "$1" "$2" "$3"
}

capture "400 — Header X-User-Id ausente" \
  "$BASE/accounts/$ACC" -H "X-Trace-Id: example-400"

capture "403 — Violación de ownership" \
  "$BASE/accounts/$ACC" -H "X-User-Id: $OTHER" -H "X-Trace-Id: example-403"

capture "404 — Cuenta inexistente" \
  "$BASE/accounts/00000000-0000-0000-0000-000000000000" -H "X-User-Id: $OWNER" -H "X-Trace-Id: example-404"

# 409: se simula una key en curso escribiendo el marcador directamente en Redis.
docker exec pge-redis redis-cli SET "idempotency:transfer:example-409" IN_PROGRESS EX 60 > /dev/null
capture "409 — X-Idempotency-Key en estado IN_PROGRESS" \
  -X POST "$BASE/payments/transfer" -H "Content-Type: application/json" \
  -H "X-User-Id: $OWNER" -H "X-Idempotency-Key: example-409" -H "X-Trace-Id: example-409" \
  -d "$(transfer_body "$ACC" "$ACC" 10.00)"

capture "422 — Regla de negocio (self-transfer INTERNAL, BR-007)" \
  -X POST "$BASE/payments/transfer" -H "Content-Type: application/json" \
  -H "X-User-Id: $OWNER" -H "X-Idempotency-Key: example-422-$(date +%s)" -H "X-Trace-Id: example-422" \
  -d "$(transfer_body "$ACC" "$ACC" 10.00)"

capture "503 — Proveedor externo no disponible tras agotar reintentos" \
  -X POST "$BASE/payments/transfer" -H "Content-Type: application/json" \
  -H "X-User-Id: $OWNER" -H "X-Idempotency-Key: example-503-$(date +%s)" -H "X-Trace-Id: example-503" \
  -d "{\"sourceAccountId\":\"$ACC\",\"transferType\":\"EXTERNAL\",\"targetProviderId\":\"$RAILS_PROVIDER\",\"targetBankId\":\"$RAILS_BANK\",\"targetExternalReference\":\"REF-CB-ALWAYS-FAIL\",\"amount\":10.00}"

echo "Generado: $OUT"