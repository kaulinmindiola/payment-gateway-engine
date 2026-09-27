package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.application.exception.IdempotencyConflictException;
import com.paymentgateway.engine.application.usecase.TransferMoney;
import com.paymentgateway.engine.application.usecase.TransferMoneyCommand;
import com.paymentgateway.engine.application.usecase.TransferOutcome;
import com.paymentgateway.engine.domain.model.AccountStatus;
import com.paymentgateway.engine.domain.model.UserStatus;
import com.paymentgateway.engine.infrastructure.adapter.persistence.AccountJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.UserJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.AccountEntity;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TransferIdempotencyIT extends AbstractApplicationIntegrationTest {

    @Autowired
    private TransferMoney transferMoney;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    private UUID seedUser() {
        UUID id = UUID.randomUUID();
        userJpaRepository.save(new UserEntity(id, "u-" + id + "@example.com", "User", UserStatus.ACTIVE));
        return id;
    }

    private UUID seedAccount(UUID ownerId, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        accountJpaRepository.save(new AccountEntity(id, ownerId, balance, AccountStatus.ACTIVE, 0L));
        return id;
    }

    @Test
    void twoConcurrentRequestsSameKey_oneSucceedsOneGetsConflict() throws Exception {
        UUID sourceOwner = seedUser();
        UUID targetOwner = seedUser();
        UUID sourceId = seedAccount(sourceOwner, new BigDecimal("100.00"));
        UUID targetId = seedAccount(targetOwner, BigDecimal.ZERO);
        String sharedKey = "concurrent-idem-" + UUID.randomUUID();

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger acquiredCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        Callable<Void> task = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                TransferMoneyCommand command = TransferMoneyCommand.forInternal(
                        sourceId, sourceOwner, targetId, new BigDecimal("10.00"), sharedKey);
                transferMoney.execute(command);
                acquiredCount.incrementAndGet();
            } catch (IdempotencyConflictException e) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        List<Future<Void>> futures = List.of(executor.submit(task), executor.submit(task));
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // BR-002: exactamente una reclama la key (ACQUIRED->ejecuta), la otra
        // encuentra IN_PROGRESS->409. No ambas ejecutan, no ambas fallan.
        assertThat(acquiredCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        AccountEntity finalSource = accountJpaRepository.findById(sourceId).orElseThrow();
        assertThat(finalSource.getBalance())
                .as("El débito debe aplicarse UNA sola vez, no dos")
                .isEqualByComparingTo("90.00");
    }

    @Test
    void terminalKey_returnsExactCachedResponseWithoutReExecuting() {
        UUID sourceOwner = seedUser();
        UUID targetOwner = seedUser();
        UUID sourceId = seedAccount(sourceOwner, new BigDecimal("100.00"));
        UUID targetId = seedAccount(targetOwner, BigDecimal.ZERO);
        String key = "terminal-idem-" + UUID.randomUUID();

        TransferMoneyCommand command = TransferMoneyCommand.forInternal(
                sourceId, sourceOwner, targetId, new BigDecimal("10.00"), key);

        TransferOutcome first = transferMoney.execute(command);
        assertThat(first).isInstanceOf(TransferOutcome.Executed.class);

        // Segunda invocación, MISMA key, después de completada -- BR-002:
        // debe devolver la respuesta EXACTA cacheada, sin tocar balances de nuevo.
        TransferOutcome second = transferMoney.execute(command);
        assertThat(second).isInstanceOf(TransferOutcome.Replayed.class);

        TransferOutcome.Replayed replayed = (TransferOutcome.Replayed) second;
        assertThat(replayed.httpStatus()).isEqualTo(201);
        assertThat(replayed.responseBody()).contains(
                ((TransferOutcome.Executed) first).transaction().getId().toString());

        AccountEntity finalSource = accountJpaRepository.findById(sourceId).orElseThrow();
        assertThat(finalSource.getBalance())
                .as("El segundo intento NO debe re-ejecutar el débito")
                .isEqualByComparingTo("90.00");
    }
}