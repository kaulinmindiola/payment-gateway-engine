package com.paymentgateway.engine.infrastructure.web.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.engine.application.exception.IdempotencyConflictException;
import com.paymentgateway.engine.application.usecase.*;
import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.exception.InsufficientBalanceException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.model.TransferType;
import com.paymentgateway.engine.infrastructure.adapter.http.AuthorizationUnavailableException;
import com.paymentgateway.engine.infrastructure.web.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BR-011: cada código de error cumple el contrato RFC 7807 completo
 * {type, title, status, detail, instance, traceId}, a través de la cadena
 * HTTP real (TraceIdFilter + controller + GlobalExceptionHandler).
 */
@WebMvcTest(controllers = {AccountController.class, PaymentController.class,
        TransactionController.class, ExternalBankController.class})
class ErrorContractTest {

    private static final String TYPE_BASE = "https://payment-gateway-engine/errors/";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CreateAccount createAccount;
    @MockBean private GetAccount getAccount;
    @MockBean private GetTransactionHistory getTransactionHistory;
    @MockBean private TransferMoney transferMoney;
    @MockBean private GetTransaction getTransaction;
    @MockBean private GetExternalBank getExternalBank;
    @MockBean private GetExternalBanks getExternalBanks;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private ResultActions getAccountRequest(String traceId) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                .header("X-User-Id", userId.toString())
                .header("X-Trace-Id", traceId));
    }

    private ResultActions transferRequest(String traceId) throws Exception {
        String body = objectMapper.writeValueAsString(new TransferRequest(
                UUID.randomUUID(), TransferType.INTERNAL, UUID.randomUUID(),
                null, null, null, new BigDecimal("10.00")));
        return mockMvc.perform(post("/api/v1/payments/transfer")
                .header("X-User-Id", userId.toString())
                .header("X-Idempotency-Key", "contract-" + traceId)
                .header("X-Trace-Id", traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void assertContract(ResultActions result, HttpStatus status, String slug,
                                String instance, String traceId) throws Exception {
        result.andExpect(status().is(status.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + slug))
                .andExpect(jsonPath("$.title").value(status.getReasonPhrase()))
                .andExpect(jsonPath("$.status").value(status.value()))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(instance))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(header().string("X-Trace-Id", traceId));
    }

    private String accountPath() {
        return "/api/v1/accounts/" + accountId;
    }

    @Test
    void code400_missingUserIdHeader() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                .header("X-Trace-Id", "contract-400"));
        assertContract(result, HttpStatus.BAD_REQUEST, "missing-required-header", accountPath(), "contract-400");
    }

    @Test
    void code403_ownershipViolation() throws Exception {
        given(getAccount.execute(any())).willThrow(new OwnershipViolationException(accountId.toString()));
        assertContract(getAccountRequest("contract-403"), HttpStatus.FORBIDDEN,
                "ownership-violation", accountPath(), "contract-403");
    }

    @Test
    void code404_accountNotFound() throws Exception {
        given(getAccount.execute(any())).willThrow(new AccountNotFoundException(accountId.toString()));
        assertContract(getAccountRequest("contract-404"), HttpStatus.NOT_FOUND,
                "account-not-found", accountPath(), "contract-404");
    }

    @Test
    void code409_idempotencyKeyInProgress() throws Exception {
        given(transferMoney.execute(any())).willThrow(new IdempotencyConflictException("contract-key"));
        assertContract(transferRequest("contract-409"), HttpStatus.CONFLICT,
                "idempotency-conflict", "/api/v1/payments/transfer", "contract-409");
    }

    @Test
    void code422_businessRuleViolation() throws Exception {
        given(transferMoney.execute(any())).willThrow(new InsufficientBalanceException(accountId.toString()));
        assertContract(transferRequest("contract-422"), HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation", "/api/v1/payments/transfer", "contract-422");
    }

    @Test
    void code503_authorizationProviderUnavailable() throws Exception {
        given(transferMoney.execute(any()))
                .willThrow(new AuthorizationUnavailableException("internal: provider returned 503", null));
        ResultActions result = transferRequest("contract-503");

        assertContract(result, HttpStatus.SERVICE_UNAVAILABLE,
                "authorization-provider-unavailable", "/api/v1/payments/transfer", "contract-503");
        result.andExpect(jsonPath("$.detail").value(not(containsString("internal"))));
    }

    @Test
    void code500_unexpectedError_doesNotLeakInternals() throws Exception {
        given(getAccount.execute(any())).willThrow(new IllegalStateException("secret: connection string"));
        ResultActions result = getAccountRequest("contract-500");

        assertContract(result, HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", accountPath(), "contract-500");
        result.andExpect(jsonPath("$.detail").value(not(containsString("secret"))));
    }
}