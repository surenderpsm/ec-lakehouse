#!/usr/bin/env bash
# End-to-end check against a running stack (docker compose up), through the gateway only.
# Walks the main multi-vendor flow: two vendors get approved and list products, a customer buys
# from both in one checkout, each vendor ships their part, payouts are recorded.
#
#   scripts/e2e-smoke.sh                       # gateway on http://localhost:8080
#   BASE_URL=http://10.0.0.5:8080 scripts/e2e-smoke.sh
#
# Needs curl and jq.
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
ADMIN_EMAIL=${BOOTSTRAP_ADMIN_EMAIL:-admin@ec.local}
ADMIN_PASSWORD=${BOOTSTRAP_ADMIN_PASSWORD:-change-me-admin}
RUN=$(date +%s)$RANDOM
BODY=$(mktemp)
STATUS_FILE=$(mktemp)
trap 'rm -f "$BODY" "$STATUS_FILE"' EXIT

pass() { printf '  \033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$1"; exit 1; }

# call METHOD PATH [TOKEN] [JSON] -> prints body; the HTTP status is kept for `expect`
# (in a file, because calls usually run inside $(...) subshells)
call() {
  local method=$1 path=$2 token=${3:-} data=${4:-}
  local args=(-s -o "$BODY" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  [[ -n $token ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n $data ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  curl "${args[@]}" >"$STATUS_FILE"
  cat "$BODY"
}

expect() { # expect STATUS DESCRIPTION
  local STATUS
  STATUS=$(cat "$STATUS_FILE")
  [[ $STATUS == "$1" ]] && pass "$2" || fail "$2 (expected HTTP $1, got $STATUS)"
}

login() { call POST /api/v1/auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}" | jq -r .accessToken; }

register_and_login() { # sets USER_ID and USER_TOKEN
  local email="$1-$RUN@e2e.local"
  USER_ID=$(call POST /api/v1/auth/register "" "{\"email\":\"$email\",\"password\":\"password-123\",\"fullName\":\"$1\"}" | jq -r .id)
  expect 201 "register $1"
  USER_TOKEN=$(login "$email" password-123)
  expect 200 "$1 signs in"
}

echo "Waiting for $BASE_URL ..."
code() { curl -s -o /dev/null -w '%{http_code}' "$BASE_URL$1"; }
for _ in $(seq 1 60); do
  # every service answers through the gateway: public 200s, protected 401s
  [[ $(code /api/v1/products) == 200 && $(code /api/v1/vendors) == 200 && $(code /api/v1/cart) == 401 \
    && $(code /api/v1/orders) == 401 && $(code /api/v1/vendor/payouts) == 401 \
    && $(code /api/v1/users/me) == 401 ]] && break
  sleep 2
done

echo "Admin and vendors"
ADMIN=$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD"); expect 200 "admin signs in"

make_vendor() { # sets USER_ID and USER_TOKEN (a token that carries the VENDOR role)
  register_and_login "$1"
  call POST /api/v1/vendors "$USER_TOKEN" "{\"storeName\":\"$1 $RUN\",\"contactEmail\":\"$1@e2e.local\",\"city\":\"Curitiba\",\"state\":\"PR\"}" >/dev/null
  expect 201 "$1 applies as a vendor"
  call POST /api/v1/products "$USER_TOKEN" '{"name":"x","price":1}' >/dev/null
  expect 403 "$1 cannot list products before approval"
  call POST "/api/v1/admin/vendors/$USER_ID/approve" "$ADMIN" >/dev/null
  expect 200 "admin approves $1"
  # sign in again so the new token carries the VENDOR role
  USER_TOKEN=$(login "$1-$RUN@e2e.local" password-123)
  expect 200 "$1 signs in again with the VENDOR role"
}
make_vendor vendor-a; VENDOR_A=$USER_ID; TOKEN_A=$USER_TOKEN
make_vendor vendor-b; VENDOR_B=$USER_ID; TOKEN_B=$USER_TOKEN

echo "Catalog"
CATEGORY=$(call POST /api/v1/categories "$ADMIN" "{\"name\":\"Home $RUN\",\"slug\":\"home-$RUN\"}" | jq -r .id)
expect 201 "admin creates a category"
LAMP=$(call POST /api/v1/products "$TOKEN_A" "{\"name\":\"Lamp $RUN\",\"price\":20.00,\"stockQuantity\":10,\"categoryId\":\"$CATEGORY\"}" | jq -r .id)
expect 201 "vendor A lists a lamp"
BOOK=$(call POST /api/v1/products "$TOKEN_B" "{\"name\":\"Book $RUN\",\"price\":35.00,\"stockQuantity\":5,\"categoryId\":\"$CATEGORY\"}" | jq -r .id)
expect 201 "vendor B lists a book"
FOUND=$(call GET "/api/v1/products?q=$RUN" | jq '.totalElements')
[[ $FOUND == 2 ]] && pass "search finds both products" || fail "search found $FOUND products"

echo "Customer checkout"
register_and_login customer; TOKEN_C=$USER_TOKEN
call POST /api/v1/cart/items "$TOKEN_C" "{\"productId\":\"$LAMP\",\"quantity\":2}" >/dev/null; expect 200 "add 2 lamps to cart"
CART=$(call POST /api/v1/cart/items "$TOKEN_C" "{\"productId\":\"$BOOK\",\"quantity\":1}"); expect 200 "add a book to cart"
[[ $(jq '.vendors | length' <<<"$CART") == 2 ]] && pass "cart groups items under 2 vendors" || fail "cart vendor groups"

ADDRESS='{"recipient":"Ana","street":"Rua A, 1","city":"Sao Paulo","state":"SP","zipCodePrefix":"01310"}'
ORDER=$(call POST /api/v1/orders/checkout "$TOKEN_C" "{\"shippingAddress\":$ADDRESS,\"paymentMethod\":\"CREDIT_CARD\",\"installments\":2}")
expect 201 "checkout"
ORDER_ID=$(jq -r .id <<<"$ORDER")
[[ $(jq -r .status <<<"$ORDER") == PLACED ]] && pass "order is PLACED" || fail "order status $(jq -r .status <<<"$ORDER")"
[[ $(jq -r .totalAmount <<<"$ORDER") == 75* ]] && pass "total is 75.00" || fail "total $(jq -r .totalAmount <<<"$ORDER")"
[[ $(jq '.vendorOrders | length' <<<"$ORDER") == 2 ]] && pass "order split into 2 vendor orders" || fail "vendor order split"
[[ $(call GET /api/v1/cart "$TOKEN_C" | jq '.items | length') == 0 ]] && pass "cart emptied" || fail "cart not emptied"
[[ $(call GET "/api/v1/products/$LAMP" | jq .stockQuantity) == 8 ]] && pass "lamp stock went 10 -> 8" || fail "lamp stock"
[[ $(call GET "/api/v1/payments/orders/$ORDER_ID" "$TOKEN_C" | jq -r .status) == AUTHORIZED ]] && pass "payment authorized" || fail "payment status"

echo "Fulfilment"
SUB_A=$(call GET /api/v1/vendor/orders "$TOKEN_A" | jq -r '.content[0].id')
[[ $(call GET /api/v1/vendor/orders "$TOKEN_A" | jq '.content[0].items[0].quantity') == 2 ]] && pass "vendor A sees only their lamps" || fail "vendor A view"
SUB_B=$(call GET /api/v1/vendor/orders "$TOKEN_B" | jq -r '.content[0].id')
call POST "/api/v1/vendor/orders/$SUB_A/ship" "$TOKEN_B" >/dev/null; expect 404 "vendor B cannot ship vendor A's part"
call POST "/api/v1/vendor/orders/$SUB_A/ship" "$TOKEN_A" >/dev/null; expect 200 "vendor A ships"
[[ $(call GET "/api/v1/orders/$ORDER_ID" "$TOKEN_C" | jq -r .status) == PARTIALLY_SHIPPED ]] && pass "order is PARTIALLY_SHIPPED" || fail "partial status"
call POST "/api/v1/vendor/orders/$SUB_B/ship" "$TOKEN_B" >/dev/null; expect 200 "vendor B ships"
call POST "/api/v1/vendor/orders/$SUB_A/deliver" "$TOKEN_A" >/dev/null; expect 200 "vendor A delivers"
call POST "/api/v1/vendor/orders/$SUB_B/deliver" "$TOKEN_B" >/dev/null; expect 200 "vendor B delivers"
[[ $(call GET "/api/v1/orders/$ORDER_ID" "$TOKEN_C" | jq -r .status) == DELIVERED ]] && pass "order is DELIVERED" || fail "delivered status"
NET=$(call GET /api/v1/vendor/payouts "$TOKEN_A" | jq -r '.content[0].netAmount')
[[ $NET == 36* ]] && pass "vendor A payout is 36.00 after 10% commission" || fail "vendor A payout $NET"

echo "Failure paths"
PRICEY=$(call POST /api/v1/products "$TOKEN_A" "{\"name\":\"Piano $RUN\",\"price\":20000.00,\"stockQuantity\":1}" | jq -r .id)
call POST /api/v1/cart/items "$TOKEN_C" "{\"productId\":\"$PRICEY\",\"quantity\":1}" >/dev/null
call POST /api/v1/orders/checkout "$TOKEN_C" "{\"shippingAddress\":$ADDRESS,\"paymentMethod\":\"BOLETO\"}" >/dev/null
expect 402 "payment over the fake provider's limit is declined"
[[ $(call GET "/api/v1/products/$PRICEY" | jq .stockQuantity) == 1 ]] && pass "declined checkout gives the stock back" || fail "stock not released"
call POST /internal/inventory/reserve "$TOKEN_C" '{"items":[]}' >/dev/null; expect 404 "gateway hides /internal endpoints"
call GET /api/v1/orders >/dev/null; expect 401 "orders need a token"

echo "All checks passed."
