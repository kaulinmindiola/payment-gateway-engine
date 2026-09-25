package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.application.fake.FakeAccountRepositoryPort;
import com.paymentgateway.engine.application.fake.FakeTransactionRepositoryPort;
import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.port.PageResult;
import com.paymentgateway.engine.domain.port.TransactionHistoryQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetTransactionHistoryTest {

    private FakeAccountRepositoryPort accountRepository;
    private FakeTransactionRepositoryPort transactionRepository;
    private GetTransactionHistory getTransactionHistory;

    private UUID ownerA;
    private UUID ownerB;
    private Account accountA;
    private Account accountB;
    private Account accountC;

    @BeforeEach
    void setUp() {
        accountRepository = new FakeAccountRepositoryPort();
        transactionRepository = new FakeTransactionRepositoryPort();
        getTransactionHistory = new GetTransactionHistory(accountRepository, transactionRepository);

        ownerA = UUID.randomUUID();
        ownerB = UUID.randomUUID();
        accountA = Account.createNew(ownerA, new BigDecimal("100.00"));
        accountB = Account.createNew(ownerB, new BigDecimal("100.00"));
        accountC = Account.createNew(ownerB, new BigDecimal("100.00"));
        accountRepository.save(accountA);
        accountRepository.save(accountB);
        accountRepository.save(accountC);
    }

    private Transaction internal(Account source, Account target) {
        Transaction tx = Transaction.createInternal(
                source.getId(), target.getId(), new BigDecimal("10.00"), "key-" + UUID.randomUUID());
        transactionRepository.save(tx);
        return tx;
    }

    private TransactionHistoryQuery queryFor(UUID accountId) {
        return new TransactionHistoryQuery(accountId, null, null, null, null, 0, 20);
    }

    @Test
    void execute_asOwner_returnsTransactionsAsSourceAndTarget() {
        Transaction asSource = internal(accountA, accountB);
        Transaction asTarget = internal(accountB, accountA);
        Transaction unrelated = internal(accountB, accountC);

        PageResult<Transaction> result = getTransactionHistory.execute(queryFor(accountA.getId()), ownerA);

        // Orden exacto verificado en JpaTransactionHistoryIT; aquí solo la pertenencia (BR-015).
        assertThat(result.content()).extracting(Transaction::getId)
                .containsExactlyInAnyOrder(asSource.getId(), asTarget.getId())
                .doesNotContain(unrelated.getId());
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void execute_forNonExistentAccount_throwsAccountNotFoundException() {
        assertThatThrownBy(() -> getTransactionHistory.execute(queryFor(UUID.randomUUID()), ownerA))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void execute_asNonOwner_throwsOwnershipViolationException() {
        assertThatThrownBy(() -> getTransactionHistory.execute(queryFor(accountA.getId()), ownerB))
                .isInstanceOf(OwnershipViolationException.class);
    }

    @Test
    void execute_asCounterpartyOwner_stillForbidden() {
        // ownerB es dueño de accountB, que participa en una transacción con accountA.
        // BR-014 (a diferencia de BR-009): ser contraparte NO da acceso al historial ajeno.
        internal(accountA, accountB);

        assertThatThrownBy(() -> getTransactionHistory.execute(queryFor(accountA.getId()), ownerB))
                .isInstanceOf(OwnershipViolationException.class);
    }

    @Test
    void execute_accountWithoutTransactions_returnsEmptyPage() {
        PageResult<Transaction> result = getTransactionHistory.execute(queryFor(accountA.getId()), ownerA);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}