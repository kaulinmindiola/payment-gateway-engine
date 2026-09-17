package com.paymentgateway.engine.domain.port;

import java.util.UUID;

public interface UserRepositoryPort {

    boolean existsById(UUID id);
}