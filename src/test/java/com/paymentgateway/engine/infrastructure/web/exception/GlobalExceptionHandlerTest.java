package com.paymentgateway.engine.infrastructure.web.exception;

import com.paymentgateway.engine.application.exception.IdempotencyConflictException;
import com.paymentgateway.engine.application.exception.UnsupportedTransferTypeException;
import com.paymentgateway.engine.application.exception.UnsupportedTransferTypeException;
import com.paymentgateway.engine.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest requestTo(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
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
        // InvalidAmountException NO tiene handler propio -- debe caer en el catch-all.
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
}