package com.paymentgateway.engine.application.handler;

import com.paymentgateway.engine.application.fake.FakeAccountRepositoryPort;
import com.paymentgateway.engine.application.fake.FakeTransactionLogRepositoryPort;
import com.paymentgateway.engine.application.fake.FakeTransactionRepositoryPort;
import com.paymentgateway.engine.domain.exception.InactiveAccountException;
import com.paymentgateway.engine.domain.exception.InsufficientBalanceException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.exception.SelfTransferException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.AccountStatus;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransactionStatus;
import com.paymentgateway.engine.domain.policy.LockOrderPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTransferHandlerTest {

    private FakeAccountRepositoryPort accountRepository;
    private FakeTransactionRepositoryPort transactionRepository;
    private FakeTransactionLogRepositoryPort transactionLogRepository;
    private InternalTransferHandler handler;

    private UUID ownerId;
    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        accountRepository = new FakeAccountRepositoryPort();
        transactionRepository = new FakeTransactionRepositoryPort();
        transactionLogRepository = new FakeTransactionLogRepositoryPort();
        handler = new InternalTransferHandler(
                accountRepository, transactionRepository, transactionLogRepository, new LockOrderPolicy());

        ownerId = UUID.randomUUID();
        source = Account.createNew(ownerId, new BigDecimal("100.00"));
        target = Account.createNew(UUID.randomUUID(), new BigDecimal("50.00"));
        accountRepository.save(source);
        accountRepository.save(target);
    }

    private TransferCommand commandFor(UUID sourceId, UUID requester, UUID targetId, String amount) {
        return TransferCommand.forInternal(
                sourceId, requester, targetId, new BigDecimal(amount), "key-" + UUID.randomUUID());
    }

    @Test
    void handle_validTransfer_movesBalanceAndPersistsCompletedTransaction() {
        Transaction result = handler.handle(commandFor(source.getId(), ownerId, target.getId(), "30.00"));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        Account updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        Account updatedTarget = accountRepository.findById(target.getId()).orElseThrow();
        assertThat(updatedSource.getBalance()).isEqualByComparingTo("70.00");
        assertThat(updatedTarget.getBalance()).isEqualByComparingTo("80.00");

        assertThat(transactionRepository.findById(result.getId())).isPresent();
        assertThat(transactionLogRepository.all()).hasSize(1);
        assertThat(transactionLogRepository.all().get(0).getTransactionId()).isEqualTo(result.getId());
    }

    @Test
    void handle_selfTransfer_throwsAndPersistsNothing() {
        assertThatThrownBy(() -> handler.handle(commandFor(source.getId(), ownerId, source.getId(), "10.00")))
                .isInstanceOf(SelfTransferException.class);

        assertThat(transactionRepository.size()).isZero();
        assertThat(transactionLogRepository.all()).isEmpty();
    }

    @Test
    void handle_requesterNotSourceOwner_throwsOwnershipViolation() {
        UUID otherUser = UUID.randomUUID();

        assertThatThrownBy(() -> handler.handle(commandFor(source.getId(), otherUser, target.getId(), "10.00")))
                .isInstanceOf(OwnershipViolationException.class);

        assertThat(transactionRepository.size()).isZero();
    }

    @Test
    void handle_inactiveSourceAccount_throwsInactiveAccountException() {
        accountRepository.save(Account.reconstitute(
                source.getId(), ownerId, source.getBalance(), AccountStatus.BLOCKED));

        assertThatThrownBy(() -> handler.handle(commandFor(source.getId(), ownerId, target.getId(), "10.00")))
                .isInstanceOf(InactiveAccountException.class);

        assertThat(transactionRepository.size()).isZero();
    }

    @Test
    void handle_inactiveTargetAccount_throwsInactiveAccountException() {
        accountRepository.save(Account.reconstitute(
                target.getId(), target.getOwnerId(), target.getBalance(), AccountStatus.CLOSED));

        assertThatThrownBy(() -> handler.handle(commandFor(source.getId(), ownerId, target.getId(), "10.00")))
                .isInstanceOf(InactiveAccountException.class);

        assertThat(transactionRepository.size()).isZero();
    }

    @Test
    void handle_insufficientBalance_throwsAndLeavesBalancesUnchanged() {
        assertThatThrownBy(() -> handler.handle(commandFor(source.getId(), ownerId, target.getId(), "1000.00")))
                .isInstanceOf(InsufficientBalanceException.class);

        Account unchangedSource = accountRepository.findById(source.getId()).orElseThrow();
        Account unchangedTarget = accountRepository.findById(target.getId()).orElseThrow();
        assertThat(unchangedSource.getBalance()).isEqualByComparingTo("100.00");
        assertThat(unchangedTarget.getBalance()).isEqualByComparingTo("50.00");
        assertThat(transactionRepository.size()).isZero();
    }
}