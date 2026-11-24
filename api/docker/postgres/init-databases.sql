-- One database per service. Locally they share this Postgres container; in production each
-- service can point DB_URL at its own server instead.
CREATE DATABASE auth_db;
CREATE DATABASE vendor_db;
CREATE DATABASE catalog_db;
CREATE DATABASE cart_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
