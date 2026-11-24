CREATE TABLE vendors (
    id              UUID PRIMARY KEY,           -- the owning user's id (seller_id everywhere else)
    store_name      VARCHAR(120) NOT NULL,
    description     TEXT,
    contact_email   VARCHAR(320) NOT NULL,
    zip_code_prefix VARCHAR(10),
    city            VARCHAR(120),
    state           VARCHAR(2),
    status          VARCHAR(20)  NOT NULL,
    approved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uq_vendors_store_name ON vendors (LOWER(store_name));
CREATE INDEX idx_vendors_status ON vendors (status);
