package com.api.orderservice;

import com.api.orderservice.filter.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class CorrelationIdIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(correlationIdFilter)
                .build();
    }

    @Test
    void shouldGenerateCorrelationId() throws Exception {
        var result = mockMvc.perform(
                        get("/api/v1/orders/999999999"))
                .andExpect(status().isNotFound())
                .andReturn();

        String id = result.getResponse()
                .getHeader("X-Correlation-ID");

        assertThat(id).isNotNull();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldPreserveExistingCorrelationId() throws Exception {
        String id = UUID.randomUUID().toString();

        mockMvc.perform(
                        get("/api/v1/orders/999999999")
                                .header("X-Correlation-ID", id))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-ID", id));

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldReplaceInvalidCorrelationId() throws Exception {
        var result = mockMvc.perform(
                        get("/api/v1/orders/999999999")
                                .header("X-Correlation-ID", "invalid-id"))
                .andExpect(status().isNotFound())
                .andReturn();

        String id = result.getResponse()
                .getHeader("X-Correlation-ID");

        assertThat(id).isNotEqualTo("invalid-id");
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(MDC.get("correlationId")).isNull();
    }
}