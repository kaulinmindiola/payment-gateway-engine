package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.CreateAccount;
import com.paymentgateway.engine.application.usecase.CreateAccountCommand;
import com.paymentgateway.engine.application.usecase.GetAccount;
import com.paymentgateway.engine.application.usecase.GetAccountQuery;
import com.paymentgateway.engine.application.usecase.GetTransactionHistory;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.TransactionStatus;
import com.paymentgateway.engine.domain.model.TransferType;
import com.paymentgateway.engine.domain.port.TransactionHistoryQuery;
import com.paymentgateway.engine.infrastructure.web.exception.InvalidQueryParameterException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Accounts")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final CreateAccount createAccount;
    private final GetAccount getAccount;
    private final GetTransactionHistory getTransactionHistory;

    public AccountController(CreateAccount createAccount, GetAccount getAccount, 
                             GetTransactionHistory getTransactionHistory) {
        this.createAccount = createAccount;
        this.getAccount = getAccount;
        this.getTransactionHistory = getTransactionHistory;
    }

    @Operation(summary = "Create a new account")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @Parameter(description = "Requesting user ID") @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateAccountRequest request) {

        Account account = createAccount.execute(
                CreateAccountCommand.of(userId, request.initialBalance()));

        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @Operation(summary = "Get account details")
    @ApiResponse(responseCode = "200", description = "Account details")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> get(
            @Parameter(description = "Requesting user; must own the account") @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {

        Account account = getAccount.execute(GetAccountQuery.of(id, userId));

        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @Operation(summary = "Transaction history of an account",
            description = "Transactions where the account is the source OR the target, ordered by createdAt DESC "
                    + "(ties broken by id). Only the account owner can query it. There is no global "
                    + "transaction listing by design.")
    @ApiResponse(responseCode = "200", description = "Paginated history (same Transaction representation as GET /transactions/{id})")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @GetMapping("/{id}/transactions")
    public PageResponse<TransactionResponse> history(
            @Parameter(description = "Requesting user; must own the account") @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id,
            @Parameter(description = "Zero-based page index", schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "" + TransactionHistoryQuery.DEFAULT_SIZE) int size,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransferType transferType,
            @Parameter(description = "Inclusive lower bound, ISO-8601 UTC (e.g. 2026-01-01T00:00:00Z)")
            @RequestParam(required = false) Instant dateFrom,   
            @Parameter(description = "Exclusive upper bound, ISO-8601 UTC")
            @RequestParam(required = false) Instant dateTo) {   

        TransactionHistoryQuery query;
        try {
            query = new TransactionHistoryQuery(id, status, transferType, dateFrom, dateTo, page, size);
        } catch (IllegalArgumentException ex) {
            throw new InvalidQueryParameterException(ex.getMessage(), ex);
        }

        return PageResponse.from(getTransactionHistory.execute(query, userId).map(TransactionResponse::from));
    }
}