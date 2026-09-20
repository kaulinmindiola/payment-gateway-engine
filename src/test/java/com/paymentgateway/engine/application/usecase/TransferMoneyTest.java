package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.application.exception.UnsupportedTransferTypeException;
import com.paymentgateway.engine.application.handler.InternalTransferHandler;
import com.paymentgateway.engine.application.handler.TransferCommand;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransferType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransferMoneyTest {

    private InternalTransferHandler internalTransferHandler;
    private TransferMoney transferMoney;

    @BeforeEach
    void setUp() {
        internalTransferHandler = mock(InternalTransferHandler.class);
        transferMoney = new TransferMoney(internalTransferHandler);
    }

    @Test
    void execute_internal_delegatesToInternalHandlerAndReturnsItsResult() {
        UUID sourceId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Transaction expected = Transaction.createInternal(sourceId, targetId, new BigDecimal("10.00"), "key-1");
        given(internalTransferHandler.handle(any(TransferCommand.class))).willReturn(expected);

        TransferMoneyCommand command = TransferMoneyCommand.of(
                sourceId, requesterId, TransferType.INTERNAL, targetId, new BigDecimal("10.00"), "key-1");

        Transaction result = transferMoney.execute(command);

        assertThat(result).isSameAs(expected);
        verify(internalTransferHandler).handle(any(TransferCommand.class));
    }

    @Test
    void execute_internalWithoutTargetAccountId_throwsIllegalArgumentException() {
        TransferMoneyCommand command = TransferMoneyCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), TransferType.INTERNAL, null,
                new BigDecimal("10.00"), "key-2");

        assertThatThrownBy(() -> transferMoney.execute(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_external_throwsUnsupportedTransferTypeException() {
        TransferMoneyCommand command = TransferMoneyCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), TransferType.EXTERNAL, null,
                new BigDecimal("10.00"), "key-3");

        assertThatThrownBy(() -> transferMoney.execute(command))
                .isInstanceOf(UnsupportedTransferTypeException.class);
    }
}