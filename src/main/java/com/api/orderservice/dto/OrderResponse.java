package com.api.orderservice.dto;

import java.time.OffsetDateTime;

public record OrderResponse(
        Long id,
        String productId,
        Integer quantity,
        String status,
        OffsetDateTime createdAt
) {}