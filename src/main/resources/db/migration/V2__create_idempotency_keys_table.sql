CREATE TABLE idempotency_keys (
                                  idempotency_key UUID PRIMARY KEY,
                                  request_hash VARCHAR(64) NOT NULL,
                                  order_id BIGINT REFERENCES orders(id),
                                  status VARCHAR(20) NOT NULL,
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);