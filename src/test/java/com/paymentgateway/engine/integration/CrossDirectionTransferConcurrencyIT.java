package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferMoneyCommand;
import com.paymentgateway.engine.domain.model.AccountStatus;
import com.paymentgateway.engine.domain.model.TransferType;
import com.paymentgateway.engine.domain.model.UserStatus;
import com.paymentgateway.engine.infrastructure.adapter.persistence.AccountJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.UserJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.AccountEntity;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RISK-001: previene deadlocks por orden inconsistente de locks.
 * Escenario: N pares de transferencias A->B y B->A simultáneas sobre las
 * MISMAS dos cuentas -- sin LockOrderPolicy, este es el caso clásico de
 * deadlock (tx1 bloquea A espera B; tx2 bloquea B espera A).
 */
class CrossDirectionTransferConcurrencyIT extends AbstractApplicationIntegrationTest {

    private static final int PAIRS = 20; // 20 A->B + 20 B->A = 40 hilos totales
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("5.00");

    @Autowired
    private TransferMoney transferMoney;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Test
    void crossDirectionTransfers_sameAccountPair_completeWithoutDeadlockAndNetToZero() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        seedUser(ownerA);
        seedUser(ownerB);

        BigDecimal initialBalance = new BigDecimal("500.00"); // margen amplio, evita mezclar con BR-001
        UUID accountA = seedAccount(ownerA, initialBalance);
        UUID accountB = seedAccount(ownerB, initialBalance);

        int totalThreads = PAIRS * 2;
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < PAIRS; i++) {
            futures.add(executor.submit(transferTask(
                    accountA, ownerA, accountB, readyLatch, startLatch, "cross-a-to-b-" + i)));
            futures.add(executor.submit(transferTask(
                    accountB, ownerB, accountA, readyLatch, startLatch, "cross-b-to-a-" + i)));
        }

        boolean allThreadsReady = readyLatch.await(10, TimeUnit.SECONDS);
        assertThat(allThreadsReady).as("Todos los hilos deben estar listos antes de liberar la señal").isTrue();

        startLatch.countDown();

        List<Exception> failures = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                // Timeout corto y deliberado: Postgres detecta deadlock real
                // en ~1s (deadlock_timeout por defecto) y aborta una de las
                // dos transacciones -- si esto cuelga más allá de este límite,
                // no es un deadlock detectado, es un bloqueo indefinido real.
                future.get(15, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                failures.add(e);
            }
        }
        executor.shutdown();

        assertThat(failures)
                .as("Ninguna transferencia debe fallar por deadlock ni timeout: %s", failures)
                .isEmpty();

        AccountEntity finalA = accountJpaRepository.findById(accountA).orElseThrow();
        AccountEntity finalB = accountJpaRepository.findById(accountB).orElseThrow();

        // Mismo monto, mismo número de transferencias en cada dirección
        // -> balance neto debe volver EXACTAMENTE al inicial en ambas cuentas.
        assertThat(finalA.getBalance())
                .as("Cuenta A debe volver a su balance inicial tras N idas y N vueltas")
                .isEqualByComparingTo(initialBalance);
        assertThat(finalB.getBalance())
                .as("Cuenta B debe volver a su balance inicial tras N idas y N vueltas")
                .isEqualByComparingTo(initialBalance);
    }

    private Callable<Void> transferTask(UUID sourceId, UUID sourceOwnerId, UUID targetId,
                                         CountDownLatch readyLatch, CountDownLatch startLatch,
                                         String idempotencyKeySuffix) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            TransferMoneyCommand command = TransferMoneyCommand.of(
                    sourceId, sourceOwnerId, TransferType.INTERNAL, targetId,
                    TRANSFER_AMOUNT, "concurrency-test-" + idempotencyKeySuffix + "-" + UUID.randomUUID());
            transferMoney.execute(command);
            return null;
        };
    }

    private void seedUser(UUID id) {
        userJpaRepository.save(new UserEntity(id, "u-" + id + "@example.com", "User " + id, UserStatus.ACTIVE));
    }

    private UUID seedAccount(UUID ownerId, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        accountJpaRepository.save(new AccountEntity(id, ownerId, balance, AccountStatus.ACTIVE, 0L));
        return id;
    }
}