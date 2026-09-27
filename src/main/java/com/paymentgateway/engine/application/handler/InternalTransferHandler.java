package com.paymentgateway.engine.application.handler;

import com.paymentgateway.engine.domain.exception.InactiveAccountException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.exception.SelfTransferException;
import com.paymentgateway.engine.domain.model.Account;
import com.paymentgateway.engine.domain.model.LogStatus;
import com.paymentgateway.engine.domain.model.Transaction;
import com.paymentgateway.engine.domain.model.TransactionLog;
import com.paymentgateway.engine.domain.policy.LockOrder;
import com.paymentgateway.engine.domain.policy.LockOrderPolicy;
import com.paymentgateway.engine.domain.port.AccountRepositoryPort;
import com.paymentgateway.engine.domain.port.TransactionLogRepositoryPort;
import com.paymentgateway.engine.domain.port.TransactionRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InternalTransferHandler implements TransferHandler {

    private final AccountRepositoryPort accountRepositoryPort;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private final TransactionLogRepositoryPort transactionLogRepositoryPort;
    private final LockOrderPolicy lockOrderPolicy;

    public InternalTransferHandler(AccountRepositoryPort accountRepositoryPort,
                                   TransactionRepositoryPort transactionRepositoryPort,
                                   TransactionLogRepositoryPort transactionLogRepositoryPort,
                                   LockOrderPolicy lockOrderPolicy) {
        this.accountRepositoryPort = accountRepositoryPort;
        this.transactionRepositoryPort = transactionRepositoryPort;
        this.transactionLogRepositoryPort = transactionLogRepositoryPort;
        this.lockOrderPolicy = lockOrderPolicy;
    }

    @Override
    @Transactional
    public Transaction handle(TransferCommand command) {
        // 1. NUEVA VALIDACIÓN: Verifica la variante y extrae a la variable 'internal'
        if (!(command instanceof TransferCommand.Internal internal)) {
            throw new IllegalStateException(
                    "InternalTransferHandler received a non-Internal TransferCommand: " + command.getClass());
        }

        if (internal.sourceAccountId().equals(internal.targetAccountId())) {
            throw new SelfTransferException(internal.sourceAccountId().toString());
        }

        LockOrder lockOrder = lockOrderPolicy.resolveLockOrder(
                internal.sourceAccountId(), internal.targetAccountId());
        Account first = accountRepositoryPort.findByIdForUpdate(lockOrder.first());
        Account second = accountRepositoryPort.findByIdForUpdate(lockOrder.second());

        // El orden de adquisición de locks es por ID, no por rol de negocio
        // -- se reordena aquí según semántica (source/target reales).
        Account source = first.getId().equals(internal.sourceAccountId()) ? first : second;
        Account target = first.getId().equals(internal.targetAccountId()) ? first : second;

        if (!source.getOwnerId().equals(internal.requestingUserId())) {
            throw new OwnershipViolationException(source.getId().toString());
        }

        if (!source.isActive()) {
            throw new InactiveAccountException(source.getId().toString());
        }
        if (!target.isActive()) {
            throw new InactiveAccountException(target.getId().toString());
        }

        Transaction transaction = Transaction.createInternal(
                source.getId(), target.getId(), internal.amount(), internal.idempotencyKey());

        source.debit(internal.amount()); 
        target.credit(internal.amount());
        transaction.markCompleted();

        accountRepositoryPort.save(source);
        accountRepositoryPort.save(target);
        transactionRepositoryPort.save(transaction);
        transactionLogRepositoryPort.save(TransactionLog.create(
                transaction.getId(), LogStatus.COMPLETED, "Internal transfer completed successfully"));

        return transaction;
    }
}