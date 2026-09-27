package com.paymentgateway.engine.application.handler;

import com.paymentgateway.engine.application.fake.*;
import com.paymentgateway.engine.domain.exception.*;
import com.paymentgateway.engine.domain.model.*;
import com.paymentgateway.engine.domain.port.AuthorizationPort;
import com.paymentgateway.engine.domain.port.AuthorizationResult;
import com.paymentgateway.engine.infrastructure.adapter.http.AuthorizationTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class ExternalTransferHandlerTest {

    private FakeAccountRepositoryPort accountRepository;
    private FakeTransactionRepositoryPort transactionRepository;
    private FakeTransactionLogRepositoryPort transactionLogRepository;
    private FakeProviderRepositoryPort providerRepository;
    private FakeExternalBankRepositoryPort bankRepository;
    private AuthorizationPort authorizationPort;
    private ExternalTransferHandler handler;

    private UUID ownerId;
    private Account source;
    private Provider provider;
    private ExternalBank bank;

    @BeforeEach
    void setUp() {
        accountRepository = new FakeAccountRepositoryPort();
        transactionRepository = new FakeTransactionRepositoryPort();
        transactionLogRepository = new FakeTransactionLogRepositoryPort();
        providerRepository = new FakeProviderRepositoryPort();
        bankRepository = new FakeExternalBankRepositoryPort();
        authorizationPort = mock(AuthorizationPort.class);
        handler = new ExternalTransferHandler(accountRepository, transactionRepository,
                transactionLogRepository, providerRepository, bankRepository, authorizationPort);

        ownerId = UUID.randomUUID();
        source = Account.createNew(ownerId, new BigDecimal("100.00"));
        accountRepository.save(source);

        provider = Provider.reconstitute(UUID.randomUUID(), "SWIFT-demo", "SWIFT", ProviderStatus.ACTIVE);
        providerRepository.seed(provider);
        bank = ExternalBank.reconstitute(UUID.randomUUID(), provider.getId(), "DE-001", "Demo Bank",
                "DE", "EUR", ExternalBankStatus.ACTIVE);
        bankRepository.seed(bank);
    }

    private TransferCommand.External commandFor(String amount) {
        return new TransferCommand.External(source.getId(), ownerId, provider.getId(), bank.getId(),
                "REF-001", new BigDecimal(amount), "key-" + UUID.randomUUID());
    }

    @Test
    void handle_approved_debitsSourceAndPersistsCompletedTransaction() {
        given(authorizationPort.authorize(any())).willReturn(AuthorizationResult.approved("AUTH-1"));

        Transaction result = handler.handle(commandFor("30.00"));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("70.00");
        assertThat(transactionRepository.findById(result.getId())).isPresent();
        assertThat(transactionLogRepository.all()).hasSize(1);
    }

    @Test
    void handle_declined_doesNotDebitAndPersistsFailedTransaction() {
        given(authorizationPort.authorize(any()))
                .willReturn(AuthorizationResult.declined("INSUFFICIENT_FUNDS_AT_DESTINATION"));

        Transaction result = handler.handle(commandFor("30.00"));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("DECLINED"); // BR-010 literal
        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00"); // sin debit
        assertThat(transactionLogRepository.all().get(0).getDetail())
                .contains("INSUFFICIENT_FUNDS_AT_DESTINATION"); // motivo específico preservado en el log
    }

    @Test
    void handle_requesterNotSourceOwner_throwsOwnershipViolation() {
        UUID stranger = UUID.randomUUID();
        TransferCommand.External command = new TransferCommand.External(
                source.getId(), stranger, provider.getId(), bank.getId(), "REF", new BigDecimal("10.00"), "key-x");

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(OwnershipViolationException.class);
        verifyNoInteractions(authorizationPort);
    }

    @Test
    void handle_inactiveSourceAccount_throwsInactiveAccountException() {
        accountRepository.save(Account.reconstitute(source.getId(), ownerId, source.getBalance(), AccountStatus.BLOCKED));

        assertThatThrownBy(() -> handler.handle(commandFor("10.00")))
                .isInstanceOf(InactiveAccountException.class);
        verifyNoInteractions(authorizationPort);
    }

    @Test
    void handle_inactiveProvider_throwsInvalidExternalCounterpartyException() {
        providerRepository.seed(Provider.reconstitute(provider.getId(), provider.getCode(), provider.getName(), ProviderStatus.INACTIVE));

        assertThatThrownBy(() -> handler.handle(commandFor("10.00")))
                .isInstanceOf(InvalidExternalCounterpartyException.class);
    }

    @Test
    void handle_bankBelongsToDifferentProvider_throwsInvalidExternalCounterpartyException() {
        Provider otherProvider = Provider.reconstitute(UUID.randomUUID(), "RAILS-flaky", "Rails", ProviderStatus.ACTIVE);
        providerRepository.seed(otherProvider);
        ExternalBank mismatchedBank = ExternalBank.reconstitute(bank.getId(), otherProvider.getId(),
                bank.getCode(), bank.getName(), bank.getCountry(), bank.getCurrency(), bank.getStatus());
        bankRepository.seed(mismatchedBank);

        assertThatThrownBy(() -> handler.handle(commandFor("10.00")))
                .isInstanceOf(InvalidExternalCounterpartyException.class);
    }

    @Test
    void handle_insufficientBalance_throwsBeforeCallingAuthorizationPort() {
        assertThatThrownBy(() -> handler.handle(commandFor("1000.00")))
                .isInstanceOf(InsufficientBalanceException.class);

        // verificado ANTES de la llamada externa -- no se gasta
        // presupuesto de circuit breaker en transferencias inviables.
        verifyNoInteractions(authorizationPort);
    }

    @Test
    void handle_technicalException_propagatesWithoutPersistingTransaction() {
        given(authorizationPort.authorize(any()))
                .willThrow(new AuthorizationTimeoutException("simulated timeout", null));

        assertThatThrownBy(() -> handler.handle(commandFor("10.00")))
                .isInstanceOf(AuthorizationTimeoutException.class);

        assertThat(transactionRepository.size()).isZero(); // nada persistido
        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00"); // sin debit
    }

    @Test
void handle_nonExistentProvider_throwsInvalidExternalCounterpartyException() {
TransferCommand.External command = new TransferCommand.External(source.getId(), ownerId,
        UUID.randomUUID(), bank.getId(), "REF-001", new BigDecimal("10.00"), "key-" + UUID.randomUUID());

assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(InvalidExternalCounterpartyException.class);
verifyNoInteractions(authorizationPort);   // BR-013 se valida ANTES de llamar al proveedor
}

@Test
void handle_nonExistentBank_throwsInvalidExternalCounterpartyException() {
TransferCommand.External command = new TransferCommand.External(source.getId(), ownerId,
        provider.getId(), UUID.randomUUID(), "REF-001", new BigDecimal("10.00"), "key-" + UUID.randomUUID());

assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(InvalidExternalCounterpartyException.class);
verifyNoInteractions(authorizationPort);
}

@Test
void handle_inactiveBank_throwsInvalidExternalCounterpartyException() {
bankRepository.seed(ExternalBank.reconstitute(bank.getId(), provider.getId(), bank.getCode(),
        bank.getName(), bank.getCountry(), bank.getCurrency(), ExternalBankStatus.INACTIVE));

assertThatThrownBy(() -> handler.handle(commandFor("10.00")))
        .isInstanceOf(InvalidExternalCounterpartyException.class);
verifyNoInteractions(authorizationPort);
}
}