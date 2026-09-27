package com.paymentgateway.engine.infrastructure.web.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.engine.application.usecase.CreateAccount;
import com.paymentgateway.engine.application.usecase.GetAccount;
import com.paymentgateway.engine.application.usecase.GetTransactionHistory;
import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.infrastructure.filter.TraceContext;
import com.paymentgateway.engine.infrastructure.web.AccountController;
import com.paymentgateway.engine.testsupport.LogCapture;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 */
@WebMvcTest(AccountController.class)
class TraceIdLogCorrelationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CreateAccount createAccount;
    @MockBean private GetAccount getAccount;
    @MockBean private GetTransactionHistory getTransactionHistory;

    private MvcResult getAccountWithoutTraceHeader() throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andReturn();
    }

    private String bodyTraceId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("traceId").asText();
    }

    private List<ILoggingEvent> eventsAt(LogCapture capture, Level level) {
        return capture.events().stream().filter(e -> e.getLevel() == level).toList();
    }

    @Test
    void generatedTraceId_isIdenticalInResponseBodyHeaderAndWarnLog() throws Exception {
        given(getAccount.execute(any())).willThrow(new AccountNotFoundException("x"));

        try (LogCapture capture = LogCapture.of(GlobalExceptionHandler.class)) {
            MvcResult result = getAccountWithoutTraceHeader();

            String bodyTraceId = bodyTraceId(result);
            assertThat(bodyTraceId).isNotBlank();
            assertThat(result.getResponse().getHeader(TraceContext.HEADER)).isEqualTo(bodyTraceId);

            List<ILoggingEvent> warns = eventsAt(capture, Level.WARN);
            assertThat(warns).hasSize(1);
            assertThat(warns.get(0).getMDCPropertyMap())
                    .as("El traceId del log debe ser EXACTAMENTE el de la respuesta")
                    .containsEntry(TraceContext.MDC_KEY, bodyTraceId);
        }
    }

    @Test
    void unexpectedError_errorLogCarriesSameTraceIdAndStackTrace() throws Exception {
        given(getAccount.execute(any())).willThrow(new IllegalStateException("boom"));

        try (LogCapture capture = LogCapture.of(GlobalExceptionHandler.class)) {
            MvcResult result = getAccountWithoutTraceHeader();

            List<ILoggingEvent> errors = eventsAt(capture, Level.ERROR);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMDCPropertyMap()).containsEntry(TraceContext.MDC_KEY, bodyTraceId(result));
            assertThat(errors.get(0).getThrowableProxy())
                    .as("Un error desconocido se registra CON stack trace")
                    .isNotNull();
        }
    }

    @Test
    void consecutiveRequests_getDistinctTraceIds_andMdcIsClearedAfterEach() throws Exception {
        given(getAccount.execute(any())).willThrow(new AccountNotFoundException("x"));

        String first = bodyTraceId(getAccountWithoutTraceHeader());
        // MockMvc ejecuta la request en el hilo del test: si el filtro no
        // limpiara el MDC, aquí seguiría el traceId anterior.
        assertThat(MDC.get(TraceContext.MDC_KEY)).isNull();

        String second = bodyTraceId(getAccountWithoutTraceHeader());
        assertThat(second).isNotEqualTo(first);
    }
}