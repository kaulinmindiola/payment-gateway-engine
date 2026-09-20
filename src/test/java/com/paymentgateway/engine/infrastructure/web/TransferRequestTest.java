package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.domain.model.TransferType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferRequestTest {

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
    void validInternalRequest_hasNoViolations() {
        TransferRequest request = new TransferRequest(
                UUID.randomUUID(), TransferType.INTERNAL, UUID.randomUUID(), new BigDecimal("10.00"));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void internalRequestWithoutTargetAccountId_hasViolation() {
        TransferRequest request = new TransferRequest(
                UUID.randomUUID(), TransferType.INTERNAL, null, new BigDecimal("10.00"));
        Set<ConstraintViolation<TransferRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("targetAccountId is required when transferType is INTERNAL");
    }

    @Test
    void externalRequestWithoutTargetAccountId_hasNoViolation() {
        // EXTERNAL no valida targetAccountId aquí -- su propio contrato llega en Fase 8.
        TransferRequest request = new TransferRequest(
                UUID.randomUUID(), TransferType.EXTERNAL, null, new BigDecimal("10.00"));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void requestWithZeroAmount_hasViolation() {
        TransferRequest request = new TransferRequest(
                UUID.randomUUID(), TransferType.INTERNAL, UUID.randomUUID(), BigDecimal.ZERO);
        assertThat(validator.validate(request)).hasSize(1);
    }

    @Test
    void requestWithNullSourceAccountId_hasViolation() {
        TransferRequest request = new TransferRequest(
                null, TransferType.INTERNAL, UUID.randomUUID(), new BigDecimal("10.00"));
        assertThat(validator.validate(request)).hasSize(1);
    }
}