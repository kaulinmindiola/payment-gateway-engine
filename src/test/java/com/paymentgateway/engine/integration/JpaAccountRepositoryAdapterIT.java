package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.domain.exception.AccountNotFoundException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.AccountStatus;
import com.paymentgateway.engine.domain.model.UserStatus;
import com.paymentgateway.engine.infrastructure.adapter.persistence.AccountJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.JpaAccountRepositoryAdapter;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.AccountEntity;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaAccountRepositoryAdapterIT extends AbstractPersistenceIntegrationTest {

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private JpaAccountRepositoryAdapter adapter;

    private JpaAccountRepositoryAdapter adapter() {
        if (adapter == null) {
            adapter = new JpaAccountRepositoryAdapter(accountJpaRepository);
        }
        return adapter;
    }

    private UUID seedUserAndAccount(BigDecimal balance) {
        UUID userId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new UserEntity(userId, "owner-" + userId + "@example.com", "Owner", UserStatus.ACTIVE));

        UUID accountId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new AccountEntity(accountId, userId, balance, AccountStatus.ACTIVE, 0L));

        entityManager.flush();
        entityManager.clear(); // fuerza un SELECT real en la siguiente lectura, no cache de sesión
        return accountId;
    }

    @Test
    void findByIdForUpdate_forExistingAccount_returnsAccountAndEmitsForUpdate() {
        UUID accountId = seedUserAndAccount(new BigDecimal("100.00"));

        try (HibernateSqlCapture capture = HibernateSqlCapture.start()) {
            Account account = adapter().findByIdForUpdate(accountId);

            assertThat(account.getId()).isEqualTo(accountId);
            assertThat(account.getBalance()).isEqualByComparingTo("100.00");

            List<String> sqlStatements = capture.capturedStatements();
            boolean emittedForUpdate = sqlStatements.stream()
                    .anyMatch(sql -> sql.toLowerCase().contains("for update"));

            assertThat(emittedForUpdate)
                    .as("Hibernate debe emitir 'for update' al usar PESSIMISTIC_WRITE. SQL capturado: %s", sqlStatements)
                    .isTrue();
        }
    }

    @Test
    void findByIdForUpdate_forUnknownId_throwsAccountNotFoundException() {
        assertThatThrownBy(() -> adapter().findByIdForUpdate(UUID.randomUUID()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void findById_forExistingAccount_returnsAccountWithoutThrowing() {
        UUID accountId = seedUserAndAccount(new BigDecimal("50.00"));

        assertThat(adapter().findById(accountId))
                .isPresent()
                .get()
                .extracting(Account::getBalance)
                .satisfies(balance -> assertThat(balance).isEqualByComparingTo("50.00"));
    }

    @Test
    void findById_forUnknownId_returnsEmpty() {
        assertThat(adapter().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_newAccount_persistsRow() {
        UUID userId = UUID.randomUUID();
        entityManager.persistAndFlush(
                new UserEntity(userId, "new-" + userId + "@example.com", "New Owner", UserStatus.ACTIVE));
        entityManager.clear();

        Account newAccount = Account.createNew(userId, new BigDecimal("200.00"));

        adapter().save(newAccount);
        entityManager.flush();
        entityManager.clear();

        AccountEntity persisted = entityManager.find(AccountEntity.class, newAccount.getId());
        assertThat(persisted).isNotNull();
        assertThat(persisted.getBalance()).isEqualByComparingTo("200.00");
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    void save_existingAccount_updatesBalanceWithoutOverwritingCreatedAt() {
        UUID accountId = seedUserAndAccount(new BigDecimal("100.00"));

        AccountEntity seeded = entityManager.find(AccountEntity.class, accountId);
        Instant originalCreatedAt = seeded.getCreatedAt();
        entityManager.clear();

        // Carga dentro de la misma transacción del test — findById() en save()
        // recuperará esta MISMA instancia gestionada (first-level cache).
        Account account = adapter().findByIdForUpdate(accountId);
        account.credit(new BigDecimal("50.00")); // BR: Account.credit(), Fase 2

        adapter().save(account);
        entityManager.flush();
        entityManager.clear();

        AccountEntity updated = entityManager.find(AccountEntity.class, accountId);
        assertThat(updated.getBalance()).isEqualByComparingTo("150.00");
        assertThat(updated.getCreatedAt())
                .as("save() no debe sobrescribir created_at en una actualización")
                .isEqualTo(originalCreatedAt);
    }
}