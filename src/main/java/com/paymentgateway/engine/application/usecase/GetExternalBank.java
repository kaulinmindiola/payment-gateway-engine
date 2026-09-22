package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.exception.ExternalBankNotFoundException;
import com.paymentgateway.engine.domain.model.ExternalBank;
import com.paymentgateway.engine.domain.model.Provider;
import com.paymentgateway.engine.domain.port.ExternalBankRepositoryPort;
import com.paymentgateway.engine.domain.port.ProviderRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetExternalBank {

    private final ExternalBankRepositoryPort externalBankRepositoryPort;
    private final ProviderRepositoryPort providerRepositoryPort;

    public GetExternalBank(ExternalBankRepositoryPort externalBankRepositoryPort,
                            ProviderRepositoryPort providerRepositoryPort) {
        this.externalBankRepositoryPort = externalBankRepositoryPort;
        this.providerRepositoryPort = providerRepositoryPort;
    }

    public ExternalBankView execute(UUID externalBankId) {
        ExternalBank bank = externalBankRepositoryPort.findById(externalBankId)
                .orElseThrow(() -> new ExternalBankNotFoundException(externalBankId.toString()));

        // La FK de Postgres (Fase 3) garantiza que providerId siempre
        // referencia una fila existente -- si esto falla, es un bug de
        // integridad de datos, no un error de usuario (Decisión 3).
        Provider provider = providerRepositoryPort.findById(bank.getProviderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Data integrity violation: ExternalBank " + bank.getId()
                                + " references non-existent Provider " + bank.getProviderId()));

        return ExternalBankView.of(bank, provider.getCode());
    }
}