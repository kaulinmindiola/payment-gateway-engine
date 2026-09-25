package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.domain.model.*;
import com.paymentgateway.engine.domain.port.PageResult;
import com.paymentgateway.engine.domain.port.TransactionHistoryQuery;
import com.paymentgateway.engine.infrastructure.adapter.persistence.JpaTransactionRepositoryAdapter;
import com.paymentgateway.engine.infrastructure.adapter.persistence.TransactionJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager; // ajusta al import real de tu entorno

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaTransactionHistoryIT extends AbstractPersistenceIntegrationTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-03T10:00:00Z");
    private static final Instant T4 = Instant.parse("2026-01-04T10:00:00Z");

    @Autowired private TransactionJpaRepository transactionJpaRepository;
    @Autowired private TestEntityManager em;

    private JpaTransactionRepositoryAdapter adapter;
    private UUID accountA, accountB, accountC, providerId, bankId;
    private UUID tx1, tx2, tx3;   // involucran a A
    private UUID txUnrelated;     // B -> C, NO involucra a A

    @BeforeEach
    void seed() {
        adapter = new JpaTransactionRepositoryAdapter(transactionJpaRepository);

        UUID owner = UUID.randomUUID();
        em.persist(new UserEntity(owner, "h-" + owner + "@example.com", "History", UserStatus.ACTIVE));
        accountA = account(owner);
        accountB = account(owner);
        accountC = account(owner);

        providerId = UUID.randomUUID();
        em.persist(new ProviderEntity(providerId, "HIST-" + providerId, "Rail", ProviderStatus.ACTIVE));
        bankId = UUID.randomUUID();
        em.persist(new ExternalBankEntity(bankId, providerId, "HB-" + bankId, "Bank", "DE", "EUR", ExternalBankStatus.ACTIVE));

        tx1 = internal(accountA, accountB, TransactionStatus.COMPLETED, T1); // A origen
        tx2 = internal(accountB, accountA, TransactionStatus.COMPLETED, T2); // A destino
        tx3 = external(accountA, TransactionStatus.FAILED, T3);              // A origen, EXTERNAL
        txUnrelated = internal(accountB, accountC, TransactionStatus.COMPLETED, T4);

        em.flush();
        em.clear();
    }

    private UUID account(UUID owner) {
        UUID id = UUID.randomUUID();
        em.persist(new AccountEntity(id, owner, new BigDecimal("1000.00"), AccountStatus.ACTIVE, 0L));
        return id;
    }

    private UUID internal(UUID source, UUID target, TransactionStatus status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        em.persist(new TransactionEntity(id, source, new BigDecimal("10.00"), "hist-" + id, status,
                null, TransferType.INTERNAL, target, null, null, null, createdAt));
        return id;
    }

    private UUID external(UUID source, TransactionStatus status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        em.persist(new TransactionEntity(id, source, new BigDecimal("10.00"), "hist-" + id, status,
                status == TransactionStatus.FAILED ? "DECLINED" : null, TransferType.EXTERNAL,
                null, providerId, bankId, "REF-" + id, createdAt));
        return id;
    }

    private PageResult<Transaction> history(TransactionStatus status, TransferType type,
                                            Instant from, Instant to, int page, int size) {
        return adapter.findHistory(new TransactionHistoryQuery(accountA, status, type, from, to, page, size));
    }

    private static java.util.List<UUID> ids(PageResult<Transaction> result) {
        return result.content().stream().map(Transaction::getId).toList();
    }

    @Test
    void includesSourceAndTarget_excludesUnrelated_orderedByCreatedAtDesc() {
        PageResult<Transaction> result = history(null, null, null, null, 0, 20);

        assertThat(ids(result)).containsExactly(tx3, tx2, tx1);   // BR-015 + createdAt DESC
        assertThat(ids(result)).doesNotContain(txUnrelated);
        assertThat(result.totalElements()).isEqualTo(3);
    }

    @Test
    void pagination_splitsPagesWithCorrectMetadata() {
        PageResult<Transaction> first = history(null, null, null, null, 0, 2);
        PageResult<Transaction> second = history(null, null, null, null, 1, 2);

        assertThat(ids(first)).containsExactly(tx3, tx2);
        assertThat(ids(second)).containsExactly(tx1);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
    }

    @Test
    void filterByStatus() {
        assertThat(ids(history(TransactionStatus.FAILED, null, null, null, 0, 20))).containsExactly(tx3);
    }

    @Test
    void filterByTransferType() {
        assertThat(ids(history(null, TransferType.INTERNAL, null, null, 0, 20))).containsExactly(tx2, tx1);
    }

    @Test
    void dateRange_isInclusiveFromExclusiveTo() {
        // dateFrom = T2 exacto (incluido), dateTo = T3 exacto (excluido) -> solo tx2
        assertThat(ids(history(null, null, T2, T3, 0, 20))).containsExactly(tx2);
    }

    @Test
    void combinedFilters() {
        assertThat(ids(history(TransactionStatus.COMPLETED, TransferType.INTERNAL, T2, null, 0, 20)))
                .containsExactly(tx2);
    }

    @Test
    void accountWithoutTransactions_returnsEmptyPage() {
        UUID owner = UUID.randomUUID();
        em.persist(new UserEntity(owner, "e-" + owner + "@example.com", "Empty", UserStatus.ACTIVE));
        UUID empty = account(owner);
        em.flush();

        PageResult<Transaction> result =
                adapter.findHistory(new TransactionHistoryQuery(empty, null, null, null, null, 0, 20));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void identicalCreatedAt_paginationIsDeterministicWithoutDuplicatesOrGaps() {
        // Decisión 6: sin desempate por id, dos filas con el mismo createdAt
        // podrían repetirse u omitirse entre páginas.
        Instant tie = Instant.parse("2026-02-01T10:00:00Z");
        UUID a = internal(accountA, accountB, TransactionStatus.COMPLETED, tie);
        UUID b = internal(accountA, accountB, TransactionStatus.COMPLETED, tie);
        em.flush();
        em.clear();

        UUID page0 = ids(history(null, null, tie, null, 0, 1)).get(0);
        UUID page1 = ids(history(null, null, tie, null, 1, 1)).get(0);

        assertThat(page0).isNotEqualTo(page1);
        assertThat(java.util.List.of(page0, page1)).containsExactlyInAnyOrder(a, b);
    }
}