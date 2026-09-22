package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.ExternalBankView;
import com.paymentgateway.engine.application.usecase.GetExternalBank;
import com.paymentgateway.engine.application.usecase.GetExternalBanks;
import com.paymentgateway.engine.domain.exception.ExternalBankNotFoundException;
import com.paymentgateway.engine.domain.model.ExternalBankStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExternalBankController.class)
class ExternalBankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetExternalBank getExternalBank;

    @MockBean
    private GetExternalBanks getExternalBanks;

    @Test
    void getAll_withNoAuthHeader_returns200WithCatalog() throws Exception {
        ExternalBankView view = new ExternalBankView(
                UUID.randomUUID(), UUID.randomUUID(), "SWIFT-demo", "DE-001",
                "Demo Bank", "DE", "EUR", ExternalBankStatus.ACTIVE);
        given(getExternalBanks.execute()).willReturn(List.of(view));

        // Deliberadamente SIN header X-User-Id -- confirma que el catálogo
        // es realmente público (Sección 6 del contexto).
        mockMvc.perform(get("/api/v1/external-banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerCode").value("SWIFT-demo"))
                .andExpect(jsonPath("$[0].country").value("DE"));
    }

    @Test
    void getAll_withEmptyCatalog_returns200WithEmptyArray() throws Exception {
        given(getExternalBanks.execute()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/external-banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getById_forExistingBank_returns200() throws Exception {
        UUID bankId = UUID.randomUUID();
        ExternalBankView view = new ExternalBankView(
                bankId, UUID.randomUUID(), "RAILS-flaky", "ES-001",
                "Bank ES", "ES", "EUR", ExternalBankStatus.ACTIVE);
        given(getExternalBank.execute(bankId)).willReturn(view);

        mockMvc.perform(get("/api/v1/external-banks/{id}", bankId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bankId.toString()))
                .andExpect(jsonPath("$.providerCode").value("RAILS-flaky"));
    }

    @Test
    void getById_forUnknownBank_returns404() throws Exception {
        UUID bankId = UUID.randomUUID();
        given(getExternalBank.execute(bankId)).willThrow(new ExternalBankNotFoundException(bankId.toString()));

        mockMvc.perform(get("/api/v1/external-banks/{id}", bankId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/external-bank-not-found")));
    }

    @Test
    void getById_withMalformedId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/external-banks/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/errors/invalid-request-parameter")));
    }
}