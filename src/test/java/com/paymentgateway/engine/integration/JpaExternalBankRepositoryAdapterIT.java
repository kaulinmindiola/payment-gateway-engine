package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.domain.model.ExternalBank;
import com.paymentgateway.engine.domain.model.ExternalBankStatus;
import com.paymentgateway.engine.domain.model.ProviderStatus;
import com.paymentgateway.engine.infrastructure.adapter.persistence.ExternalBankJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.JpaExternalBankRepositoryAdapter;
import com.paymentgateway.engine.infrastructure.adapter.persistence.ProviderJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.ExternalBankEntity;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.ProviderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaExternalBankRepositoryAdapterIT extends AbstractPersistenceIntegrationTest {

    @Autowired
    private ProviderJpaRepository providerJpaRepository;

    @Autowired
    private ExternalBankJpaRepository externalBankJpaRepository;

    private JpaExternalBankRepositoryAdapter adapter;

    private JpaExternalBankRepositoryAdapter adapter() {
        if (adapter == null) adapter = new JpaExternalBankRepositoryAdapter(externalBankJpaRepository);
        return adapter;
    }

    private UUID seedProvider() {
        UUID id = UUID.randomUUID();
        providerJpaRepository.save(new ProviderEntity(id, "P-" + id, "Rail", ProviderStatus.ACTIVE));
        return id;
    }

    @Test
    void findById_forExistingBank_returnsBank() {
        UUID providerId = seedProvider();
        UUID bankId = UUID.randomUUID();
        externalBankJpaRepository.save(new ExternalBankEntity(
                bankId, providerId, "DE-" + bankId, "Demo Bank", "DE", "EUR", ExternalBankStatus.ACTIVE));

        assertThat(adapter().findById(bankId))
                .isPresent()
                .get()
                .extracting(ExternalBank::getCountry)
                .isEqualTo("DE");
    }

    @Test
    void findById_forUnknownId_returnsEmpty() {
        assertThat(adapter().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll_returnsAllSeededBanks() {
        UUID providerId = seedProvider();
        externalBankJpaRepository.save(new ExternalBankEntity(
                UUID.randomUUID(), providerId, "B1-" + UUID.randomUUID(), "Bank 1", "DE", "EUR", ExternalBankStatus.ACTIVE));

        assertThat(adapter().findAll()).isNotEmpty();
    }
    @Test
    void findAll_withEmptyTable_returnsEmptyList() {
        assertThat(adapter().findAll()).isEmpty();
    }
}