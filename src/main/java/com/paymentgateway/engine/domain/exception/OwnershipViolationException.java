package com.paymentgateway.engine.domain.exception;

public class OwnershipViolationException extends DomainException {
    public OwnershipViolationException(String resourceId) {
        super("Requester does not own resource: " + resourceId);
    }
}