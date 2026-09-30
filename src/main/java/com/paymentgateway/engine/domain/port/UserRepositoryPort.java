package com.paymentgateway.engine.domain.port;

import java.util.UUID;
import java.util.Optional;
import com.paymentgateway.engine.domain.model.User;

public interface UserRepositoryPort {


    boolean existsById(UUID id);
    Optional<User> findById(UUID id);
}