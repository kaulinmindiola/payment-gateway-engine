package com.paymentgateway.engine.infrastructure.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateAccountRequest(

        @Schema(example = "500.00")
        @NotNull(message = "initialBalance is required")
        @PositiveOrZero(message = "initialBalance must be zero or positive")
        BigDecimal initialBalance
) {
}