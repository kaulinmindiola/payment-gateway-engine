package com.paymentgateway.engine.infrastructure.web;

import com.paymentgateway.engine.domain.model.User;
import com.paymentgateway.engine.domain.model.UserStatus;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email, UserStatus status) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getStatus());
    }
}