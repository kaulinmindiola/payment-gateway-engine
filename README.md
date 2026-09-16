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