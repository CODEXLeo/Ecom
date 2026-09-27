#!/usr/bin/env bash

# Load local test credentials (Ecom/.env.test)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

if [[ ! -f "$PROJECT_ROOT/.env.test" ]]; then
    echo "ERROR: $PROJECT_ROOT/.env.test not found."
    exit 1
fi

set -a
source "$PROJECT_ROOT/.env.test"
set +a


# ============================================================
# Order Service - End-to-End Validation
# ============================================================
#
# Requires the following services to be running:
#   User Service     https://localhost:8443
#   Product Service  https://localhost:8444
#   Order Service    https://localhost:8445
#   Payment Service  https://localhost:8446
#
# The test creates a temporary product through Product Service,
# exercises Order -> Inventory -> Payment, then removes the product.
#
# Optional environment variables:
#   ADMIN_EMAIL / ADMIN_PASSWORD
#   USER_EMAIL  / USER_PASSWORD
#   ORDER_CLIENT_CERT / ORDER_CLIENT_KEY
# ============================================================

set -u
set -o pipefail

USER_SERVICE="https://localhost:8443"
PRODUCT_SERVICE="https://localhost:8444"
ORDER_SERVICE="https://localhost:8445"

ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
USER_EMAIL="${USER_EMAIL:-}"
USER_PASSWORD="${USER_PASSWORD:-}"

ORDER_CLIENT_CERT="${ORDER_CLIENT_CERT:-/c/Users/swata/.config/microservice-orders/tls/order-client-cert.pem}"
ORDER_CLIENT_KEY="${ORDER_CLIENT_KEY:-/c/Users/swata/.config/microservice-orders/tls/order-client-key.pem}"

TMP_DIR="$(mktemp -d)"
ADMIN_COOKIE="${TMP_DIR}/admin-cookie.txt"
USER_COOKIE="${TMP_DIR}/user-cookie.txt"
RESPONSE="${TMP_DIR}/response.json"

PRODUCT_ID=""
ADMIN_JWT=""
USER_JWT=""
ORDER_ID=""

TOTAL=0
PASSED=0
FAILED=0

cleanup() {
    if [[ -n "$PRODUCT_ID" && -n "$ADMIN_JWT" ]]; then
        curl -k -sS -o /dev/null \
            -X DELETE \
            "${PRODUCT_SERVICE}/api/v1/products/${PRODUCT_ID}" \
            -H "Authorization: Bearer ${ADMIN_JWT}" || true
    fi
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

pass() {
    echo "[PASS] $1"
    ((PASSED++))
    ((TOTAL++))
}

fail() {
    echo "[FAIL] $1"
    ((FAILED++))
    ((TOTAL++))
}

json_value() {
    local file="$1"
    local key="$2"

    if command -v jq >/dev/null 2>&1; then
        jq -r --arg key "$key" '.[$key] // empty' "$file"
        return
    fi

    python - "$file" "$key" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    value = json.load(f).get(sys.argv[2])

if value is not None:
    print(value)
PY
}

login() {
    local email="$1"
    local password="$2"
    local cookie="$3"

    rm -f "$cookie"

    local csrf_status
    csrf_status="$(curl -k -sS -c "$cookie" -b "$cookie" \
        -o /dev/null -w '%{http_code}' \
        "${USER_SERVICE}/api/v1/auth/csrf")"

    if [[ "$csrf_status" != "200" ]]; then
        echo "CSRF bootstrap failed: HTTP $csrf_status"
        return 1
    fi

    local csrf_token
    csrf_token="$(awk '$6 == "XSRF-TOKEN" { print $7; exit }' "$cookie")"

    if [[ -z "$csrf_token" ]]; then
        echo "XSRF-TOKEN cookie was not returned"
        return 1
    fi

    local login_status
    login_status="$(curl -k -sS \
        --cookie "$cookie" \
        --cookie-jar "$cookie" \
        -H 'Content-Type: application/json' \
        -H "X-XSRF-TOKEN: ${csrf_token}" \
        -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
        -o "$RESPONSE" \
        -w '%{http_code}' \
        "${USER_SERVICE}/api/v1/auth/login")"

    if [[ "$login_status" != "200" ]]; then
        echo "Login failed for ${email}: HTTP $login_status"
        cat "$RESPONSE"
        return 1
    fi

    return 0
}

request_status() {
    curl -k -sS "$@" -o "$RESPONSE" -w '%{http_code}'
}

# ============================================================
# PRE-FLIGHT
# ============================================================

echo "================================================"
echo " Order Service End-to-End Validation"
echo "================================================"
echo

if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required."
    exit 1
fi

if ! command -v jq >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1; then
    echo "jq or Python is required for JSON parsing."
    exit 1
fi

if [[ ! -f "$ORDER_CLIENT_CERT" || ! -f "$ORDER_CLIENT_KEY" ]]; then
    echo "Order client certificate/key not found."
    echo "CERT: $ORDER_CLIENT_CERT"
    echo "KEY : $ORDER_CLIENT_KEY"
    exit 1
fi

# ============================================================
# 1. HEALTH
# ============================================================

status="$(request_status "${ORDER_SERVICE}/actuator/health")"
if [[ "$status" == "200" ]]; then pass "Order Service health"; else fail "Order Service health (HTTP $status)"; fi

# ============================================================
# 2. AUTHORIZATION BOUNDARY
# ============================================================

status="$(request_status -X GET "${ORDER_SERVICE}/api/v1/orders")"
if [[ "$status" == "401" || "$status" == "403" ]]; then
    pass "Order endpoints reject unauthenticated requests"
else
    fail "Order endpoints unexpectedly accepted unauthenticated request (HTTP $status)"
    cat "$RESPONSE"
fi

# ============================================================
# 3. LOGIN
# ============================================================

if login "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "$ADMIN_COOKIE"; then
    ADMIN_JWT="$(awk '$6 == "__Host-access_token" { print $7; exit }' "$ADMIN_COOKIE")"
    [[ -n "$ADMIN_JWT" ]] && pass "Admin authentication" || { fail "Admin access token extraction"; exit 1; }
else
    fail "Admin authentication"
    exit 1
fi

if login "$USER_EMAIL" "$USER_PASSWORD" "$USER_COOKIE"; then
    USER_JWT="$(awk '$6 == "__Host-access_token" { print $7; exit }' "$USER_COOKIE")"
    [[ -n "$USER_JWT" ]] && pass "User authentication" || { fail "User access token extraction"; exit 1; }
else
    fail "User authentication"
    exit 1
fi

# ============================================================
# 4. CREATE TEMPORARY PRODUCT
# ============================================================

PRODUCT_BODY='{
  "name": "AUTOTEST - Order Service Product",
  "description": "Temporary product for Order Service E2E validation.",
  "price": 1999.00,
  "currency": "INR",
  "stockQuantity": 20
}'

status="$(request_status \
    -X POST \
    "${PRODUCT_SERVICE}/api/v1/products" \
    -H "Authorization: Bearer ${ADMIN_JWT}" \
    -H 'Content-Type: application/json' \
    -d "$PRODUCT_BODY")"

if [[ "$status" == "200" || "$status" == "201" ]]; then
    PRODUCT_ID="$(json_value "$RESPONSE" productId)"
    if [[ -n "$PRODUCT_ID" ]]; then
        pass "Temporary product creation"
    else
        fail "Temporary product creation returned no productId"
        cat "$RESPONSE"
        exit 1
    fi
else
    fail "Temporary product creation (HTTP $status)"
    cat "$RESPONSE"
    exit 1
fi

echo "Product ID: $PRODUCT_ID"

# ============================================================
# 5. CREATE ONLINE ORDER
# ============================================================

ORDER_BODY="{
  \"items\": [
    {
      \"productId\": \"${PRODUCT_ID}\",
      \"quantity\": 1
    }
  ],
  \"paymentMethod\": \"CARD\"
}"

IDEMPOTENCY_KEY="order-e2e-$(date +%s%N)"

status="$(request_status \
    -X POST \
    "${ORDER_SERVICE}/api/v1/orders" \
    -H "Authorization: Bearer ${USER_JWT}" \
    -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
    -H 'Content-Type: application/json' \
    -d "$ORDER_BODY")"

if [[ "$status" == "201" ]]; then
    ORDER_ID="$(json_value "$RESPONSE" orderId)"
    ORDER_STATUS="$(json_value "$RESPONSE" status)"
    PAYMENT_STATUS="$(json_value "$RESPONSE" paymentStatus)"

    if [[ -n "$ORDER_ID" && "$ORDER_STATUS" == "CONFIRMED" && "$PAYMENT_STATUS" == "CAPTURED" ]]; then
        pass "Order -> Inventory -> Payment successful checkout"
    else
        fail "Successful checkout returned unexpected state"
        cat "$RESPONSE"
        exit 1
    fi
else
    fail "Order creation (HTTP $status)"
    cat "$RESPONSE"
    exit 1
fi

echo "Order ID: $ORDER_ID"

# ============================================================
# 6. IDEMPOTENCY REPLAY
# ============================================================

status="$(request_status \
    -X POST \
    "${ORDER_SERVICE}/api/v1/orders" \
    -H "Authorization: Bearer ${USER_JWT}" \
    -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
    -H 'Content-Type: application/json' \
    -d "$ORDER_BODY")"

REPLAY_ORDER_ID="$(json_value "$RESPONSE" orderId)"

if [[ "$status" == "201" && "$REPLAY_ORDER_ID" == "$ORDER_ID" ]]; then
    pass "Duplicate order request replays the original order"
else
    fail "Idempotency replay (HTTP $status, orderId=$REPLAY_ORDER_ID)"
    cat "$RESPONSE"
fi

# ============================================================
# 7. IDEMPOTENCY KEY REUSE WITH DIFFERENT REQUEST
# ============================================================

CONFLICT_BODY="{
  \"items\": [
    {
      \"productId\": \"${PRODUCT_ID}\",
      \"quantity\": 2
    }
  ],
  \"paymentMethod\": \"CARD\"
}"

status="$(request_status \
    -X POST \
    "${ORDER_SERVICE}/api/v1/orders" \
    -H "Authorization: Bearer ${USER_JWT}" \
    -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
    -H 'Content-Type: application/json' \
    -d "$CONFLICT_BODY")"

if [[ "$status" == "409" ]]; then
    pass "Idempotency key reuse with different request is rejected"
else
    fail "Idempotency conflict expected HTTP 409, got $status"
    cat "$RESPONSE"
fi

# ============================================================
# 8. GET ORDER
# ============================================================

status="$(request_status \
    -X GET \
    "${ORDER_SERVICE}/api/v1/orders/${ORDER_ID}" \
    -H "Authorization: Bearer ${USER_JWT}")"

if [[ "$status" == "200" && "$(json_value "$RESPONSE" orderId)" == "$ORDER_ID" ]]; then
    pass "Get created order"
else
    fail "Get created order (HTTP $status)"
    cat "$RESPONSE"
fi

# ============================================================
# 9. ORDER HISTORY
# ============================================================

status="$(request_status \
    -X GET \
    "${ORDER_SERVICE}/api/v1/orders?page=0&size=20" \
    -H "Authorization: Bearer ${USER_JWT}")"

if [[ "$status" == "200" ]]; then
    pass "Order history"
else
    fail "Order history (HTTP $status)"
    cat "$RESPONSE"
fi

# ============================================================
# 10. CONFIRMED ORDER CANNOT BE CANCELLED
# ============================================================

status="$(request_status \
    -X POST \
    "${ORDER_SERVICE}/api/v1/orders/${ORDER_ID}/cancel" \
    -H "Authorization: Bearer ${USER_JWT}" \
    -H 'Content-Type: application/json' \
    -d '{"reason":"E2E cancellation boundary test"}')"

if [[ "$status" == "409" ]]; then
    pass "Confirmed order cancellation is rejected by lifecycle rules"
else
    fail "Confirmed order cancellation expected HTTP 409, got $status"
    cat "$RESPONSE"
fi

# ============================================================
# SUMMARY
# ============================================================

echo
echo "================================================"
echo " Order Service E2E Summary"
echo "================================================"
echo "Total tests : $TOTAL"
echo "Passed      : $PASSED"
echo "Failed      : $FAILED"
echo

if [[ "$FAILED" -eq 0 ]]; then
    echo "ALL TESTS PASSED"
    exit 0
fi

echo "SOME TESTS FAILED"
exit 1
