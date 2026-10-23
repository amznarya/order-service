package com.api.orderservice;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class PostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    static {
        postgres.start();
    }
}