package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.GetTransaction;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.exception.TransactionNotFoundException;
import com.paymentgateway.engine.domain.model.Transaction;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetTransaction getTransaction;

    @Test
    void get_asOwner_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        Transaction tx = Transaction.createInternal(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), "key-1");
        given(getTransaction.execute(any())).willReturn(tx);

        mockMvc.perform(get("/api/v1/transactions/{id}", tx.getId())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tx.getId().toString()));
    }

    @Test
    void get_asNonOwner_returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        given(getTransaction.execute(any())).willThrow(new OwnershipViolationException(txId.toString()));

        mockMvc.perform(get("/api/v1/transactions/{id}", txId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/ownership-violation")));
    }

    @Test
    void get_forNonExistentTransaction_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        given(getTransaction.execute(any())).willThrow(new TransactionNotFoundException(txId.toString()));

        mockMvc.perform(get("/api/v1/transactions/{id}", txId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/transaction-not-found")));
    }

    @Test
    void globalTransactionListing_doesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.traceId").exists());
    }
}