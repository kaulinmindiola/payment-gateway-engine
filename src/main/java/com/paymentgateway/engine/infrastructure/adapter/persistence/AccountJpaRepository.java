package com.paymentgateway.engine.infrastructure.adapter.persistence;

import com.paymentgateway.engine.infrastructure.adapter.persistence.entity.AccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    /**
     * Lock explícito PESSIMISTIC_WRITE. Se usa @Query (no el findById heredado)
     * para que la intención quede en el nombre del método y no dependa de
     * anotar el CRUD estándar, que otras rutas (findById sin lock) siguen usando.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id = :id")
    Optional<AccountEntity> findByIdForUpdate(@Param("id") UUID id);
}