package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.GetTransaction;
import com.paymentgateway.engine.application.usecase.GetTransactionQuery;
import com.paymentgateway.engine.domain.model.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@SecurityRequirement(name = "userId")
@Tag(name = "Transactions")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final GetTransaction getTransaction;

    public TransactionController(GetTransaction getTransaction) {
        this.getTransaction = getTransaction;
    }

    @Operation(summary = "Get transaction details")
    @ApiResponse(responseCode = "200", description = "Transaction details")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> get(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {

        Transaction transaction = getTransaction.execute(GetTransactionQuery.of(id, userId));

        return ResponseEntity.ok(TransactionResponse.from(transaction));
    }
}