CREATE TABLE categories (
    id        UUID PRIMARY KEY,
    name      VARCHAR(120) NOT NULL,
    slug      VARCHAR(120) NOT NULL UNIQUE,
    parent_id UUID REFERENCES categories (id)
);

CREATE TABLE products (
    id             UUID PRIMARY KEY,
    seller_id      UUID           NOT NULL,
    category_id    UUID REFERENCES categories (id),
    name           VARCHAR(200)   NOT NULL,
    description    TEXT,
    price          NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    stock_quantity INTEGER        NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    weight_g       INTEGER,
    status         VARCHAR(20)    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_products_seller ON products (seller_id);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_status_price ON products (status, price);
