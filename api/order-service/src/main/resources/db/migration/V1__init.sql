CREATE TABLE orders (
    id                   UUID PRIMARY KEY,
    customer_id          UUID           NOT NULL,
    status               VARCHAR(30)    NOT NULL,
    total_amount         NUMERIC(12, 2) NOT NULL,
    payment_method       VARCHAR(20)    NOT NULL,
    installments         INTEGER        NOT NULL DEFAULT 1,
    payment_id           UUID,
    ship_recipient       VARCHAR(200),
    ship_street          VARCHAR(300),
    ship_city            VARCHAR(120),
    ship_state           VARCHAR(2),
    ship_zip_code_prefix VARCHAR(10),
    placed_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_orders_customer ON orders (customer_id, created_at DESC);

CREATE TABLE vendor_orders (
    id           UUID PRIMARY KEY,
    order_id     UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    seller_id    UUID           NOT NULL,
    status       VARCHAR(30)    NOT NULL,
    subtotal     NUMERIC(12, 2) NOT NULL,
    shipped_at   TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    UNIQUE (order_id, seller_id)
);

CREATE INDEX idx_vendor_orders_seller ON vendor_orders (seller_id, status);

CREATE TABLE order_items (
    id              UUID PRIMARY KEY,
    vendor_order_id UUID           NOT NULL REFERENCES vendor_orders (id) ON DELETE CASCADE,
    product_id      UUID           NOT NULL,
    product_name    VARCHAR(200)   NOT NULL,
    unit_price      NUMERIC(12, 2) NOT NULL,
    quantity        INTEGER        NOT NULL CHECK (quantity > 0),
    line_total      NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_order_items_vendor_order ON order_items (vendor_order_id);
