package com.paymentgateway.engine.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.engine.application.exception.IdempotencyConflictException;
import com.paymentgateway.engine.application.handler.ExternalTransferHandler;
import com.paymentgateway.engine.application.handler.InternalTransferHandler;
import com.paymentgateway.engine.application.handler.TransferCommand;
import com.paymentgateway.engine.domain.exception.InsufficientBalanceException;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.port.IdempotencyClaim;
import com.paymentgateway.engine.domain.port.IdempotencyPort;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;
import com.paymentgateway.engine.infrastructure.adapter.http.AuthorizationTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class TransferMoneyTest {

    private InternalTransferHandler internalTransferHandler;
    private ExternalTransferHandler externalTransferHandler;
    private IdempotencyPort idempotencyPort;
    private TransactionRepositoryPort transactionRepositoryPort;
    private TransferMoney transferMoney;

    @BeforeEach
    void setUp() {
        internalTransferHandler = mock(InternalTransferHandler.class);
        externalTransferHandler = mock(ExternalTransferHandler.class);
        idempotencyPort = mock(IdempotencyPort.class);
        transactionRepositoryPort = mock(TransactionRepositoryPort.class);
        transferMoney = new TransferMoney(internalTransferHandler, externalTransferHandler,
                idempotencyPort, transactionRepositoryPort, new ObjectMapper());
    }

    private TransferMoneyCommand internalCommand(String idempotencyKey) {
        return TransferMoneyCommand.forInternal(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("10.00"), idempotencyKey);
    }

    private TransferMoneyCommand externalCommand(String idempotencyKey) {
        return TransferMoneyCommand.forExternal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "REF-001", new BigDecimal("10.00"), idempotencyKey);
    }

        @Test
        void execute_acquiredClaim_delegatesAndCachesSuccess() {
        given(idempotencyPort.tryBegin(anyString())).willReturn(IdempotencyClaim.acquired());
        Transaction tx = Transaction.createInternal(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), "key-1");
        given(internalTransferHandler.handle(any(TransferCommand.class))).willReturn(tx);

        TransferOutcome outcome = transferMoney.execute(internalCommand("key-1"));

        assertThat(outcome).isInstanceOf(TransferOutcome.Executed.class);
        assertThat(((TransferOutcome.Executed) outcome).transaction()).isSameAs(tx);
        verify(idempotencyPort).complete(eq("key-1"), any());
    }

    @Test
    void execute_inProgressClaim_throwsIdempotencyConflictException() {
        given(idempotencyPort.tryBegin(anyString())).willReturn(IdempotencyClaim.inProgress());

        assertThatThrownBy(() -> transferMoney.execute(internalCommand("key-2")))
                .isInstanceOf(IdempotencyConflictException.class);

        verifyNoInteractions(internalTransferHandler);
        verifyNoInteractions(externalTransferHandler);
    }

    @Test
    void execute_terminalCompletedClaim_returnsReplayedWithoutInvokingHandler() {
        IdempotencyClaim terminal = IdempotencyClaim.terminal(
                IdempotencyClaim.ClaimStatus.COMPLETED, 201, "{\"id\":\"cached\"}");
        given(idempotencyPort.tryBegin(anyString())).willReturn(terminal);

        TransferOutcome outcome = transferMoney.execute(internalCommand("key-3"));

        assertThat(outcome).isInstanceOf(TransferOutcome.Replayed.class);
        TransferOutcome.Replayed replayed = (TransferOutcome.Replayed) outcome;
        assertThat(replayed.httpStatus()).isEqualTo(201);
        assertThat(replayed.responseBody()).isEqualTo("{\"id\":\"cached\"}");
        verifyNoInteractions(internalTransferHandler);
        verifyNoInteractions(externalTransferHandler);
    }

    @Test
    void execute_domainExceptionFromHandler_cachesFailureAndRethrows() {
        given(idempotencyPort.tryBegin(anyString())).willReturn(IdempotencyClaim.acquired());
        InsufficientBalanceException ex = new InsufficientBalanceException(UUID.randomUUID().toString());
        given(internalTransferHandler.handle(any(TransferCommand.class))).willThrow(ex);

        assertThatThrownBy(() -> transferMoney.execute(internalCommand("key-4")))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(idempotencyPort).complete(eq("key-4"), any());
    }

    @Test
    void execute_duplicateKeyConstraintViolation_returnsExistingTransactionAsExecuted() {
        given(idempotencyPort.tryBegin(anyString())).willReturn(IdempotencyClaim.acquired());
        given(internalTransferHandler.handle(any(TransferCommand.class)))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        Transaction existing = Transaction.createInternal(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), "key-5");
        given(transactionRepositoryPort.findByIdempotencyKey("key-5")).willReturn(Optional.of(existing));

        TransferOutcome outcome = transferMoney.execute(internalCommand("key-5"));

        assertThat(outcome).isInstanceOf(TransferOutcome.Executed.class);
        assertThat(((TransferOutcome.Executed) outcome).transaction()).isSameAs(existing);
    }

    @Test
    void execute_external_delegatesToExternalHandler() {
        given(idempotencyPort.tryBegin(anyString())).willReturn(IdempotencyClaim.acquired());
        Transaction tx = Transaction.createExternal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "REF-001", new BigDecimal("10.00"), "key-ext-1");
        given(externalTransferHandler.handle(any(TransferCommand.class))).willReturn(tx);

        TransferOutcome outcome = transferMoney.execute(externalCommand("key-ext-1"));

        assertThat(outcome).isInstanceOf(TransferOutcome.Executed.class);
        verify(externalTransferHandler).handle(any(TransferCommand.class));
        verify(internalTransferHandler, never()).handle(any());
    }

    @Test
    void execute_technicalExceptionFromExternalHandler_releasesKeyAndRethrows() {
        given(idempotencyPort.tryBegin(anyString())).willReturn(IdempotencyClaim.acquired());
        given(externalTransferHandler.handle(any(TransferCommand.class)))
                .willThrow(new AuthorizationTimeoutException("timeout", null));

        assertThatThrownBy(() -> transferMoney.execute(externalCommand("key-ext-2")))
                .isInstanceOf(AuthorizationTimeoutException.class);

        verify(idempotencyPort).release("key-ext-2");
        verify(idempotencyPort, never()).complete(anyString(), any());
    }
}