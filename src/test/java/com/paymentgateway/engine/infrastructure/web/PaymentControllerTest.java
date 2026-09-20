package com.paymentgateway.engine.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.engine.application.exception.UnsupportedTransferTypeException;
import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.domain.exception.InsufficientBalanceException;
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
                new TransferRequest(sourceId, TransferType.INTERNAL, targetId, new BigDecimal("10.00")));
    }

    @Test
    void transfer_validInternalRequest_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Transaction tx = Transaction.createInternal(sourceId, targetId, new BigDecimal("10.00"), "key-1");
        given(transferMoney.execute(any())).willReturn(tx);

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
    void transfer_internalWithoutTargetAccountId_returns400FromBeanValidation() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(
                new TransferRequest(UUID.randomUUID(), TransferType.INTERNAL, null, new BigDecimal("10.00")));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-key-2")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-body")));
    }

    @Test
    void transfer_externalTransferType_returns400UnsupportedTransferType() throws Exception {
        UUID userId = UUID.randomUUID();
        given(transferMoney.execute(any())).willThrow(new UnsupportedTransferTypeException(TransferType.EXTERNAL));
        String body = objectMapper.writeValueAsString(
                new TransferRequest(UUID.randomUUID(), TransferType.EXTERNAL, null, new BigDecimal("10.00")));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-key-3")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/unsupported-transfer-type")));
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
                .willThrow(new com.paymentgateway.engine.domain.exception.SelfTransferException(UUID.randomUUID().toString()));

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
                .willThrow(new com.paymentgateway.engine.domain.exception.InactiveAccountException(UUID.randomUUID().toString()));

        mockMvc.perform(post("/api/v1/payments/transfer")
                        .header("X-User-Id", userId.toString())
                        .header("X-Idempotency-Key", "idem-key-6")
                        .contentType("application/json")
                        .content(validInternalBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/business-rule-violation")));
        }
}