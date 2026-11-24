# EC Lakehouse API

Multi-vendor e-commerce backend: one Spring Boot microservice per domain, each with its own
database and its own Docker image. It is the operational system that will feed the lakehouse, so
table and field names follow the Olist dataset where they can (`seller_id`, `order_items`,
payment types, `order_approved_at` style timestamps).

**Stack:** Java 21, Spring Boot 4.2, Spring Cloud Gateway 2026.0, PostgreSQL 16, Flyway,
Spring Security (JWT), springdoc OpenAPI, Testcontainers.

## Services

| Service | Port | Database | Owns |
|---|---|---|---|
| `gateway` | 8080 | none | The only public entry point. Routes `/api/v1/**`, never routes `/internal/**` |
| `auth-service` | 8081 | `auth_db` | Users, roles, sign-up and login, access and refresh tokens, `/.well-known/jwks.json` |
| `vendor-service` | 8082 | `vendor_db` | Seller applications, profiles, admin approve, reject and suspend |
| `catalog-service` | 8083 | `catalog_db` | Categories, products, search, stock (`/internal/inventory/*`) |
| `cart-service` | 8084 | `cart_db` | One cart per user, grouped by vendor |
| `order-service` | 8085 | `order_db` | Checkout, orders, one vendor order per seller, shipping status |
| `payment-service` | 8086 | `payment_db` | Payments through a fake provider, vendor payouts after commission |

`common` is a shared library compiled into every service jar. It isn't deployed on its own. It
holds the JWT role mapping, problem-details error handling, token forwarding for
service-to-service calls, and test helpers.

```
client ──> gateway :8080 ──> auth | vendor | catalog | cart | order | payment
                                     each service ──> its own database in Postgres
```

**Auth.** auth-service signs RS256 access tokens (15 minutes) and rotating refresh tokens
(7 days). Every other service checks tokens itself against auth-service's public key, so nothing
calls auth-service per request. Roles are `CUSTOMER` (everyone), `VENDOR` (given when an admin
approves the seller) and `ADMIN`. After approval a vendor signs in again, or refreshes, to get a
token with the new role.

**Service-to-service.** Services call each other over REST and forward the caller's token.

**Checkout** (order-service) runs in this order:

1. Read the cart.
2. Reserve stock in catalog. This step is all or nothing, and it also returns the real prices.
3. Save the order, split by seller.
4. Authorize payment with per-vendor splits.
5. Clear the cart.

If payment is declined or fails, the stock is released and the order is kept as
`PAYMENT_FAILED`. Cancelling an order before anything ships refunds the payment and releases the
stock.

## Run it

You need Docker and JDK 21. Maven comes with the wrapper.

```bash
cd api
./mvnw package -DskipTests          # builds one jar per service
docker compose up -d --build        # Postgres + 7 service containers
scripts/e2e-smoke.sh                # full flow through the gateway (needs curl and jq)
```

The gateway is on http://localhost:8080. A first admin `admin@ec.local` / `change-me-admin` is
created on first start. Change it in `.env` (copy `.env.example`). Each service serves Swagger
UI at `/swagger-ui.html` on its own port inside the Docker network.

For a stable signing key, so restarts don't sign everyone out, run
`scripts/generate-jwt-keys.sh >> .env` before `docker compose up`.

To build or deploy one service alone:

```bash
./mvnw -pl order-service -am package -DskipTests
docker build -t ec-lakehouse/order-service order-service
```

Each service reads its settings from environment variables: `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `JWKS_URI`, the other services' `*_SERVICE_URL`, and `PORT`. Moving a service to its
own host or its own Postgres only means changing those values.

## Tests

```bash
./mvnw verify        # needs Docker running
```

- **Per-service integration tests** start the whole Spring app against a real PostgreSQL
  container (Testcontainers) and call the HTTP API with MockMvc. Calls to other services are
  mocked.
- **Gateway test** checks the routes and that `/internal/**` isn't exposed.
- **`scripts/e2e-smoke.sh`** runs against the real stack: two vendors get approved and list
  products, a customer buys from both in one checkout, each vendor ships and delivers, payouts
  are recorded, and a declined payment gives the stock back.

## API at a glance (all through the gateway)

| | |
|---|---|
| Auth | `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `GET /api/v1/users/me` |
| Admin | `GET /api/v1/admin/users`, `POST /api/v1/admin/users/{id}/roles`, `GET /api/v1/admin/vendors?status=PENDING`, `POST /api/v1/admin/vendors/{id}/approve\|reject\|suspend`, `POST /api/v1/categories` |
| Vendors | `POST /api/v1/vendors` (apply), `GET\|PUT /api/v1/vendors/me`, `GET /api/v1/vendors`, `GET /api/v1/vendors/{id}` |
| Catalog | `GET /api/v1/products?q=&categoryId=&sellerId=&minPrice=&maxPrice=&page=&size=&sort=`, `GET /api/v1/products/{id}`, `GET /api/v1/categories` |
| Vendor products | `POST /api/v1/products`, `PUT /api/v1/products/{id}`, `PUT /api/v1/products/{id}/stock`, `DELETE /api/v1/products/{id}`, `GET /api/v1/vendor/products` |
| Cart | `GET /api/v1/cart`, `POST /api/v1/cart/items`, `PUT\|DELETE /api/v1/cart/items/{productId}`, `DELETE /api/v1/cart` |
| Orders | `POST /api/v1/orders/checkout`, `GET /api/v1/orders`, `GET /api/v1/orders/{id}`, `POST /api/v1/orders/{id}/cancel` |
| Vendor fulfilment | `GET /api/v1/vendor/orders?status=`, `POST /api/v1/vendor/orders/{id}/ship`, `POST /api/v1/vendor/orders/{id}/deliver` |
| Payments | `GET /api/v1/payments/orders/{orderId}`, `GET /api/v1/vendor/payouts` |

Errors use RFC 9457 problem details (`application/problem+json`).

## Not done yet

- **Events and clickstream (v2).** Planned: outbox tables or Debezium change capture on these
  databases, plus a tracking endpoint, to feed the lakehouse.
- **Reviews.** Moved out of this first cut.
- **Real payment provider.** `PaymentProvider` is the place to plug one in. The fake declines
  anything above `PAYMENT_FAKE_DECLINE_ABOVE` (default 10000).
- **Network-level protection for `/internal/**`.** Today those endpoints are protected because the
  gateway doesn't route them, plus the caller's token. Add mTLS or a network policy when this is
  deployed on Proxmox.
- **Interrupted checkouts.** If a service dies in the middle of a checkout, an order can be left in
  `PENDING_PAYMENT` with its stock held. A scheduled clean-up job, or a saga with events, would
  fix that.
- **Proxmox deployment specifics** (registry, compose or k3s, TLS) will be added once the target is
  decided.
