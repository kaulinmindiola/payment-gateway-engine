package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.model.ExternalBank;
import com.paymentgateway.engine.domain.port.ExternalBankRepositoryPort;
import com.paymentgateway.engine.domain.port.ProviderRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GetExternalBanks {

    private final ExternalBankRepositoryPort externalBankRepositoryPort;
    private final ProviderRepositoryPort providerRepositoryPort;

    public GetExternalBanks(ExternalBankRepositoryPort externalBankRepositoryPort,
                             ProviderRepositoryPort providerRepositoryPort) {
        this.externalBankRepositoryPort = externalBankRepositoryPort;
        this.providerRepositoryPort = providerRepositoryPort;
    }

    public List<ExternalBankView> execute() {
        Map<UUID, String> providerCodeById = providerRepositoryPort.findAll().stream()
        .collect(java.util.stream.Collectors.toMap(p -> p.getId(), p -> p.getCode()));

        return externalBankRepositoryPort.findAll().stream()
                .map(bank -> ExternalBankView.of(bank, resolveProviderCode(bank, providerCodeById)))
                .toList();
    }

    private String resolveProviderCode(ExternalBank bank, Map<UUID, String> providerCodeById) {
        String code = providerCodeById.get(bank.getProviderId());
        if (code == null) {
            throw new IllegalStateException(
                    "Data integrity violation: ExternalBank " + bank.getId()
                            + " references non-existent Provider " + bank.getProviderId());
        }
        return code;
    }
}