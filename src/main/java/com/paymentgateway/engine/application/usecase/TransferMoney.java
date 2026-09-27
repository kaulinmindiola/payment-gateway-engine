package com.paymentgateway.engine.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.engine.application.exception.IdempotencyConflictException;
import com.paymentgateway.engine.application.handler.ExternalTransferHandler;
import com.paymentgateway.engine.application.handler.InternalTransferHandler;
import com.paymentgateway.engine.application.handler.TransferCommand;
import com.paymentgateway.engine.domain.exception.DomainException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.port.IdempotencyClaim;
import com.paymentgateway.engine.domain.port.IdempotencyPort;
import com.paymentgateway.engine.domain.port.IdempotencyResult;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;
import com.paymentgateway.engine.infrastructure.adapter.http.AuthorizationTechnicalException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TransferMoney {

    private final InternalTransferHandler internalTransferHandler;
    private final ExternalTransferHandler externalTransferHandler;
    private final IdempotencyPort idempotencyPort;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private final ObjectMapper objectMapper;

    public TransferMoney(InternalTransferHandler internalTransferHandler,
                         ExternalTransferHandler externalTransferHandler,
                         IdempotencyPort idempotencyPort,
                         TransactionRepositoryPort transactionRepositoryPort,
                         ObjectMapper objectMapper) {
        this.internalTransferHandler = internalTransferHandler;
        this.externalTransferHandler = externalTransferHandler;
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
            return transactionRepositoryPort.findByIdempotencyKey(command.getIdempotencyKey())
                    .<TransferOutcome>map(TransferOutcome.Executed::new)
                    .orElseThrow(() -> duplicate);
        } catch (DomainException ex) {
            cacheFailure(command.getIdempotencyKey(), ex);
            throw ex;
        } catch (AuthorizationTechnicalException ex) {
            // fallo TÉCNICO (timeout/5xx/circuito abierto).
            // NO se cachea como resultado terminal: se LIBERA la key para que
            // un reintento legítimo del cliente no espere el TTL de 24h.
            // Ninguna Transaction se persiste (ExternalTransferHandler revirtió
            // vía @Transactional).
            idempotencyPort.release(command.getIdempotencyKey());
            throw ex;
        }
    }

    private Transaction dispatch(TransferMoneyCommand command) {
        return switch (command.getTransferType()) {
            case INTERNAL -> internalTransferHandler.handle(toInternalCommand(command));
            case EXTERNAL -> externalTransferHandler.handle(toExternalCommand(command));
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
        return new TransferCommand.Internal(
                command.getSourceAccountId(), command.getRequestingUserId(),
                command.getTargetAccountId(), command.getAmount(), command.getIdempotencyKey());
    }

    // Campos EXTERNAL garantizados por TransferMoneyCommand.forExternal(...).
    private TransferCommand toExternalCommand(TransferMoneyCommand command) {
        return new TransferCommand.External(
                command.getSourceAccountId(), command.getRequestingUserId(), command.getTargetProviderId(),
                command.getTargetBankId(), command.getTargetExternalReference(),
                command.getAmount(), command.getIdempotencyKey());
    }
}