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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

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

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateAccountRequest request) {

        Account account = createAccount.execute(
                CreateAccountCommand.of(userId, request.initialBalance()));

        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> get(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {

        Account account = getAccount.execute(GetAccountQuery.of(id, userId));

        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @GetMapping("/{id}/transactions")
    public PageResponse<TransactionResponse> history(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TransactionHistoryQuery.DEFAULT_SIZE) int size,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransferType transferType,
            @RequestParam(required = false) Instant dateFrom,   
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