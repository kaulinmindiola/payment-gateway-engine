package com.paymentgateway.engine.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateAccountRequest(

        @NotNull(message = "initialBalance is required")
        @PositiveOrZero(message = "initialBalance must be zero or positive")
        BigDecimal initialBalance
) {
}