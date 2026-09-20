package com.paymentgateway.engine.domain.policy;

import java.util.UUID;

/**
 * Par de IDs de cuenta en el orden en que deben adquirirse los locks
 * (Sección 10 del contexto): ascendente por UUID.compareTo(), sin importar
 * cuál es origen y cuál destino en la transferencia.
 */
public record LockOrder(UUID first, UUID second) {
}