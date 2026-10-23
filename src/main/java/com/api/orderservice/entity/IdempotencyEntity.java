package com.api.orderservice.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyEntity() {
    }

    public IdempotencyEntity(UUID idempotencyKey, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = "COMPLETED";
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}