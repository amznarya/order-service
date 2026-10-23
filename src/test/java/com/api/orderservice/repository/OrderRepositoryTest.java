package com.api.orderservice.repository;

import com.api.orderservice.entity.OrderEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class OrderRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndRetrieveOrder() {
        OrderEntity order = new OrderEntity(
                "product-123",
                2,
                "CREATED"
        );

        OrderEntity saved = orderRepository.saveAndFlush(order);
        Long orderId = saved.getId();

        // Force the next read to fetch from PostgreSQL
        entityManager.clear();

        OrderEntity retrieved = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(retrieved.getId()).isEqualTo(orderId);
        assertThat(retrieved.getProductId()).isEqualTo("product-123");
        assertThat(retrieved.getQuantity()).isEqualTo(2);
        assertThat(retrieved.getStatus()).isEqualTo("CREATED");
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }
}