package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.GetTransaction;
import com.paymentgateway.engine.application.usecase.GetTransactionQuery;
import com.paymentgateway.engine.domain.model.Transaction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final GetTransaction getTransaction;

    public TransactionController(GetTransaction getTransaction) {
        this.getTransaction = getTransaction;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> get(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {

        Transaction transaction = getTransaction.execute(GetTransactionQuery.of(id, userId));

        return ResponseEntity.ok(TransactionResponse.from(transaction));
    }
}