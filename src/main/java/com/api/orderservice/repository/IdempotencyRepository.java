package com.api.orderservice.repository;

import com.api.orderservice.entity.IdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface IdempotencyRepository
        extends JpaRepository<IdempotencyEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys
                (idempotency_key, request_hash, status)
            VALUES (:key, :requestHash, 'COMPLETED')
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int reserveKey(
            @Param("key") UUID key,
            @Param("requestHash") String requestHash
    );
}