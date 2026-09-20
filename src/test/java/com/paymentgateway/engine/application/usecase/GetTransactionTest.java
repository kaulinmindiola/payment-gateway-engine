package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.application.fake.FakeAccountRepositoryPort;
import com.paymentgateway.engine.application.fake.FakeTransactionRepositoryPort;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.exception.TransactionNotFoundException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetTransactionTest {

    private FakeTransactionRepositoryPort transactionRepository;
    private FakeAccountRepositoryPort accountRepository;
    private GetTransaction getTransaction;

    private UUID sourceOwnerId;
    private UUID targetOwnerId;
    private Account sourceAccount;
    private Account targetAccount;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transactionRepository = new FakeTransactionRepositoryPort();
        accountRepository = new FakeAccountRepositoryPort();
        getTransaction = new GetTransaction(transactionRepository, accountRepository);

        sourceOwnerId = UUID.randomUUID();
        targetOwnerId = UUID.randomUUID();
        sourceAccount = Account.createNew(sourceOwnerId, new BigDecimal("100.00"));
        targetAccount = Account.createNew(targetOwnerId, new BigDecimal("50.00"));
        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);

        transaction = Transaction.createInternal(
                sourceAccount.getId(), targetAccount.getId(), new BigDecimal("20.00"), "key-gt-1");
        transactionRepository.save(transaction);
    }

    @Test
    void execute_asSourceOwner_returnsTransaction() {
        Transaction result = getTransaction.execute(GetTransactionQuery.of(transaction.getId(), sourceOwnerId));
        assertThat(result.getId()).isEqualTo(transaction.getId());
    }

    @Test
    void execute_asTargetOwner_returnsTransaction() {
        Transaction result = getTransaction.execute(GetTransactionQuery.of(transaction.getId(), targetOwnerId));
        assertThat(result.getId()).isEqualTo(transaction.getId());
    }

    @Test
    void execute_asNeitherOwner_throwsOwnershipViolationException() {
        UUID strangerId = UUID.randomUUID();
        assertThatThrownBy(() -> getTransaction.execute(GetTransactionQuery.of(transaction.getId(), strangerId)))
                .isInstanceOf(OwnershipViolationException.class);
    }

    @Test
    void execute_forNonExistentTransaction_throwsTransactionNotFoundException() {
        assertThatThrownBy(() ->
                getTransaction.execute(GetTransactionQuery.of(UUID.randomUUID(), sourceOwnerId)))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}