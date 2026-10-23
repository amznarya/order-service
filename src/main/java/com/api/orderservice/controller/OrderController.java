package com.api.orderservice.controller;

import com.api.orderservice.dto.CreateOrderRequest;
import com.api.orderservice.dto.OrderResponse;
import com.api.orderservice.service.OrderService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response =
                orderService.createOrder(request, idempotencyKey);

        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + response.id()))
                .body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }
}