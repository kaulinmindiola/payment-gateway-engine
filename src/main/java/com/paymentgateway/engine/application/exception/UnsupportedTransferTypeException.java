package com.paymentgateway.engine.application.exception;

import com.paymentgateway.engine.domain.model.TransferType;

/**
 * Scaffolding temporal: EXTERNAL es un TransferType válido en el dominio
 * (Fase 2), pero ExternalTransferHandler no existe hasta Fase 8 (Sección 9
 * del plan: "Controller: POST /payments/transfer (rama INTERNAL únicamente
 * por ahora)"). No extiende DomainException -- no es una regla de negocio,
 * es una limitación de fase. Se elimina cuando ExternalTransferHandler
 * quede implementado.
 */
public class UnsupportedTransferTypeException extends RuntimeException {
    public UnsupportedTransferTypeException(TransferType transferType) {
        super("Transfer type not yet supported in this phase: " + transferType);
    }
}