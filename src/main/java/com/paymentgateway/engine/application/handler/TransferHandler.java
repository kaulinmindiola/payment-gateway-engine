package com.paymentgateway.engine.application.handler;

import com.paymentgateway.engine.domain.model.Transaction;

public interface TransferHandler {
    Transaction handle(TransferCommand command);
}