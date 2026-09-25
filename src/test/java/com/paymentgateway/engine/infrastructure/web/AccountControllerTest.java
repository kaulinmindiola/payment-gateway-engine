package com.paymentgateway.engine.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.engine.application.usecase.CreateAccount;
import com.paymentgateway.engine.application.usecase.GetAccount;
import com.paymentgateway.engine.application.usecase.GetTransactionHistory;
import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.exception.UserNotFoundException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransactionStatus;
import com.paymentgateway.engine.domain.model.TransferType;
import com.paymentgateway.engine.domain.port.PageResult;
import com.paymentgateway.engine.domain.port.TransactionHistoryQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @MockBean
    private GetTransactionHistory getTransactionHistory;

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
                .andExpect(jsonPath("$.type").value(endsWith("/errors/missing-required-header")));
    }

    @Test
    void create_withMalformedUserIdHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", "not-a-uuid")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(new BigDecimal("10.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-parameter")));
    }

    @Test
    void create_withNegativeBalance_returns400FromBeanValidation() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateAccountRequest(new BigDecimal("-1.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-body")));
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
                .andExpect(jsonPath("$.type").value(endsWith("/errors/user-not-found")));
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
                .andExpect(jsonPath("$.type").value(endsWith("/errors/ownership-violation")));
    }

    @Test
    void get_forNonExistentAccount_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        given(getAccount.execute(any())).willThrow(new AccountNotFoundException(accountId.toString()));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/account-not-found")));
    }

    @Test
    void get_withMalformedPathId_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/accounts/{id}", "not-a-uuid")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-parameter")));
    }

    // ---- GET /api/v1/accounts/{id}/transactions ----

    private Transaction sampleTransaction() {
        return Transaction.createInternal(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), "key-h");
    }

    @Test
    void history_asOwner_returns200WithPaginationEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Transaction tx = sampleTransaction();
        given(getTransactionHistory.execute(any(), eq(userId)))
                .willReturn(new PageResult<>(List.of(tx), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(tx.getId().toString()))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].idempotencyKey").doesNotExist()) // misma representación que GET /transactions/{id}
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void history_withoutParams_appliesDefaultsPage0Size20() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        given(getTransactionHistory.execute(any(), any())).willReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<TransactionHistoryQuery> captor = ArgumentCaptor.forClass(TransactionHistoryQuery.class);
        verify(getTransactionHistory).execute(captor.capture(), eq(userId));
        assertThat(captor.getValue().page()).isZero();
        assertThat(captor.getValue().size()).isEqualTo(20);
        assertThat(captor.getValue().accountId()).isEqualTo(accountId);
    }

    @Test
    void history_bindsAllFiltersIntoQuery() throws Exception {
        UUID userId = UUID.randomUUID();
        given(getTransactionHistory.execute(any(), any())).willReturn(new PageResult<>(List.of(), 1, 50, 0, 0));

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", UUID.randomUUID())
                        .header("X-User-Id", userId.toString())
                        .queryParam("page", "1")
                        .queryParam("size", "50")
                        .queryParam("status", "FAILED")
                        .queryParam("transferType", "EXTERNAL")
                        .queryParam("dateFrom", "2026-01-01T00:00:00Z")
                        .queryParam("dateTo", "2026-02-01T00:00:00Z"))
                .andExpect(status().isOk());

        ArgumentCaptor<TransactionHistoryQuery> captor = ArgumentCaptor.forClass(TransactionHistoryQuery.class);
        verify(getTransactionHistory).execute(captor.capture(), eq(userId));
        TransactionHistoryQuery q = captor.getValue();
        assertThat(q.page()).isEqualTo(1);
        assertThat(q.size()).isEqualTo(50);
        assertThat(q.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(q.transferType()).isEqualTo(TransferType.EXTERNAL);
        assertThat(q.dateFrom()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(q.dateTo()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @ParameterizedTest(name = "[{index}] {0}={1} -> 400")
    @CsvSource({
            "size, 101",
            "size, 0",
            "page, -1",
            "page, abc",
            "status, UNKNOWN",
            "transferType, FOO",
            "dateFrom, not-a-date"
    })
    void history_withInvalidParam_returns400AndNeverCallsUseCase(String param, String value) throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .queryParam(param, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-parameter")));

        verifyNoInteractions(getTransactionHistory);
    }

    @Test
    void history_withDateFromAfterDateTo_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .queryParam("dateFrom", "2026-02-01T00:00:00Z")
                        .queryParam("dateTo", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-parameter")));

        verifyNoInteractions(getTransactionHistory);
    }

    @Test
    void history_forNonExistentAccount_returns404() throws Exception {
        UUID accountId = UUID.randomUUID();
        given(getTransactionHistory.execute(any(), any()))
                .willThrow(new AccountNotFoundException(accountId.toString()));

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/account-not-found")));
    }

    @Test
    void history_asNonOwner_returns403() throws Exception {
        UUID accountId = UUID.randomUUID();
        given(getTransactionHistory.execute(any(), any()))
                .willThrow(new OwnershipViolationException(accountId.toString()));

        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/ownership-violation")));
    }

    @Test
    void history_withoutUserIdHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/missing-required-header")));
    }
}