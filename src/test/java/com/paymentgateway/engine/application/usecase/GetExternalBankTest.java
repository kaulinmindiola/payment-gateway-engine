package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.application.fake.FakeExternalBankRepositoryPort;
import com.paymentgateway.engine.application.fake.FakeProviderRepositoryPort;
import com.paymentgateway.engine.domain.exception.ExternalBankNotFoundException;
import com.paymentgateway.engine.domain.model.ExternalBank;
import com.paymentgateway.engine.domain.model.ExternalBankStatus;
import com.paymentgateway.engine.domain.model.Provider;
import com.paymentgateway.engine.domain.model.ProviderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetExternalBankTest {

    private FakeExternalBankRepositoryPort bankRepository;
    private FakeProviderRepositoryPort providerRepository;
    private GetExternalBank getExternalBank;

    @BeforeEach
    void setUp() {
        bankRepository = new FakeExternalBankRepositoryPort();
        providerRepository = new FakeProviderRepositoryPort();
        getExternalBank = new GetExternalBank(bankRepository, providerRepository);
    }

    @Test
    void execute_forExistingBank_returnsViewWithProviderCode() {
        Provider provider = Provider.reconstitute(UUID.randomUUID(), "SWIFT-demo", "SWIFT Demo", ProviderStatus.ACTIVE);
        providerRepository.seed(provider);
        ExternalBank bank = ExternalBank.reconstitute(
                UUID.randomUUID(), provider.getId(), "DE-001", "Demo Bank", "DE", "EUR", ExternalBankStatus.ACTIVE);
        bankRepository.seed(bank);

        ExternalBankView view = getExternalBank.execute(bank.getId());

        assertThat(view.providerCode()).isEqualTo("SWIFT-demo");
        assertThat(view.country()).isEqualTo("DE");
    }

    @Test
    void execute_forUnknownBank_throwsExternalBankNotFoundException() {
        assertThatThrownBy(() -> getExternalBank.execute(UUID.randomUUID()))
                .isInstanceOf(ExternalBankNotFoundException.class);
    }

    @Test
    void execute_withDanglingProviderReference_throwsIllegalStateException() {
        // Simula corrupción de datos -- FK de Postgres normalmente lo previene.
        ExternalBank bank = ExternalBank.reconstitute(
                UUID.randomUUID(), UUID.randomUUID(), "XX-001", "Orphan Bank", "XX", "EUR", ExternalBankStatus.ACTIVE);
        bankRepository.seed(bank);

        assertThatThrownBy(() -> getExternalBank.execute(bank.getId()))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test
    void execute_forInactiveBank_stillReturnsIt() {
        // El catálogo de consulta NO filtra por status -- BR-013 filtra en
        // Fase 8, al validar una transferencia EXTERNAL, no aquí.
        Provider provider = Provider.reconstitute(UUID.randomUUID(), "SWIFT-demo", "SWIFT", ProviderStatus.ACTIVE);
        providerRepository.seed(provider);
        ExternalBank inactiveBank = ExternalBank.reconstitute(
                UUID.randomUUID(), provider.getId(), "DE-002", "Inactive Bank", "DE", "EUR", ExternalBankStatus.INACTIVE);
        bankRepository.seed(inactiveBank);

        ExternalBankView view = getExternalBank.execute(inactiveBank.getId());

        assertThat(view.status()).isEqualTo(ExternalBankStatus.INACTIVE);
    }
}