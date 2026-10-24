package com.api.orderservice;

import com.api.orderservice.dto.CreateOrderRequest;
import com.api.orderservice.dto.OrderResponse;
import com.api.orderservice.entity.OrderEntity;
import com.api.orderservice.repository.OrderRepository;
import com.api.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrderCacheIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    static {
        redis.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> redis.getMappedPort(6379)
        );
    }

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Test
    void shouldConnectToRedisTestContainer() {
        try (var connection = connectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }

    @Test
    void shouldReadFromDatabaseOnlyOnCacheMiss() {

        // STEP 1: Create a new order using the existing service
        CreateOrderRequest request = new CreateOrderRequest(
                "iphone-17",
                2
        );

        OrderResponse createdOrder = orderService.createOrder(
                request,
                UUID.randomUUID()
        );

        Long orderId = createdOrder.id();

        // STEP 2: Ensure Redis does not contain this order
        redisTemplate.delete("orders::" + orderId);

        // STEP 3: Clear previous repository interactions
        clearInvocations(orderRepository);

        // STEP 4: First GET -> Cache MISS -> PostgreSQL
        OrderResponse firstResponse = orderService.getOrder(orderId);

        // PostgreSQL should have been queried exactly once
        verify(orderRepository, times(1)).findById(orderId);

        // STEP 5: Second GET -> Cache HIT -> No PostgreSQL query
        OrderResponse secondResponse = orderService.getOrder(orderId);

        // Repository should STILL have been called only once
        verify(orderRepository, times(1)).findById(orderId);

        // STEP 6: Verify both responses are identical
        assertThat(secondResponse).isEqualTo(firstResponse);

        // STEP 7: Verify Redis contains the cached order
        Boolean cacheKeyExists = redisTemplate.hasKey(
                "orders::" + orderId
        );

        assertThat(cacheKeyExists).isTrue();
    }

    @Test
    void shouldQueryDatabaseAgainAfterCacheExpires() throws Exception {

        // Create an order
        CreateOrderRequest request =
                new CreateOrderRequest("macbook-pro", 1);

        OrderResponse created = orderService.createOrder(
                request,
                UUID.randomUUID()
        );

        Long orderId = created.id();
        String cacheKey = "orders::" + orderId;

        redisTemplate.delete(cacheKey);
        clearInvocations(orderRepository);

        // First GET: cache MISS
        orderService.getOrder(orderId);

        verify(orderRepository, times(1)).findById(orderId);
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // Shorten the existing cache entry's TTL
        redisTemplate.expire(cacheKey, Duration.ofSeconds(1));

        // Wait for expiration
        Thread.sleep(1500);

        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        // Second GET: cache MISS again
        orderService.getOrder(orderId);

        verify(orderRepository, times(2)).findById(orderId);

        // Redis should be populated again
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }

    @Test
    void shouldInvalidateCacheAfterOrderStatusUpdate() {

        // 1. Create a fresh order
        CreateOrderRequest request =
                new CreateOrderRequest("iphone-17", 2);

        OrderResponse created = orderService.createOrder(
                request,
                UUID.randomUUID()
        );

        Long orderId = created.id();
        String cacheKey = "orders::" + orderId;

        // 2. Ensure cache starts empty
        redisTemplate.delete(cacheKey);
        clearInvocations(orderRepository);

        // 3. First GET -> Cache MISS -> PostgreSQL
        OrderResponse first = orderService.getOrder(orderId);

        assertThat(first.status()).isEqualTo("CREATED");
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        verify(orderRepository, times(1)).findById(orderId);

        // 4. Update status -> PostgreSQL commit -> Redis eviction
        OrderResponse updated =
                orderService.updateOrderStatus(orderId, "SHIPPED");

        assertThat(updated.status()).isEqualTo("SHIPPED");

        // Redis key should be deleted after commit
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        // 5. Second GET -> Cache MISS -> PostgreSQL
        OrderResponse second = orderService.getOrder(orderId);

        assertThat(second.status()).isEqualTo("SHIPPED");
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // 6. Third GET -> Cache HIT
        OrderResponse third = orderService.getOrder(orderId);

        assertThat(third.status()).isEqualTo("SHIPPED");

        // 7. Verify only one DB read for the second and third GET
        // Clear previous interactions because updateOrderStatus
        // also calls findById()
        // We'll refine the repository-call assertions separately.
    }


    @Test
    void shouldPreserveCacheWhenTransactionRollsBack() {

        // Create a committed order
        OrderResponse created = orderService.createOrder(
                new CreateOrderRequest("rollback-product", 1),
                UUID.randomUUID()
        );

        Long orderId = created.id();
        String cacheKey = "orders::" + orderId;

        redisTemplate.delete(cacheKey);

        // Populate Redis with CREATED
        OrderResponse cached = orderService.getOrder(orderId);

        assertThat(cached.status()).isEqualTo("CREATED");
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // Simulate a transaction that rolls back
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {

            orderService.updateOrderStatus(orderId, "SHIPPED");

            // Force the enclosing transaction to roll back
            status.setRollbackOnly();
        });

        // The cached value should still exist
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // Cache should still contain CREATED
        OrderResponse afterRollback = orderService.getOrder(orderId);

        assertThat(afterRollback.status()).isEqualTo("CREATED");

        // Verify PostgreSQL also retained CREATED
        OrderEntity persisted = orderRepository.findById(orderId)
                .orElseThrow();

        assertThat(persisted.getStatus()).isEqualTo("CREATED");
    }
}