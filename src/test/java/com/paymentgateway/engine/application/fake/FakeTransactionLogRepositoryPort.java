package com.paymentgateway.engine.application.fake;

import com.paymentgateway.engine.domain.model.TransactionLog;
import com.paymentgateway.engine.domain.port.TransactionLogRepositoryPort;

import java.util.ArrayList;
import java.util.List;

public class FakeTransactionLogRepositoryPort implements TransactionLogRepositoryPort {

    private final List<TransactionLog> storage = new ArrayList<>();

    @Override
    public void save(TransactionLog log) {
        storage.add(log);
    }

    public List<TransactionLog> all() { return storage; }
}