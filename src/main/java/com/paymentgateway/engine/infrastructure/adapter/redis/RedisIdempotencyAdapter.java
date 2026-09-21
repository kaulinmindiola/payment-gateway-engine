package com.paymentgateway.engine.infrastructure.adapter.redis;

import com.paymentgateway.engine.domain.port.IdempotencyClaim;
import com.paymentgateway.engine.domain.port.IdempotencyPort;
import com.paymentgateway.engine.domain.port.IdempotencyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisIdempotencyAdapter implements IdempotencyPort {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyAdapter.class);
    private static final String KEY_PREFIX = "idempotency:transfer:";
    private static final Duration TTL = Duration.ofHours(24); // Sección 9 del contexto
    private static final String IN_PROGRESS_MARKER = "IN_PROGRESS";

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public IdempotencyClaim tryBegin(String idempotencyKey) {
        String key = KEY_PREFIX + idempotencyKey;
        try {
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            Boolean claimed = ops.setIfAbsent(key, IN_PROGRESS_MARKER, TTL);

            if (Boolean.TRUE.equals(claimed)) {
                return IdempotencyClaim.acquired();
            }

            String existingValue = ops.get(key);
            if (existingValue == null || IN_PROGRESS_MARKER.equals(existingValue)) {
                return IdempotencyClaim.inProgress();
            }

            return StoredResult.parse(existingValue).toClaim();

        } catch (Exception ex) {
            // RISK-003 / ADR-0003: si Redis no responde, se deja pasar y se
            // confía en el UNIQUE constraint de transactions.idempotency_key
            // (backstop en Postgres, Fase 3). La corrección nunca depende de
            // Redis -- solo la velocidad de respuesta ante un duplicado.
            log.warn("Redis unavailable during tryBegin for key '{}', falling back to Postgres UNIQUE constraint", idempotencyKey, ex);
            return IdempotencyClaim.acquired();
        }
    }

    @Override
    public void complete(String idempotencyKey, IdempotencyResult result) {
        String key = KEY_PREFIX + idempotencyKey;
        try {
            String serialized = StoredResult.from(result).serialize();
            redisTemplate.opsForValue().set(key, serialized, TTL);
        } catch (Exception ex) {
            log.warn("Redis unavailable during complete for key '{}', cached result lost (Postgres remains source of truth)", idempotencyKey, ex);
        }
    }

    /**
     * Formato interno simple, delimitado -- evita traer Jackson a infrastructure/adapter/redis/
     * solo para serializar 3 campos. status|httpStatus|responseBody (responseBody puede
     * contener '|', por eso va al final sin más split).
     */
    private record StoredResult(String status, int httpStatus, String responseBody) {

        static StoredResult from(IdempotencyResult result) {
            String status = result.getOutcome() == IdempotencyResult.Outcome.COMPLETED ? "COMPLETED" : "FAILED";
            return new StoredResult(status, result.getHttpStatus(), result.getResponseBody());
        }

        static StoredResult parse(String raw) {
            String[] parts = raw.split("\\|", 3);
            return new StoredResult(parts[0], Integer.parseInt(parts[1]), parts[2]);
        }

        String serialize() {
            return status + "|" + httpStatus + "|" + responseBody;
        }

        IdempotencyClaim toClaim() {
            IdempotencyClaim.ClaimStatus claimStatus = "COMPLETED".equals(status)
                    ? IdempotencyClaim.ClaimStatus.COMPLETED
                    : IdempotencyClaim.ClaimStatus.FAILED;
            return IdempotencyClaim.terminal(claimStatus, httpStatus, responseBody);
        }
    }
}