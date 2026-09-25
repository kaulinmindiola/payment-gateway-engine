package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.domain.port.TransactionHistoryQuery;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.domain.Specification;

/**
 * Decisión 3 (Fase 9): cada filtro se añade SOLO si está presente. Se evita
 * el patrón JPQL "(:param IS NULL OR ...)", que falla con parámetros nulos
 * tipados en Postgres. La base (cuenta origen O destino, BR-015) siempre
 * existe, así que nunca se compone sobre una Specification nula.
 */
final class TransactionHistorySpecifications {

    private TransactionHistorySpecifications() {
    }

    static Specification<TransactionEntity> from(TransactionHistoryQuery query) {
        Specification<TransactionEntity> spec = involvesAccount(query);

        if (query.status() != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("status"), query.status()));
        }
        if (query.transferType() != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("transferType"), query.transferType()));
        }
        if (query.dateFrom() != null) {   // inclusivo
            spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), query.dateFrom()));
        }
        if (query.dateTo() != null) {     // exclusivo
            spec = spec.and((root, cq, cb) -> cb.lessThan(root.get("createdAt"), query.dateTo()));
        }
        return spec;
    }

    private static Specification<TransactionEntity> involvesAccount(TransactionHistoryQuery query) {
        return (root, cq, cb) -> cb.or(
                cb.equal(root.get("sourceAccountId"), query.accountId()),
                cb.equal(root.get("targetAccountId"), query.accountId()));
    }
}