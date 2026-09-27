package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.model.ExternalBank;
import com.paymentgateway.engine.domain.model.ExternalBankStatus;

import java.util.UUID;

public record ExternalBankView(
        UUID id, UUID providerId, String providerCode, String code, String name,
        String country, String currency, ExternalBankStatus status
) {
    public static ExternalBankView of(ExternalBank bank, String providerCode) {
        return new ExternalBankView(bank.getId(), bank.getProviderId(), providerCode,
                bank.getCode(), bank.getName(), bank.getCountry(), bank.getCurrency(), bank.getStatus());
    }
}