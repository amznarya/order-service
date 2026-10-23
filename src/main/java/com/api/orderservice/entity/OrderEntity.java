package com.api.orderservice.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OrderEntity() {
        // Required by JPA
    }

    public OrderEntity(String productId, Integer quantity, String status) {
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}