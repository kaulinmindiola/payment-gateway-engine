package com.paymentgateway.engine.integration;

import com.paymentgateway.engine.domain.model.Provider;
import com.paymentgateway.engine.domain.model.ProviderStatus;
import com.paymentgateway.engine.infrastructure.adapter.persistence.JpaProviderRepositoryAdapter;
import com.paymentgateway.engine.infrastructure.adapter.persistence.ProviderJpaRepository;
import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.ProviderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaProviderRepositoryAdapterIT extends AbstractPersistenceIntegrationTest {

    @Autowired
    private ProviderJpaRepository providerJpaRepository;

    private JpaProviderRepositoryAdapter adapter;

    private JpaProviderRepositoryAdapter adapter() {
        if (adapter == null) adapter = new JpaProviderRepositoryAdapter(providerJpaRepository);
        return adapter;
    }

    @Test
    void findById_forExistingProvider_returnsProvider() {
        UUID id = UUID.randomUUID();
        providerJpaRepository.save(new ProviderEntity(id, "TEST-" + id, "Test Rail", ProviderStatus.ACTIVE));

        assertThat(adapter().findById(id))
                .isPresent()
                .get()
                .extracting(Provider::getCode)
                .isEqualTo("TEST-" + id);
    }

    @Test
    void findById_forUnknownId_returnsEmpty() {
        assertThat(adapter().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll_returnsAllSeededProviders() {
        providerJpaRepository.save(new ProviderEntity(UUID.randomUUID(), "P1-" + UUID.randomUUID(), "Rail 1", ProviderStatus.ACTIVE));
        providerJpaRepository.save(new ProviderEntity(UUID.randomUUID(), "P2-" + UUID.randomUUID(), "Rail 2", ProviderStatus.INACTIVE));

        List<Provider> all = adapter().findAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }
}