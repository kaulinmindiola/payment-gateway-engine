package com.paymentgateway.engine.application.usecase;

import com.paymentgateway.engine.domain.exception.UserNotFoundException;
import com.paymentgateway.engine.domain.model.User;
import com.paymentgateway.engine.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Read-only identity of the caller (ADR-0008 update): no user management. */
@Service
public class GetCurrentUser {

    private final UserRepositoryPort userRepositoryPort;

    public GetCurrentUser(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    public User execute(UUID requestingUserId) {
        return userRepositoryPort.findById(requestingUserId)
                .orElseThrow(() -> new UserNotFoundException(requestingUserId.toString()));
    }
}