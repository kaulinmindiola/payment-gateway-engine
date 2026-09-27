package com.paymentgateway.engine.domain.port;

import com.paymentgateway.engine.domain.model.TransactionStatus;
import com.paymentgateway.engine.domain.model.TransferType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Consulta de historial. Filtros opcionales (null = sin filtro).
 * Intervalo de fechas semiabierto: dateFrom INCLUSIVO, dateTo EXCLUSIVO.
 * La capa web valida primero y responde 400; esta
 * validación es defensa en profundidad para cualquier otro llamador.
 */
public record TransactionHistoryQuery(
        UUID accountId,
        TransactionStatus status,
        TransferType transferType,
        Instant dateFrom,
        Instant dateTo,
        int page,
        int size
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public TransactionHistoryQuery {
        Objects.requireNonNull(accountId, "accountId must not be null");
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom must not be after dateTo");
        }
    }
}