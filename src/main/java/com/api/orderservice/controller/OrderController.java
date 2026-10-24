package com.api.orderservice.controller;

import com.api.orderservice.dto.CreateOrderRequest;
import com.api.orderservice.dto.OrderResponse;
import com.api.orderservice.dto.UpdateOrderStatusRequest;
import com.api.orderservice.service.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public OrderController(
            OrderService orderService,
            MeterRegistry meterRegistry, ObservationRegistry observationRegistry
    ) {
        this.orderService = orderService;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }


    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {

        OrderResponse response = Observation
                .createNotStarted("order.create", observationRegistry)
                .observe(() -> orderService.createOrder(
                        request, idempotencyKey
                ));

//        OrderResponse response =
//                orderService.createOrder(request, idempotencyKey);
        log.info(
                "event=order.create.completed orderId={} productId={} quantity={}",
                response.id(),
                response.productId(),
                response.quantity()
        );
        meterRegistry.counter("orders.create.completed").increment();
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + response.id()))
                .body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long orderId
    ) {
        OrderResponse response = Observation
                .createNotStarted("order.get", observationRegistry)
                .observe(() -> orderService.getOrder(orderId));

        return ResponseEntity.ok(response);
//        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        OrderResponse response = Observation
                .createNotStarted("order.status.update", observationRegistry)
                .observe(() -> orderService.updateOrderStatus(
                        orderId, request.status()
                ));
//        OrderResponse response = orderService.updateOrderStatus(
//                orderId,
//                request.status()
//        );
        log.info(
                "event=order.status.updated orderId={} status={}",
                response.id(),
                response.status()
        );
        meterRegistry.counter("orders.status.update.completed").increment();
        return ResponseEntity.ok(response);
    }
}