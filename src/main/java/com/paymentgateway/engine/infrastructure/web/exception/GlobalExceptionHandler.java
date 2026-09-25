package com.paymentgateway.engine.infrastructure.web.exception;

import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.exception.DomainException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.exception.UserNotFoundException;
import com.paymentgateway.engine.infrastructure.adapter.http.AuthorizationTechnicalException;
import com.paymentgateway.engine.application.exception.UnsupportedTransferTypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TYPE_BASE = "https://payment-gateway-engine/errors/";

    // 400 — X-User-Id (u otro parámetro) con formato inválido (no parseable como UUID).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request-parameter",
                "Parameter '" + ex.getName() + "' has an invalid value or format.", request);
    }

    // 400 — X-User-Id ausente (BR-011: "ausente" siempre 400).
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "missing-required-header",
                "Required header '" + ex.getHeaderName() + "' is missing.", request);
    }

    // 400 — violaciones de Bean Validation en el body (Paso 3: CreateAccountRequest).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "invalid-request-body", detail, request);
    }

    // 400 -- header presente pero vacío (distinto de MissingRequestHeaderException,
    // que cubre AUSENCIA total del header).
    @ExceptionHandler(BlankHeaderException.class)
    public ProblemDetail handleBlankHeader(BlankHeaderException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request-parameter", ex.getMessage(), request);
    }

    // 400 -- parámetros de paginación/filtro fuera de rango (size > 100, page < 0, dateFrom > dateTo).
    @ExceptionHandler(InvalidQueryParameterException.class)
    public ProblemDetail handleInvalidQueryParameter(InvalidQueryParameterException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request-parameter", ex.getMessage(), request);
    }

    // 403 — BR-008 (y BR-006/BR-014 en fases futuras): violación de ownership.
    @ExceptionHandler(OwnershipViolationException.class)
    public ProblemDetail handleOwnershipViolation(OwnershipViolationException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "ownership-violation", ex.getMessage(), request);
    }

    // 404 — BR-012: X-User-Id no corresponde a un usuario existente.
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "user-not-found", ex.getMessage(), request);
    }

    // 404 — cuenta no encontrada.
    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "account-not-found", ex.getMessage(), request);
    }

        // 404 -- ExternalBank inexistente.
    @ExceptionHandler(com.paymentgateway.engine.domain.exception.ExternalBankNotFoundException.class)
    public ProblemDetail handleExternalBankNotFound(
            com.paymentgateway.engine.domain.exception.ExternalBankNotFoundException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "external-bank-not-found", ex.getMessage(), request);
    }

    // 400 — EXTERNAL todavía no está soportado en esta fase.
    @ExceptionHandler(UnsupportedTransferTypeException.class)
    public ProblemDetail handleUnsupportedTransferType(
            UnsupportedTransferTypeException ex,
            HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "unsupported-transfer-type",
                ex.getMessage(),
                request
        );
    }

    // 422 — catch-all de defensa en profundidad para invariantes de dominio
    // sin handler específico todavía (InvalidAmountException, InsufficientBalanceException,
    // InvalidTransactionTargetException — llegan con lógica real en Fases 5/8).
    // Spring despacha al handler MÁS ESPECÍFICO disponible: los tres de arriba
    // nunca caen aquí.
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule-violation", ex.getMessage(), request);
    }

    private ProblemDetail problem(HttpStatus status, String typeSlug, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + typeSlug));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        // traceId: pendiente -- requiere TraceIdFilter + MDC (Fase 10, Sección 14
        // del contexto). BR-011 lo exige; se añade allí, no aquí (ver Decisión 2).
        return problem;
    }
        // 404 -- BR-009: transacción no encontrada.
    @ExceptionHandler(com.paymentgateway.engine.domain.exception.TransactionNotFoundException.class)
    public ProblemDetail handleTransactionNotFound(
            com.paymentgateway.engine.domain.exception.TransactionNotFoundException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "transaction-not-found", ex.getMessage(), request);
    }
    // 409 -- BR-002: key en estado IN_PROGRESS.
    @ExceptionHandler(com.paymentgateway.engine.application.exception.IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(
            com.paymentgateway.engine.application.exception.IdempotencyConflictException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "idempotency-conflict", ex.getMessage(), request);
    }
    // 503 -- BR-011: fallo TÉCNICO del proveedor (timeout / 5xx / circuito abierto).
    // El detail es genérico a propósito: no se filtran mensajes internos
    // (URL del proveedor, estado del circuito) al cliente.
    @ExceptionHandler(AuthorizationTechnicalException.class)
    public ProblemDetail handleAuthorizationTechnical(AuthorizationTechnicalException ex, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "authorization-provider-unavailable",
                "External authorization provider is temporarily unavailable. The transfer was not executed "
                        + "and can be safely retried with the same X-Idempotency-Key.", request);
    }
    
}