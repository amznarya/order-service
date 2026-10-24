package com.api.orderservice.controller;

import com.api.orderservice.PostgresIntegrationTest;
import com.api.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@SpringBootTest
class OrderControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    private long observationCount(String metricName) {
        return meterRegistry.find(metricName)
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum();
    }

    private double counterValue(String metricName) {
        Counter counter = meterRegistry.find(metricName).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void shouldCreateOrder() throws Exception {
        long countBefore = orderRepository.count();
        String key = UUID.randomUUID().toString();

        mockMvc().perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("""
                                {
                                    "productId": "product-123",
                                    "quantity": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value("product-123"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.id").isNumber());
        assertEquals(countBefore + 1, orderRepository.count());
    }

    @Test
    void shouldReturn404ForMissingOrder() throws Exception {
        mockMvc().perform(get("/api/v1/orders/999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnSameOrderForDuplicateRequest() throws Exception {
        String key = UUID.randomUUID().toString();

        String requestBody = """
                {
                    "productId": "product-123",
                    "quantity": 2
                }
                """;

        MvcResult firstResponse = mockMvc()
                .perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult secondResponse = mockMvc()
                .perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        ObjectMapper mapper = new ObjectMapper();

        JsonNode first = mapper.readTree(
                firstResponse.getResponse().getContentAsString()
        );

        JsonNode second = mapper.readTree(
                secondResponse.getResponse().getContentAsString()
        );

        assertEquals(first.get("id").asLong(), second.get("id").asLong());
    }

    @Test
    void shouldHandleConcurrentRequestsWithSameIdempotencyKey()
            throws Exception {

        String key = UUID.randomUUID().toString();

        String requestBody = """
        {
            "productId": "product-123",
            "quantity": 2
        }
        """;

        long countBefore = orderRepository.count();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        Callable<Long> task = () -> {
            startGate.await();

            MvcResult result = mockMvc()
                    .perform(post("/api/v1/orders")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode response = objectMapper.readTree(
                    result.getResponse().getContentAsString()
            );

            return response.get("id").asLong();
        };

        try {
            Future<Long> first = executor.submit(task);
            Future<Long> second = executor.submit(task);

            startGate.countDown();

            Long firstId = first.get(15, TimeUnit.SECONDS);
            Long secondId = second.get(15, TimeUnit.SECONDS);

            assertEquals(firstId, secondId);
            assertEquals(countBefore + 1, orderRepository.count());

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectZeroQuantity() throws Exception {
        mockMvc().perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "productId": "product-123",
                    "quantity": 0
                }
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectBlankProductId() throws Exception {
        mockMvc().perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "productId": "",
                    "quantity": 2
                }
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectMissingIdempotencyKey() throws Exception {
        mockMvc().perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "productId": "product-123",
                    "quantity": 2
                }
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRecordSuccessfulBusinessOperations() throws Exception {

        double createsBefore = counterValue("orders.create.completed");
        double updatesBefore = counterValue("orders.status.update.completed");

        String key = UUID.randomUUID().toString();

        MvcResult created = mockMvc().perform(
                post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "productId": "metrics-test",
                                "quantity": 1
                            }
                            """)
        ).andExpect(status().isCreated()).andReturn();

        long orderId = objectMapper.readTree(
                created.getResponse().getContentAsString()
        ).get("id").asLong();

        assertThat(counterValue("orders.create.completed") - createsBefore)
                .isEqualTo(1.0);

        mockMvc().perform(
                patch("/api/v1/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"status": "SHIPPED"}
                            """)
        ).andExpect(status().isOk());

        assertThat(counterValue("orders.status.update.completed") - updatesBefore)
                .isEqualTo(1.0);
    }

    @Test
    void shouldRecordCustomOrderObservations() throws Exception {

        long createsBefore = observationCount("order.create");
        long getsBefore = observationCount("order.get");
        long readsBefore = observationCount("order.database.read");
        long updatesBefore = observationCount("order.status.update");

        String key = UUID.randomUUID().toString();

        // Create new order
        MvcResult created = mockMvc().perform(
                post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "productId": "tracing-integration",
                                "quantity": 1
                            }
                            """)
        ).andExpect(status().isCreated()).andReturn();

        long orderId = objectMapper.readTree(
                created.getResponse().getContentAsString()
        ).get("id").asLong();

        // First GET = cache miss
        mockMvc().perform(
                get("/api/v1/orders/{orderId}", orderId)
        ).andExpect(status().isOk());

        // Second GET = cache hit
        mockMvc().perform(
                get("/api/v1/orders/{orderId}", orderId)
        ).andExpect(status().isOk());

        // Update status
        mockMvc().perform(
                patch("/api/v1/orders/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"status":"SHIPPED"}
                            """)
        ).andExpect(status().isOk());

        assertThat(observationCount("order.create") - createsBefore)
                .isEqualTo(1);

        assertThat(observationCount("order.get") - getsBefore)
                .isEqualTo(2);

        assertThat(observationCount("order.database.read") - readsBefore)
                .isEqualTo(1);

        assertThat(observationCount("order.status.update") - updatesBefore)
                .isEqualTo(1);
    }
}