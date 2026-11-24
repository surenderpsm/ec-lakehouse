CREATE TABLE carts (
    id          UUID PRIMARY KEY,
    customer_id UUID        NOT NULL UNIQUE,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE cart_items (
    id           UUID PRIMARY KEY,
    cart_id      UUID           NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id   UUID           NOT NULL,
    seller_id    UUID           NOT NULL,
    product_name VARCHAR(200)   NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER        NOT NULL CHECK (quantity > 0),
    added_at     TIMESTAMPTZ    NOT NULL,
    UNIQUE (cart_id, product_id)
);
