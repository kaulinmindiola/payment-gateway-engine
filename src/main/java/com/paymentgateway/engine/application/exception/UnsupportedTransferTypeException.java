package com.paymentgateway.engine.application.exception;

import com.paymentgateway.engine.domain.model.TransferType;
public class UnsupportedTransferTypeException extends RuntimeException {
    public UnsupportedTransferTypeException(TransferType transferType) {
        super("Transfer type not yet supported in this phase: " + transferType);
    }
}