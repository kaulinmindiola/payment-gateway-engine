package com.paymentgateway.engine.infrastructure.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAccountRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    void validRequest_withPositiveBalance_hasNoViolations() {
        CreateAccountRequest request = new CreateAccountRequest(new BigDecimal("100.00"));
        Set<ConstraintViolation<CreateAccountRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void validRequest_withZeroBalance_hasNoViolations() {
        CreateAccountRequest request = new CreateAccountRequest(BigDecimal.ZERO);
        Set<ConstraintViolation<CreateAccountRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void invalidRequest_withNullBalance_hasViolation() {
        CreateAccountRequest request = new CreateAccountRequest(null);
        Set<ConstraintViolation<CreateAccountRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("initialBalance is required");
    }

    @Test
    void invalidRequest_withNegativeBalance_hasViolation() {
        CreateAccountRequest request = new CreateAccountRequest(new BigDecimal("-0.01"));
        Set<ConstraintViolation<CreateAccountRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("initialBalance must be zero or positive");
    }
}