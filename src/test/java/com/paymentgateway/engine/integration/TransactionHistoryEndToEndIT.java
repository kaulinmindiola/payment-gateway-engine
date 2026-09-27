package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.application.usecase.GetTransactionHistory;
import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferMoneyCommand;
import com.paymentgateway.engine.application.usecase.TransferOutcome;
import com.paymentgateway.engine.domain.model.*;
import com.paymentgateway.engine.domain.port.PageResult;
import com.paymentgateway.engine.domain.port.TransactionHistoryQuery;
import com.paymentgateway.engine.infrastructure.adapter.persistence.AccountJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.UserJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.AccountEntity;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
class TransactionHistoryEndToEndIT extends AbstractExternalProviderIntegrationTest {

    @Autowired private TransferMoney transferMoney;
    @Autowired private GetTransactionHistory getTransactionHistory;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private AccountJpaRepository accountJpaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID owner, accountA, accountB, providerId, bankId;
    private Transaction internalAtoB, internalBtoA, externalApproved, externalDeclined;

    @BeforeEach
    void createRealTransactions() {
        owner = UUID.randomUUID();
        userJpaRepository.save(new UserEntity(owner, "e2e-" + owner + "@example.com", "E2E", UserStatus.ACTIVE));
        accountA = account(new BigDecimal("500.00"));
        accountB = account(new BigDecimal("500.00"));

        jdbcTemplate.update(
                "INSERT INTO providers (id, code, name, status) VALUES (?, 'SWIFT-demo', 'SWIFT Demo Rail', 'ACTIVE') "
                        + "ON CONFLICT (code) DO NOTHING", UUID.randomUUID());
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE code = 'SWIFT-demo'", UUID.class);
        bankId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO external_banks (id, provider_id, code, name, country, currency, status) "
                        + "VALUES (?, ?, ?, 'E2E Bank', 'DE', 'EUR', 'ACTIVE')",
                bankId, providerId, "E2E-" + bankId);

        // Orden de creación = orden temporal real (createdAt generado por el dominio).
        internalAtoB = execute(TransferMoneyCommand.forInternal(accountA, owner, accountB, new BigDecimal("10.00"), key()));
        internalBtoA = execute(TransferMoneyCommand.forInternal(accountB, owner, accountA, new BigDecimal("20.00"), key()));
        externalApproved = execute(TransferMoneyCommand.forExternal(accountA, owner, providerId, bankId,
                "ES9121000418450200051332", new BigDecimal("30.00"), key()));
        externalDeclined = execute(TransferMoneyCommand.forExternal(accountA, owner, providerId, bankId,
                "REF-DECLINE", new BigDecimal("40.00"), key()));
    }

    private UUID account(BigDecimal balance) {
        UUID id = UUID.randomUUID();
        accountJpaRepository.save(new AccountEntity(id, owner, balance, AccountStatus.ACTIVE, 0L));
        return id;
    }

    private Transaction execute(TransferMoneyCommand command) {
        return ((TransferOutcome.Executed) transferMoney.execute(command)).transaction();
    }

    private String key() {
        return "e2e-history-" + UUID.randomUUID();
    }

    private PageResult<Transaction> history(UUID accountId, TransactionStatus status, TransferType type,
                                            Instant from, int page, int size) {
        return getTransactionHistory.execute(
                new TransactionHistoryQuery(accountId, status, type, from, null, page, size), owner);
    }

    private static List<UUID> ids(PageResult<Transaction> result) {
        return result.content().stream().map(Transaction::getId).toList();
    }

    @Test
    void fullHistory_includesInternalAndExternal_newestFirst() {
        PageResult<Transaction> result = history(accountA, null, null, null, 0, 20);

        assertThat(ids(result)).containsExactly(
                externalDeclined.getId(), externalApproved.getId(), internalBtoA.getId(), internalAtoB.getId());
        assertThat(result.totalElements()).isEqualTo(4);
    }

    @Test
    void counterpartAccount_seesOnlyItsInternalTransactions() {
        // B participa en las dos INTERNAL (origen y destino) y en ninguna EXTERNAL.
        assertThat(ids(history(accountB, null, null, null, 0, 20)))
                .containsExactly(internalBtoA.getId(), internalAtoB.getId());
    }

    @Test
    void declinedExternal_isVisibleAndFilterable() {
        PageResult<Transaction> result = history(accountA, TransactionStatus.FAILED, TransferType.EXTERNAL, null, 0, 20);

        assertThat(ids(result)).containsExactly(externalDeclined.getId());
        assertThat(result.content().get(0).getFailureReason()).isEqualTo("DECLINED");
    }

    @Test
    void pagination_overRealData() {
        PageResult<Transaction> first = history(accountA, null, null, null, 0, 3);
        PageResult<Transaction> second = history(accountA, null, null, null, 1, 3);

        assertThat(first.content()).hasSize(3);
        assertThat(ids(second)).containsExactly(internalAtoB.getId());
        assertThat(first.totalPages()).isEqualTo(2);
    }

    @Test
    void dateFrom_withCreatedAtReadBackFromDatabase_isInclusive() {
        Instant persistedCreatedAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM transactions WHERE id = ?", java.sql.Timestamp.class,
                externalApproved.getId()).toInstant();

        assertThat(ids(history(accountA, null, null, persistedCreatedAt, 0, 20)))
                .containsExactly(externalDeclined.getId(), externalApproved.getId());
    }
}