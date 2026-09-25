package com.paymentgateway.engine.infrastructure.web.exception;

import com.paymentgateway.engine.application.exception.IdempotencyConflictException;
import com.paymentgateway.engine.application.exception.UnsupportedTransferTypeException;
import com.paymentgateway.engine.domain.exception.*;
import com.paymentgateway.engine.infrastructure.adapter.http.AuthorizationUnavailableException;
import com.paymentgateway.engine.infrastructure.filter.TraceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest requestTo(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @BeforeEach
    void setTraceId() {
        MDC.put(TraceContext.MDC_KEY, "unit-trace-1");
    }

    @AfterEach
    void clearTraceId() {
        MDC.remove(TraceContext.MDC_KEY);
    }

    @Test
    void everyProblem_includesTraceIdFromMdc() {
        ProblemDetail problem = handler.handleAccountNotFound(
                new AccountNotFoundException("acc-1"), requestTo("/api/v1/accounts/acc-1"));

        assertThat(problem.getProperties()).containsEntry("traceId", "unit-trace-1");
    }

    @Test
    void handleUnreadableBody_mapsTo400WithoutLeakingParserDetails() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Cannot deserialize value of type `java.math.BigDecimal`",
                new MockHttpInputMessage(new byte[0]));

        ProblemDetail problem = handler.handleUnreadableBody(ex, requestTo("/api/v1/payments/transfer"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType().toString()).endsWith("/errors/malformed-request-body");
        assertThat(problem.getDetail()).doesNotContain("java.math");
    }

    @Test
    void handleUnexpected_withUnknownException_returns500WithoutLeakingInternals() {
        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
                new IllegalStateException("secret internal detail: db password"), requestTo("/api/v1/accounts"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getType().toString()).endsWith("/errors/internal-error");
        assertThat(response.getBody().getDetail()).doesNotContain("secret");
        assertThat(response.getBody().getProperties()).containsEntry("traceId", "unit-trace-1");
    }

    @Test
    void handleUnexpected_withSpringErrorResponse_keepsItsOwnStatus() {
        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
                new NoResourceFoundException(HttpMethod.GET, "api/v1/transactions"), requestTo("/api/v1/transactions"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getType().toString()).endsWith("/errors/not-found");
    }

    @Test
    void handleUserNotFound_mapsTo404WithCorrectTypeAndInstance() {
        UserNotFoundException ex = new UserNotFoundException("11111111-1111-1111-1111-111111111111");
        ProblemDetail problem = handler.handleUserNotFound(ex, requestTo("/api/v1/accounts"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType().toString()).endsWith("/errors/user-not-found");
        assertThat(problem.getDetail()).contains("11111111-1111-1111-1111-111111111111");
        assertThat(problem.getInstance().toString()).isEqualTo("/api/v1/accounts");
    }

    @Test
    void handleAccountNotFound_mapsTo404() {
        AccountNotFoundException ex = new AccountNotFoundException("acc-1");
        ProblemDetail problem = handler.handleAccountNotFound(ex, requestTo("/api/v1/accounts/acc-1"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType().toString()).endsWith("/errors/account-not-found");
    }

    @Test
    void handleUnsupportedTransferType_mapsTo400() {
        UnsupportedTransferTypeException ex =
                new UnsupportedTransferTypeException(com.paymentgateway.engine.domain.model.TransferType.EXTERNAL);
        ProblemDetail problem = handler.handleUnsupportedTransferType(ex, requestTo("/api/v1/payments/transfer"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).endsWith("/errors/unsupported-transfer-type");
    }

    @Test
    void handleOwnershipViolation_mapsTo403() {
        OwnershipViolationException ex = new OwnershipViolationException("acc-1");
        ProblemDetail problem = handler.handleOwnershipViolation(ex, requestTo("/api/v1/accounts/acc-1"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getType().toString()).endsWith("/errors/ownership-violation");
    }

    @Test
    void handleDomainException_catchAll_mapsUnhandledSubtypeTo422() {
        InvalidAmountException ex = new InvalidAmountException("Amount must be strictly positive");
        ProblemDetail problem = handler.handleDomainException(ex, requestTo("/api/v1/accounts"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getType().toString()).endsWith("/errors/business-rule-violation");
        assertThat(problem.getDetail()).isEqualTo("Amount must be strictly positive");
    }

    @Test
    void handleIdempotencyConflict_mapsTo409() {
        IdempotencyConflictException ex = new IdempotencyConflictException("dup-key");
        ProblemDetail problem = handler.handleIdempotencyConflict(ex, requestTo("/api/v1/payments/transfer"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType().toString()).endsWith("/errors/idempotency-conflict");
    }

    @Test
    void handleBlankHeader_mapsTo400() {
        BlankHeaderException ex = new BlankHeaderException("X-Idempotency-Key");
        ProblemDetail problem = handler.handleBlankHeader(ex, requestTo("/api/v1/payments/transfer"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).endsWith("/errors/invalid-request-parameter");
    }

    @Test
    void handleExternalBankNotFound_mapsTo404() {
        ExternalBankNotFoundException ex = new ExternalBankNotFoundException("bank-1");
        ProblemDetail problem = handler.handleExternalBankNotFound(ex, requestTo("/api/v1/external-banks/bank-1"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType().toString()).endsWith("/errors/external-bank-not-found");
    }

    @Test
    void handleAuthorizationTechnical_mapsTo503WithoutLeakingInternalMessage() {
        AuthorizationUnavailableException ex =
                new AuthorizationUnavailableException("internal: http://provider:8089 returned 503", null);
        ProblemDetail problem = handler.handleAuthorizationTechnical(ex, requestTo("/api/v1/payments/transfer"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getType().toString()).endsWith("/errors/authorization-provider-unavailable");
        assertThat(problem.getDetail()).doesNotContain("http://provider");
    }

    @Test
    void handleInvalidQueryParameter_mapsTo400() {
        InvalidQueryParameterException ex = new InvalidQueryParameterException("dateFrom cannot be after dateTo", null);
        ProblemDetail problem = handler.handleInvalidQueryParameter(ex, requestTo("/api/v1/accounts/123/transactions"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).endsWith("/errors/invalid-request-parameter");
        assertThat(problem.getDetail()).isEqualTo("dateFrom cannot be after dateTo");
    }
}