package com.paymentgateway.engine.domain.port;

import com.paymentgateway.engine.domain.model.TransactionLog;

public interface TransactionLogRepositoryPort {
    void save(TransactionLog log);
}