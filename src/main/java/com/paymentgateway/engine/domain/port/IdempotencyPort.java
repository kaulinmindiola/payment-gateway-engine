package com.paymentgateway.engine.domain.port;

public interface IdempotencyPort {
    IdempotencyClaim tryBegin(String idempotencyKey);
    void complete(String idempotencyKey, IdempotencyResult result);

   
    void release(String idempotencyKey);
}