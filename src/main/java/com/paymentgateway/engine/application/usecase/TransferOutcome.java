package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.model.Transaction;
public sealed interface TransferOutcome {
    record Executed(Transaction transaction) implements TransferOutcome {}
    record Replayed(int httpStatus, String responseBody) implements TransferOutcome {}
}