package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.domain.model.TransferType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(

        @NotNull(message = "sourceAccountId is required")
        UUID sourceAccountId,

        @NotNull(message = "transferType is required")
        TransferType transferType,

        UUID targetAccountId,          
        UUID targetProviderId,         
        UUID targetBankId,             
        String targetExternalReference, 

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be strictly positive")
        BigDecimal amount
) {
    @AssertTrue(message = "targetAccountId is required when transferType is INTERNAL")
    public boolean isTargetAccountIdValid() {
        return transferType != TransferType.INTERNAL || targetAccountId != null;
    }

    @AssertTrue(message = "targetProviderId, targetBankId and targetExternalReference are required when transferType is EXTERNAL")
    public boolean isExternalFieldsValid() {
        return transferType != TransferType.EXTERNAL
                || (targetProviderId != null && targetBankId != null
                        && targetExternalReference != null && !targetExternalReference.isBlank());
    }
}