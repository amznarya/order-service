package com.api.orderservice.repository;

import com.api.orderservice.entity.OrderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldSaveAndRetrieveOrder() {
        // Arrange
        OrderEntity order = new OrderEntity(
                "product-123",
                2,
                "CREATED"
        );

        // Act
        OrderEntity saved = orderRepository.saveAndFlush(order);
        OrderEntity retrieved = orderRepository.findById(saved.getId())
                .orElseThrow();

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(retrieved.getProductId()).isEqualTo("product-123");
        assertThat(retrieved.getQuantity()).isEqualTo(2);
        assertThat(retrieved.getStatus()).isEqualTo("CREATED");
    }
}