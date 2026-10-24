package com.api.orderservice.service;

import com.api.orderservice.dto.CreateOrderRequest;
import com.api.orderservice.dto.OrderResponse;
import com.api.orderservice.entity.IdempotencyEntity;
import com.api.orderservice.entity.OrderEntity;
import com.api.orderservice.exception.OrderNotFoundException;
import com.api.orderservice.repository.IdempotencyRepository;
import com.api.orderservice.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.UUID;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final EntityManager entityManager;
    private final CacheManager cacheManager;
    private final ObservationRegistry observationRegistry;

    public OrderService(
            OrderRepository orderRepository,
            IdempotencyRepository idempotencyRepository,
            EntityManager entityManager,
            CacheManager cacheManager, ObservationRegistry observationRegistry
    ) {
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.entityManager = entityManager;
        this.cacheManager = cacheManager;
        this.observationRegistry = observationRegistry;
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

            log.debug(
                    "event=order.idempotency.replayed orderId={}",
                    existing.getOrderId()
            );
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

        OrderEntity order = Observation
                .createNotStarted("order.database.read", observationRegistry)
                .highCardinalityKeyValue(
                        "order.id",
                        orderId.toString()
                )
                .observe(() -> orderRepository.findById(orderId)
                        .orElseThrow(() ->
                                new OrderNotFoundException(orderId)
                        ));

        log.debug("event=order.database.read orderId={}", orderId);

        return toResponse(order);

//        OrderEntity order = orderRepository.findById(orderId)
//                .orElseThrow(() -> new OrderNotFoundException(orderId));
//        log.debug(
//                "event=order.database.read orderId={}",
//                orderId
//        );
//        return toResponse(order);
    }

    @Transactional
    @CacheEvict(cacheNames = "orders", key = "#orderId")
    public OrderResponse updateOrderStatus(Long orderId, String status) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.updateStatus(status);

        return toResponse(order);
    }
}