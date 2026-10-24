package com.api.orderservice.service;

import com.api.orderservice.dto.CreateOrderRequest;
import com.api.orderservice.dto.OrderResponse;
import com.api.orderservice.entity.IdempotencyEntity;
import com.api.orderservice.entity.OrderEntity;
import com.api.orderservice.exception.OrderNotFoundException;
import com.api.orderservice.repository.IdempotencyRepository;
import com.api.orderservice.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final EntityManager entityManager;
    private final CacheManager cacheManager;

    public OrderService(
            OrderRepository orderRepository,
            IdempotencyRepository idempotencyRepository,
            EntityManager entityManager,
            CacheManager cacheManager
    ) {
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.entityManager = entityManager;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public OrderResponse createOrder(
            CreateOrderRequest request,
            UUID idempotencyKey
    ) {
        int inserted = idempotencyRepository.reserveKey(
                idempotencyKey,
                "DEFERRED"
        );

        if (inserted == 0) {
            IdempotencyEntity existing = idempotencyRepository
                    .findById(idempotencyKey)
                    .orElseThrow();

            if (existing.getOrderId() == null) {
                throw new IllegalStateException(
                        "Idempotency key has no associated order"
                );
            }

            OrderEntity existingOrder = orderRepository
                    .findById(existing.getOrderId())
                    .orElseThrow();

            return toResponse(existingOrder);
        }

        OrderEntity order = new OrderEntity(
                request.productId(),
                request.quantity(),
                "CREATED"
        );

        OrderEntity saved = orderRepository.saveAndFlush(order);
        entityManager.refresh(saved);

        // Associate the reserved key with the new order.
        IdempotencyEntity reservation = idempotencyRepository
                .findById(idempotencyKey)
                .orElseThrow();

        reservation.setOrderId(saved.getId());

        return toResponse(saved);
    }

    private OrderResponse toResponse(OrderEntity order) {
        return new OrderResponse(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }

    @Cacheable(cacheNames = "orders", key = "#orderId")
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return toResponse(order);
    }

    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#orderId")
    public OrderResponse updateOrderStatus(Long orderId, String status) {

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.updateStatus(status);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        Cache cache = cacheManager.getCache("orders");

                        if (cache == null) {
                            throw new IllegalStateException(
                                    "Orders cache is not configured"
                            );
                        }

                        cache.evictIfPresent(orderId);
                    }
                }
        );

        return toResponse(order);
    }
}