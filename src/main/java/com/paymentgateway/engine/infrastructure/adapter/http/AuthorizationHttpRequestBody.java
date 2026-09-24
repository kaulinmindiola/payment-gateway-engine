package com.paymentgateway.engine.infrastructure.adapter.http;

import com.paymentgateway.engine.domain.port.AuthorizationRequest;

import java.util.UUID;

/** targetProviderId NO viaja en el body -- ya expresado en X-Provider-Code. */
record AuthorizationHttpRequestBody(
        UUID sourceAccountId, UUID targetBankId, String targetExternalReference,
        String amount, String currency
) {
    static AuthorizationHttpRequestBody from(AuthorizationRequest request) {
        return new AuthorizationHttpRequestBody(
                request.getSourceAccountId(), request.getTargetBankId(),
                request.getTargetExternalReference(),
                request.getAmount().toPlainString(), // string, no number -- CON-005
                request.getCurrency());
    }
}