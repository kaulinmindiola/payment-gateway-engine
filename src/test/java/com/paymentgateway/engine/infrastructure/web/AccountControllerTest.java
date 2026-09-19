package com.paymentgateway.engine.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.engine.application.usecase.CreateAccount;
import com.paymentgateway.engine.application.usecase.GetAccount;
import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.exception.UserNotFoundException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

   @MockBean
    private CreateAccount createAccount;

    @MockBean
    private GetAccount getAccount;

    // ---- POST /api/v1/accounts ----

    @Test
    void create_withValidRequest_returns201WithAccountBody() throws Exception {
        UUID userId = UUID.randomUUID();
        Account account = Account.createNew(userId, new BigDecimal("100.00"));
        given(createAccount.execute(any())).willReturn(account);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(new BigDecimal("100.00")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(account.getId().toString()))
                .andExpect(jsonPath("$.ownerId").value(userId.toString()))
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_withMissingUserIdHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(new BigDecimal("10.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/errors/missing-required-header")));
    }

    @Test
    void create_withMalformedUserIdHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", "not-a-uuid")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(new BigDecimal("10.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/errors/invalid-request-parameter")));
    }

    @Test
    void create_withNegativeBalance_returns400FromBeanValidation() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(new BigDecimal("-1.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/errors/invalid-request-body")));
    }

    @Test
    void create_forNonExistentUser_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        given(createAccount.execute(any())).willThrow(new UserNotFoundException(userId.toString()));

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(new BigDecimal("10.00")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/errors/user-not-found")));
    }

    // ---- GET /api/v1/accounts/{id} ----

    @Test
    void get_asOwner_returns200WithAccountBody() throws Exception {
        UUID userId = UUID.randomUUID();
        Account account = Account.createNew(userId, new BigDecimal("50.00"));
        given(getAccount.execute(any())).willReturn(account);

        mockMvc.perform(get("/api/v1/accounts/{id}", account.getId())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void get_asNonOwner_returns403() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        given(getAccount.execute(any())).willThrow(new OwnershipViolationException(accountId.toString()));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .header("X-User-Id", requesterId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/errors/ownership-violation")));
    }

    @Test
    void get_forNonExistentAccount_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        given(getAccount.execute(any())).willThrow(new AccountNotFoundException(accountId.toString()));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/errors/account-not-found")));
    }

    @Test
    void get_withMalformedPathId_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/accounts/{id}", "not-a-uuid")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/errors/invalid-request-parameter")));
    }
}