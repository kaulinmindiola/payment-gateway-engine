package com.paymentgateway.engine.application.handler;

import com.paymentgateway.engine.domain.exception.InactiveAccountException;
import com.paymentgateway.engine.domain.exception.InsufficientBalanceException;
import com.paymentgateway.engine.domain.exception.InvalidExternalCounterpartyException;
import com.paymentgateway.engine.domain.exception.OwnershipViolationException;
import com.paymentgateway.engine.domain.model.*;
import com.paymentgateway.engine.domain.port.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExternalTransferHandler implements TransferHandler {

    private final AccountRepositoryPort accountRepositoryPort;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private final TransactionLogRepositoryPort transactionLogRepositoryPort;
    private final ProviderRepositoryPort providerRepositoryPort;
    private final ExternalBankRepositoryPort externalBankRepositoryPort;
    private final AuthorizationPort authorizationPort;

    public ExternalTransferHandler(AccountRepositoryPort accountRepositoryPort,
                                    TransactionRepositoryPort transactionRepositoryPort,
                                    TransactionLogRepositoryPort transactionLogRepositoryPort,
                                    ProviderRepositoryPort providerRepositoryPort,
                                    ExternalBankRepositoryPort externalBankRepositoryPort,
                                    AuthorizationPort authorizationPort) {
        this.accountRepositoryPort = accountRepositoryPort;
        this.transactionRepositoryPort = transactionRepositoryPort;
        this.transactionLogRepositoryPort = transactionLogRepositoryPort;
        this.providerRepositoryPort = providerRepositoryPort;
        this.externalBankRepositoryPort = externalBankRepositoryPort;
        this.authorizationPort = authorizationPort;
    }

    @Override
    @Transactional
    public Transaction handle(TransferCommand command) {
        if (!(command instanceof TransferCommand.External external)) {
            throw new IllegalStateException(
                    "ExternalTransferHandler received a non-External TransferCommand: " + command.getClass());
        }

        // BR-003: solo la cuenta ORIGEN se bloquea -- no hay una segunda cuenta
        // propia del sistema. LockOrderPolicy no se invoca (Sección 10 del contexto).
        Account source = accountRepositoryPort.findByIdForUpdate(external.sourceAccountId());

        // BR-006 antes de BR-003: mismo criterio de RISK-006 ya establecido en
        // InternalTransferHandler (Fase 5) -- no revelar estado de cuenta ajena.
        if (!source.getOwnerId().equals(external.requestingUserId())) {
            throw new OwnershipViolationException(source.getId().toString());
        }
        if (!source.isActive()) {
            throw new InactiveAccountException(source.getId().toString());
        }

        // BR-013: provider y bank deben existir, estar ACTIVE, y el bank debe
        // pertenecer realmente al provider declarado (defensa adicional no
        // exigida literalmente por el contexto, pero previene un mismatch
        // silencioso entre X-Provider-Code y el bank_id enviado).
        Provider provider = providerRepositoryPort.findById(external.targetProviderId())
                .filter(Provider::isActive)
                .orElseThrow(() -> new InvalidExternalCounterpartyException(
                        "Provider not found or inactive: " + external.targetProviderId()));

        ExternalBank bank = externalBankRepositoryPort.findById(external.targetBankId())
                .filter(ExternalBank::isActive)
                .filter(b -> b.getProviderId().equals(provider.getId()))
                .orElseThrow(() -> new InvalidExternalCounterpartyException(
                        "External bank not found, inactive, or provider mismatch: " + external.targetBankId()));

        // BR-001, verificado ANTES de invocar al proveedor -- evita gastar
        // presupuesto de circuit breaker en transferencias inviables localmente.
        if (!source.hasSufficientBalance(external.amount())) {
            throw new InsufficientBalanceException(source.getId().toString());
        }

        AuthorizationRequest authRequest = AuthorizationRequest.of(
                source.getId(), provider.getId(), provider.getCode(), bank.getId(),
                external.targetExternalReference(), external.amount(), bank.getCurrency(),
                external.idempotencyKey());

        // Excepciones técnicas (timeout/5xx/circuito abierto) se propagan SIN
        // capturar aquí -- @Transactional revierte todo, nada se persiste
        // (Sección 7 del contexto). TransferMoney las intercepta en Paso 4.
        AuthorizationResult result = authorizationPort.authorize(authRequest);

        Transaction transaction = Transaction.createExternal(source.getId(), provider.getId(), bank.getId(),
                external.targetExternalReference(), external.amount(), external.idempotencyKey());

        if (result.isApproved()) {
            source.debit(external.amount()); // ya validado -- no debería fallar aquí
            transaction.markCompleted();
            accountRepositoryPort.save(source);
            transactionRepositoryPort.save(transaction);
            transactionLogRepositoryPort.save(TransactionLog.create(transaction.getId(), LogStatus.COMPLETED,
                    "External transfer approved (provider reference: " + result.getProviderReference() + ")"));
        } else {
            // BR-010: DECLINED es resultado de negocio válido, nunca excepción.
            // failureReason = literal "DECLINED"; el motivo específico del
            // proveedor se conserva en el log de auditoría (decisión aprobada).
            transaction.markFailed("DECLINED");
            transactionRepositoryPort.save(transaction);
            transactionLogRepositoryPort.save(TransactionLog.create(transaction.getId(), LogStatus.FAILED,
                    "External transfer declined by provider: " + result.getDeclineReason()));
            // Sin debit -- el dinero nunca sale de la cuenta origen.
        }

        return transaction;
    }
}