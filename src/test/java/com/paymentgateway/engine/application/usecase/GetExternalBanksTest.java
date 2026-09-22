package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.application.fake.FakeExternalBankRepositoryPort;
import com.paymentgateway.engine.application.fake.FakeProviderRepositoryPort;
import com.paymentgateway.engine.domain.model.ExternalBank;
import com.paymentgateway.engine.domain.model.ExternalBankStatus;
import com.paymentgateway.engine.domain.model.Provider;
import com.paymentgateway.engine.domain.model.ProviderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetExternalBanksTest {

    private FakeExternalBankRepositoryPort bankRepository;
    private FakeProviderRepositoryPort providerRepository;
    private GetExternalBanks getExternalBanks;

    @BeforeEach
    void setUp() {
        bankRepository = new FakeExternalBankRepositoryPort();
        providerRepository = new FakeProviderRepositoryPort();
        getExternalBanks = new GetExternalBanks(bankRepository, providerRepository);
    }

    @Test
    void execute_withNoBanks_returnsEmptyList() {
        assertThat(getExternalBanks.execute()).isEmpty();
    }

    @Test
    void execute_withMultipleBanksAndProviders_resolvesEachProviderCode() {
        Provider swift = Provider.reconstitute(UUID.randomUUID(), "SWIFT-demo", "SWIFT", ProviderStatus.ACTIVE);
        Provider rails = Provider.reconstitute(UUID.randomUUID(), "RAILS-flaky", "Rails", ProviderStatus.ACTIVE);
        providerRepository.seed(swift);
        providerRepository.seed(rails);

        bankRepository.seed(ExternalBank.reconstitute(
                UUID.randomUUID(), swift.getId(), "DE-001", "Bank DE", "DE", "EUR", ExternalBankStatus.ACTIVE));
        bankRepository.seed(ExternalBank.reconstitute(
                UUID.randomUUID(), rails.getId(), "ES-001", "Bank ES", "ES", "EUR", ExternalBankStatus.ACTIVE));

        List<ExternalBankView> views = getExternalBanks.execute();

        assertThat(views).hasSize(2);
        assertThat(views).extracting(ExternalBankView::providerCode)
                .containsExactlyInAnyOrder("SWIFT-demo", "RAILS-flaky");
    }
}