package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferMoneyCommand;
import com.paymentgateway.engine.application.usecase.TransferOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Payments")
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final TransferMoney transferMoney;

    public PaymentController(TransferMoney transferMoney) {
        this.transferMoney = transferMoney;
    }

    @Operation(summary = "Transfer money (INTERNAL or EXTERNAL)",
            description = "Idempotent via X-Idempotency-Key: repeating a completed request returns the original "
                    + "response without executing it again; a request still in progress returns 409. "
                    + "A provider DECLINED is a business result: 201 with status FAILED and failureReason DECLINED.")
    @ApiResponse(responseCode = "201", description = "Transfer executed, or declined by the provider",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionResponse.class)))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(
            @Parameter(description = "Requesting user; must own the source account")
            @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "Unique key per logical transfer; reuse it to retry safely")
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