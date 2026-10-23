package com.api.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisConnectivityTest extends PostgresIntegrationTest {

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Test
    void shouldConnectToRedis() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }
}