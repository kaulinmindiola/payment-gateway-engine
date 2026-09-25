package com.paymentgateway.engine.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferOutcome;
import com.paymentgateway.engine.domain.exception.InactiveAccountException;
import com.paymentgateway.engine.domain.exception.InsufficientBalanceException;
import com.paymentgateway.engine.domain.exception.SelfTransferException;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransferType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferMoney transferMoney;

    private String validInternalBody(UUID sourceId, UUID targetId) throws Exception {
        return objectMapper.writeValueAsString(
                new TransferRequest(sourceId, TransferType.INTERNAL, targetId, null, null, null, new BigDecimal("10.00")));
    }

    @Test
    void transfer_validInternalRequest_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Transaction tx = Transaction.createInternal(sourceId, targetId, new BigDecimal("10.00"), "key-1");
        given(transferMoney.execute(any())).willReturn(new TransferOutcome.Executed(tx));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-key-1")
                        .contentType("application/json")
                        .content(validInternalBody(sourceId, targetId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(tx.getId().toString()))
                .andExpect(jsonPath("$.transferType").value("INTERNAL"));
    }

    @Test
    void transfer_missingIdempotencyKeyHeader_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content(validInternalBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/missing-required-header")));
    }

    @Test
    void transfer_blankIdempotencyKeyHeader_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "")
                        .contentType("application/json")
                        .content(validInternalBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-parameter")));
    }

    @Test
    void transfer_internalWithoutTargetAccountId_returns400FromBeanValidation() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(
                new TransferRequest(UUID.randomUUID(), TransferType.INTERNAL, null, null, null, null, new BigDecimal("10.00")));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-key-2")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-body")));
    }

    @Test
    void transfer_insufficientBalance_returns422() throws Exception {
        UUID userId = UUID.randomUUID();
        given(transferMoney.execute(any()))
                .willThrow(new InsufficientBalanceException(UUID.randomUUID().toString()));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-key-4")
                        .contentType("application/json")
                        .content(validInternalBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/business-rule-violation")));
    }

    @Test
    void transfer_selfTransfer_returns422() throws Exception {
        UUID userId = UUID.randomUUID();
        given(transferMoney.execute(any()))
                .willThrow(new SelfTransferException(UUID.randomUUID().toString()));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-key-5")
                        .contentType("application/json")
                        .content(validInternalBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/business-rule-violation")));
    }

    @Test
    void transfer_inactiveAccount_returns422() throws Exception {
        UUID userId = UUID.randomUUID();
        given(transferMoney.execute(any()))
                .willThrow(new InactiveAccountException(UUID.randomUUID().toString()));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-key-6")
                        .contentType("application/json")
                        .content(validInternalBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/business-rule-violation")));
    }

    @Test
    void transfer_validExternalRequest_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        Transaction tx = Transaction.createExternal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "REF-001", new BigDecimal("10.00"), "key-ext-1");
        given(transferMoney.execute(any())).willReturn(new TransferOutcome.Executed(tx));

        String body = objectMapper.writeValueAsString(new TransferRequest(
                UUID.randomUUID(), TransferType.EXTERNAL, null, UUID.randomUUID(), UUID.randomUUID(),
                "REF-001", new BigDecimal("10.00")));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-ext-1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferType").value("EXTERNAL"));
    }

    @Test
    void transfer_externalWithoutRequiredFields_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new TransferRequest(
                UUID.randomUUID(), TransferType.EXTERNAL, null, null, null, null, new BigDecimal("10.00")));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-ext-2")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-body")));
    }
    @Test
        void transfer_withMalformedJsonBody_returns400ProblemDetailWithTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Idempotency-Key", "idem-malformed")
                        .contentType("application/json")
                        .content("{\"sourceAccountId\": \"not-a-uuid\", \"amount\": \"abc\""))   // JSON roto
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/malformed-request-body")))
                .andExpect(jsonPath("$.traceId").exists());
        }
}