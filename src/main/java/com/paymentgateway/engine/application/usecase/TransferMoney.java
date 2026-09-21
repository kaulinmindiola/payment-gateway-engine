package com.paymentgateway.engine.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.engine.application.exception.IdempotencyConflictException;
import com.paymentgateway.engine.application.exception.UnsupportedTransferTypeException;
import com.paymentgateway.engine.application.handler.InternalTransferHandler;
import com.paymentgateway.engine.application.handler.TransferCommand;
import com.paymentgateway.engine.domain.exception.DomainException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransferType;
import com.paymentgateway.engine.domain.port.IdempotencyClaim;
import com.paymentgateway.engine.domain.port.IdempotencyPort;
import com.paymentgateway.engine.domain.port.IdempotencyResult;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TransferMoney {

    private final InternalTransferHandler internalTransferHandler;
    private final IdempotencyPort idempotencyPort;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private final ObjectMapper objectMapper;

    public TransferMoney(InternalTransferHandler internalTransferHandler, IdempotencyPort idempotencyPort,
                          TransactionRepositoryPort transactionRepositoryPort, ObjectMapper objectMapper) {
        this.internalTransferHandler = internalTransferHandler;
        this.idempotencyPort = idempotencyPort;
        this.transactionRepositoryPort = transactionRepositoryPort;
        this.objectMapper = objectMapper;
    }

    public TransferOutcome execute(TransferMoneyCommand command) {
        IdempotencyClaim claim = idempotencyPort.tryBegin(command.getIdempotencyKey());
        return switch (claim.getStatus()) {
            case ACQUIRED -> executeAndCache(command);
            case IN_PROGRESS -> throw new IdempotencyConflictException(command.getIdempotencyKey());
            case COMPLETED, FAILED ->
                    new TransferOutcome.Replayed(claim.getCachedHttpStatus(), claim.getCachedResponseBody());
        };
    }

    private TransferOutcome executeAndCache(TransferMoneyCommand command) {
        try {
            Transaction transaction = dispatch(command);
            cacheSuccess(command.getIdempotencyKey(), transaction);
            return new TransferOutcome.Executed(transaction);
        } catch (DataIntegrityViolationException duplicate) {
            // RISK-003/ADR-0003: Redis estaba caído (fallback en tryBegin ya
            // dejó pasar). Postgres rechazó el INSERT por el UNIQUE de
            // idempotency_key -- la Transaction original YA existe. Se
            // devuelve tal cual, logrando idempotencia real incluso sin caché.
            return transactionRepositoryPort.findByIdempotencyKey(command.getIdempotencyKey())
                    .<TransferOutcome>map(TransferOutcome.Executed::new)
                    .orElseThrow(() -> duplicate); // defensivo -- no debería alcanzarse
        } catch (DomainException ex) {
            cacheFailure(command.getIdempotencyKey(), ex);
            throw ex; // primera vez: GlobalExceptionHandler responde normalmente
        }
        // UnsupportedTransferTypeException / IllegalArgumentException: NO se
        // cachean (Decisión 4) -- ver nota sobre key atascada en IN_PROGRESS
        // hasta TTL, aceptada como Opción A.
    }

    private Transaction dispatch(TransferMoneyCommand command) {
        return switch (command.getTransferType()) {
            case INTERNAL -> internalTransferHandler.handle(toInternalCommand(command));
            case EXTERNAL -> throw new UnsupportedTransferTypeException(TransferType.EXTERNAL);
        };
    }

    private void cacheSuccess(String idempotencyKey, Transaction transaction) {
        String body = serialize(CachedTransferPayload.from(transaction));
        idempotencyPort.complete(idempotencyKey,
                IdempotencyResult.of(IdempotencyResult.Outcome.COMPLETED, HttpStatus.CREATED.value(), body));
    }

    private void cacheFailure(String idempotencyKey, DomainException ex) {
        int httpStatus = httpStatusFor(ex);
        CachedProblemPayload payload = new CachedProblemPayload(
                "https://payment-gateway-engine/errors/" + typeSlugFor(ex),
                HttpStatus.valueOf(httpStatus).getReasonPhrase(), httpStatus, ex.getMessage());
        idempotencyPort.complete(idempotencyKey,
                IdempotencyResult.of(IdempotencyResult.Outcome.FAILED, httpStatus, serialize(payload)));
    }

    // DUPLICACIÓN CONOCIDA Y DOCUMENTADA: espeja un subconjunto acotado del
    // mapeo de GlobalExceptionHandler (infrastructure/web/exception/), limitado
    // a los DomainException que InternalTransferHandler puede lanzar.
    // application/ no puede depender de infrastructure/ (ArchUnit) -- dado el
    // contrato ya fijo de IdempotencyPort (httpStatus:int, Fase 2), esta
    // duplicación acotada es estructuralmente necesaria. Si se añade un nuevo
    // DomainException con status distinto en este flujo, actualizar AMBOS lugares.
    private int httpStatusFor(DomainException ex) {
        return (ex instanceof OwnershipViolationException) ? HttpStatus.FORBIDDEN.value() : HttpStatus.UNPROCESSABLE_ENTITY.value();
    }

    private String typeSlugFor(DomainException ex) {
        return (ex instanceof OwnershipViolationException) ? "ownership-violation" : "business-rule-violation";
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency cache payload", e);
        }
    }

    private TransferCommand toInternalCommand(TransferMoneyCommand command) {
        if (command.getTargetAccountId() == null) {
            throw new IllegalArgumentException("targetAccountId is required for INTERNAL transfers");
        }
        return TransferCommand.forInternal(
                command.getSourceAccountId(), command.getRequestingUserId(),
                command.getTargetAccountId(), command.getAmount(), command.getIdempotencyKey());
    }
}