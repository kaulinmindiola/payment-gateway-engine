package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferMoneyCommand;
import com.paymentgateway.engine.domain.model.Transaction;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final TransferMoney transferMoney;

    public PaymentController(TransferMoney transferMoney) {
        this.transferMoney = transferMoney;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        TransferMoneyCommand command = TransferMoneyCommand.of(
                request.sourceAccountId(), userId, request.transferType(),
                request.targetAccountId(), request.amount(), idempotencyKey);

        Transaction transaction = transferMoney.execute(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(transaction));
    }
}