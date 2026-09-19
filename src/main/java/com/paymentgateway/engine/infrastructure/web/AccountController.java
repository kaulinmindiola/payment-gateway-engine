package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.CreateAccount;
import com.paymentgateway.engine.application.usecase.CreateAccountCommand;
import com.paymentgateway.engine.application.usecase.GetAccount;
import com.paymentgateway.engine.application.usecase.GetAccountQuery;
import com.paymentgateway.engine.domain.model.Account;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final CreateAccount createAccount;
    private final GetAccount getAccount;

    public AccountController(CreateAccount createAccount, GetAccount getAccount) {
        this.createAccount = createAccount;
        this.getAccount = getAccount;
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
}