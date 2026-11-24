CREATE TABLE payments (
    id                 UUID PRIMARY KEY,
    order_id           UUID           NOT NULL UNIQUE,
    customer_id        UUID           NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    method             VARCHAR(20)    NOT NULL,
    installments       INTEGER        NOT NULL DEFAULT 1,
    status             VARCHAR(20)    NOT NULL,
    provider_reference VARCHAR(100),
    decline_reason     VARCHAR(500),
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL
);

CREATE TABLE vendor_payouts (
    id           UUID PRIMARY KEY,
    payment_id   UUID           NOT NULL REFERENCES payments (id),
    order_id     UUID           NOT NULL,
    seller_id    UUID           NOT NULL,
    gross_amount NUMERIC(12, 2) NOT NULL,
    commission   NUMERIC(12, 2) NOT NULL,
    net_amount   NUMERIC(12, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_vendor_payouts_seller ON vendor_payouts (seller_id, created_at DESC);
