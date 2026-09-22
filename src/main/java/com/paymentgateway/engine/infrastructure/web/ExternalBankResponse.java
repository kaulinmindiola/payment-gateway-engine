package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.application.usecase.ExternalBankView;
import com.paymentgateway.engine.domain.model.ExternalBankStatus;

import java.util.UUID;

public record ExternalBankResponse(
        UUID id,
        UUID providerId,
        String providerCode,
        String code,
        String name,
        String country,
        String currency,
        ExternalBankStatus status
) {
    public static ExternalBankResponse from(ExternalBankView view) {
        return new ExternalBankResponse(view.id(), view.providerId(), view.providerCode(),
                view.code(), view.name(), view.country(), view.currency(), view.status());
    }
}