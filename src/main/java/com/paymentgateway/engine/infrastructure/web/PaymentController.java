package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferMoneyCommand;
import com.paymentgateway.engine.application.usecase.TransferOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    public ResponseEntity<?> transfer(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.paymentgateway.engine.infrastructure.web.exception.BlankHeaderException("X-Idempotency-Key");
        }

        TransferMoneyCommand command = switch (request.transferType()) {
            case INTERNAL -> TransferMoneyCommand.forInternal(
                    request.sourceAccountId(), userId, request.targetAccountId(), request.amount(), idempotencyKey);
            case EXTERNAL -> TransferMoneyCommand.forExternal(
                    request.sourceAccountId(), userId, request.targetProviderId(), request.targetBankId(),
                    request.targetExternalReference(), request.amount(), idempotencyKey);
        };

        TransferOutcome outcome = transferMoney.execute(command);

        return switch (outcome) {
            case TransferOutcome.Executed executed ->
                    ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(executed.transaction()));
            case TransferOutcome.Replayed replayed ->
                    ResponseEntity.status(replayed.httpStatus())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(replayed.responseBody());
        };
    }
}