package com.paymentgateway.engine.infrastructure.web.exception;

import com.paymentgateway.engine.application.exception.IdempotencyConflictException;
import com.paymentgateway.engine.application.exception.UnsupportedTransferTypeException;
import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.exception.DomainException;
import com.paymentgateway.engine.domain.exception.ExternalBankNotFoundException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.exception.TransactionNotFoundException;
import com.paymentgateway.engine.domain.exception.UserNotFoundException;
import com.paymentgateway.engine.infrastructure.adapter.http.AuthorizationTechnicalException;
import com.paymentgateway.engine.infrastructure.filter.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * BR-011: todo 4xx/5xx en RFC 7807 {type, title, status, detail, instance, traceId}.
 * Errores conocidos -> WARN sin stack trace. Errores desconocidos -> ERROR con stack trace.
 * El traceId del body coincide con el del MDC, y por tanto con el de los logs de la request.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://payment-gateway-engine/errors/";

    // ---------------------------------------------------------------- 400

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request-parameter",
                "Parameter '" + ex.getName() + "' has an invalid value or format.", request, ex);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "missing-required-header",
                "Required header '" + ex.getHeaderName() + "' is missing.", request, ex);
    }

    @ExceptionHandler(UnsupportedTransferTypeException.class)
    public ProblemDetail handleUnsupportedTransferType(UnsupportedTransferTypeException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "unsupported-transfer-type", ex.getMessage(), request, ex);
    }

    @ExceptionHandler(BlankHeaderException.class)
    public ProblemDetail handleBlankHeader(BlankHeaderException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request-parameter", ex.getMessage(), request, ex);
    }

    @ExceptionHandler(InvalidQueryParameterException.class)
    public ProblemDetail handleInvalidQueryParameter(InvalidQueryParameterException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request-parameter", ex.getMessage(), request, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        // @AssertTrue a nivel de clase (TransferRequest) produce errores globales, no de campo.
        if (detail.isBlank()) {
            detail = ex.getBindingResult().getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        return problem(HttpStatus.BAD_REQUEST, "invalid-request-body", detail, request, ex);
    }

    // Body no parseable (JSON roto, tipo incorrecto, enum desconocido en el body).
    // El mensaje de Jackson NO se devuelve: puede exponer nombres de clases internas.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-request-body",
                "Request body is malformed or contains values of an invalid type.", request, ex);
    }

    // ---------------------------------------------------------------- 403

    @ExceptionHandler(OwnershipViolationException.class)
    public ProblemDetail handleOwnershipViolation(OwnershipViolationException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "ownership-violation", ex.getMessage(), request, ex);
    }

    // ---------------------------------------------------------------- 404

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "user-not-found", ex.getMessage(), request, ex);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "account-not-found", ex.getMessage(), request, ex);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleTransactionNotFound(TransactionNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "transaction-not-found", ex.getMessage(), request, ex);
    }

    @ExceptionHandler(ExternalBankNotFoundException.class)
    public ProblemDetail handleExternalBankNotFound(ExternalBankNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "external-bank-not-found", ex.getMessage(), request, ex);
    }

    // ---------------------------------------------------------------- 409

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "idempotency-conflict", ex.getMessage(), request, ex);
    }

    // ---------------------------------------------------------------- 422

    // Catch-all de reglas de negocio. Spring despacha al handler MÁS específico,
    // así que los DomainException con handler propio (403/404) nunca llegan aquí.
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule-violation", ex.getMessage(), request, ex);
    }

    // ---------------------------------------------------------------- 503

    @ExceptionHandler(AuthorizationTechnicalException.class)
    public ProblemDetail handleAuthorizationTechnical(AuthorizationTechnicalException ex, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "authorization-provider-unavailable",
                "External authorization provider is temporarily unavailable. The transfer was not executed "
                        + "and can be safely retried with the same X-Idempotency-Key.", request, ex);
    }

    // ---------------------------------------------------------------- resto

    /**
     * Excepciones de Spring MVC que ya declaran su propio código (ErrorResponse:
     * ruta inexistente -> 404, método no soportado -> 405, etc.) conservan ese
     * código y sus headers. Cualquier otra cosa es un bug -> 500 genérico.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            ProblemDetail body = problem(status, slugFor(status), errorResponse.getBody().getDetail(), request, ex);
            return ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(body);
        }

        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail body = build(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "An unexpected error occurred. Report the traceId to support.", request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    // ---------------------------------------------------------------- helpers

    /** Error conocido: una línea WARN con el mensaje interno (saneado) y el body RFC 7807. */
    private ProblemDetail problem(HttpStatusCode status, String typeSlug, String detail,
                                  HttpServletRequest request, Exception ex) {
        log.warn("{} {} -> {} {}: {}", request.getMethod(), request.getRequestURI(),
                status.value(), typeSlug, sanitize(ex.getMessage()));
        return build(status, typeSlug, detail, request);
    }

    private ProblemDetail build(HttpStatusCode status, String typeSlug, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + typeSlug));
        HttpStatus resolved = HttpStatus.resolve(status.value());
        problem.setTitle(resolved != null ? resolved.getReasonPhrase() : "Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        String traceId = TraceContext.current();
        if (traceId != null) {
            // Siempre presente en requests reales: TraceIdFilter tiene máxima precedencia.
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }

    private static String slugFor(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null
                ? resolved.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '-')
                : "request-error";
    }

    // Algunos mensajes contienen input del cliente: sin saltos de línea -> sin log injection.
    private static String sanitize(String message) {
        return message == null ? "" : message.replaceAll("[\\r\\n]", "_");
    }
}