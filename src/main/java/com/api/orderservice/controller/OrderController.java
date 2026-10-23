package com.api.orderservice.controller;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping
    public Map<String, Object> createOrder(
            @RequestBody Map<String, Object> request) {

        return Map.of(
                "orderId", UUID.randomUUID().toString(),
                "status", "CREATED"
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> getOrder(
            @PathVariable String id) {

        return Map.of(
                "orderId", id,
                "status", "CREATED"
        );
    }
}