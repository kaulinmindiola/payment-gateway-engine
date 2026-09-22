package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.model.ExternalBank;
import com.paymentgateway.engine.domain.model.ExternalBankStatus;

import java.util.UUID;

/**
 * ADR-0010: denormaliza providerCode inline -- domain/model/ExternalBank
 * (Fase 2) referencia Provider solo por id, sin objeto, por diseño. Esta
 * vista combina ambos SOLO para la respuesta de consulta, sin alterar el
 * modelo de dominio.
 */
public record ExternalBankView(
        UUID id, UUID providerId, String providerCode, String code, String name,
        String country, String currency, ExternalBankStatus status
) {
    public static ExternalBankView of(ExternalBank bank, String providerCode) {
        return new ExternalBankView(bank.getId(), bank.getProviderId(), providerCode,
                bank.getCode(), bank.getName(), bank.getCountry(), bank.getCurrency(), bank.getStatus());
    }
}