CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        product_id VARCHAR(100) NOT NULL,
                        quantity INTEGER NOT NULL CHECK (quantity > 0),
                        status VARCHAR(30) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_created_at
    ON orders (created_at);