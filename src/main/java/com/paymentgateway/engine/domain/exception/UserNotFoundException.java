package com.paymentgateway.engine.domain.exception;

public class UserNotFoundException extends DomainException {
    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
    }
}