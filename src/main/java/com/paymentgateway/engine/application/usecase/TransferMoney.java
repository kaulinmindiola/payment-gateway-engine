package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.application.exception.UnsupportedTransferTypeException;
import com.paymentgateway.engine.application.handler.InternalTransferHandler;
import com.paymentgateway.engine.application.handler.TransferCommand;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransferType;
import org.springframework.stereotype.Service;

@Service
public class TransferMoney {

    private final InternalTransferHandler internalTransferHandler;

    // Fase 8 añade ExternalTransferHandler como segundo parámetro de
    // constructor + reemplaza el throw de la rama EXTERNAL por la
    // delegación real -- sin tocar el resto de esta clase (ver Paso 3).
    public TransferMoney(InternalTransferHandler internalTransferHandler) {
        this.internalTransferHandler = internalTransferHandler;
    }

    public Transaction execute(TransferMoneyCommand command) {
        return switch (command.getTransferType()) {
            case INTERNAL -> internalTransferHandler.handle(toInternalCommand(command));
            case EXTERNAL -> throw new UnsupportedTransferTypeException(TransferType.EXTERNAL);
        };
    }

    private TransferCommand toInternalCommand(TransferMoneyCommand command) {
        // Defensa de programador, no de usuario: un DTO bien formado (Paso 5)
        // nunca debería permitir INTERNAL sin targetAccountId -- esa validación
        // de forma vive en el DTO (Bean Validation), no aquí. Si esta línea se
        // alcanza, es un bug de capa web, no un input de usuario malformado.
        if (command.getTargetAccountId() == null) {
            throw new IllegalArgumentException("targetAccountId is required for INTERNAL transfers");
        }
        return TransferCommand.forInternal(
                command.getSourceAccountId(), command.getRequestingUserId(),
                command.getTargetAccountId(), command.getAmount(), command.getIdempotencyKey());
    }
}