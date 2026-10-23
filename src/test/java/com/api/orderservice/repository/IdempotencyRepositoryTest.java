package com.api.orderservice.repository;

import com.api.orderservice.PostgresIntegrationTest;
import com.api.orderservice.entity.IdempotencyEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class IdempotencyRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private IdempotencyRepository repository;

    @Test
    void shouldSaveAndRetrieveIdempotencyKey() {
        UUID key = UUID.randomUUID();

        repository.saveAndFlush(
                new IdempotencyEntity(key, "request-hash-123")
        );

        IdempotencyEntity retrieved = repository.findById(key)
                .orElseThrow();

        assertThat(retrieved.getIdempotencyKey()).isEqualTo(key);
        assertThat(retrieved.getRequestHash()).isEqualTo("request-hash-123");
        assertThat(retrieved.getStatus()).isEqualTo("COMPLETED");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRejectDuplicateIdempotencyKey() throws SQLException {
        UUID key = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO idempotency_keys
                    (idempotency_key, request_hash, status)
                VALUES (?, ?, ?)
                """, key, "request-hash-123", "COMPLETED");

        assertThatThrownBy(() ->
                jdbcTemplate.update("""
                        INSERT INTO idempotency_keys
                            (idempotency_key, request_hash, status)
                        VALUES (?, ?, ?)
                        """, key, "request-hash-456", "COMPLETED")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}