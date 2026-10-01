#!/usr/bin/env bash

set -u
set -o pipefail

# ============================================================
# FULL SYSTEM API GATEWAY E2E REGRESSION
# ============================================================
#
# External path under test:
#
#   Client
#      |
#      v
#   API Gateway :8442
#      |
#      +----> User Service :8443
#      |
#      +----> Product / Inventory :8444
#      |
#      +----> Order Service :8445
#                    |
#                    +---- mTLS ----> Product / Inventory
#                    |
#                    +---- mTLS ----> Payment :8446
#
# Business requests intentionally go ONLY through the Gateway.
#
# Requirements:
#   - curl
#   - python
#
# Optional environment:
#   API_GATEWAY_URL
#   ADMIN_EMAIL
#   ADMIN_PASSWORD
#   USER_EMAIL
#   USER_PASSWORD
#
# ============================================================


API_GATEWAY_URL="${API_GATEWAY_URL:-https://localhost:8442}"

ADMIN_EMAIL="${ADMIN_EMAIL:-java@jvm.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Java25@jvm}"

USER_EMAIL="${USER_EMAIL:-duke@example.com}"
USER_PASSWORD="${USER_PASSWORD:-Duke@12345}"


# ============================================================
# TEMPORARY TEST STATE
# ============================================================

TMP_DIR="$(mktemp -d)"

ADMIN_COOKIE="${TMP_DIR}/admin-cookie.txt"
USER_COOKIE="${TMP_DIR}/user-cookie.txt"

RESPONSE="${TMP_DIR}/response.json"

PRODUCT_RESPONSE="${TMP_DIR}/product.json"
ORDER_RESPONSE="${TMP_DIR}/order.json"
PAYMENT_RESPONSE="${TMP_DIR}/payment.json"


PRODUCT_ID=""
ORDER_ID=""
PAYMENT_ID=""

ADMIN_JWT=""
USER_JWT=""

TOTAL=0
PASSED=0
FAILED=0


# ============================================================
# OUTPUT HELPERS
# ============================================================

pass() {
    echo "  [PASS] $1"
    PASSED=$((PASSED + 1))
    TOTAL=$((TOTAL + 1))
}


fail() {
    echo "  [FAIL] $1"
    FAILED=$((FAILED + 1))
    TOTAL=$((TOTAL + 1))
}


section() {
    echo
    echo "============================================================"
    echo "$1"
    echo "============================================================"
}


# ============================================================
# HTTP HELPERS
# ============================================================

request_status() {

    curl \
        -k \
        -sS \
        "$@" \
        -o "$RESPONSE" \
        -w '%{http_code}'
}


# ============================================================
# JSON HELPERS
# ============================================================

json_value() {

    local file="$1"
    local field="$2"

    python - "$file" "$field" <<'PY'
import json
import sys

file_name = sys.argv[1]
field = sys.argv[2]

try:
    with open(file_name, "r", encoding="utf-8") as f:
        data = json.load(f)
except Exception:
    sys.exit(1)

value = data

for part in field.split("."):

    if isinstance(value, dict):
        value = value.get(part)

    else:
        value = None
        break

if value is None:
    sys.exit(1)

print(value)
PY
}


# ============================================================
# COOKIE HELPERS
# ============================================================

cookie_value() {

    local jar="$1"
    local name="$2"

    awk -v n="$name" '$6 == n {print $7}' "$jar" | tail -n 1
}


csrf() {

    local jar="$1"

    curl \
        -k \
        -sS \
        -b "$jar" \
        -c "$jar" \
        "${API_GATEWAY_URL}/api/v1/auth/csrf" \
        -o /dev/null

    cookie_value "$jar" "XSRF-TOKEN"
}


login() {

    local jar="$1"
    local email="$2"
    local password="$3"

    rm -f "$jar"

    local token
    token="$(csrf "$jar")"

    if [[ -z "$token" ]]; then
        return 1
    fi

    local status

    status="$(
        curl \
            -k \
            -sS \
            -b "$jar" \
            -c "$jar" \
            -X POST \
            "${API_GATEWAY_URL}/api/v1/auth/login" \
            -H 'Content-Type: application/json' \
            -H "X-XSRF-TOKEN: ${token}" \
            -d "{
                \"email\": \"${email}\",
                \"password\": \"${password}\"
            }" \
            -o "$RESPONSE" \
            -w '%{http_code}'
    )"

    [[ "$status" == "200" ]]
}


extract_access_token() {

    local jar="$1"

    cookie_value "$jar" "__Host-access_token"
}


# ============================================================
# CLEANUP
# ============================================================
#
# If a temporary product was created, try to delete it through
# the same public Gateway path used by the test.
#
# This is best-effort cleanup and must never hide the original
# test result.
# ============================================================

cleanup() {

    if [[ -n "$PRODUCT_ID" && -n "$ADMIN_JWT" ]]; then

        curl \
            -k \
            -sS \
            -X DELETE \
            "${API_GATEWAY_URL}/api/v1/products/${PRODUCT_ID}" \
            -H "Authorization: Bearer ${ADMIN_JWT}" \
            -o /dev/null \
            -w '' \
            >/dev/null 2>&1 || true

    fi

    rm -rf "$TMP_DIR"
}

trap cleanup EXIT


# ============================================================
# PRE-FLIGHT
# ============================================================

echo
echo "============================================================"
echo " Full System API Gateway E2E Regression"
echo "============================================================"
echo
echo "Gateway: ${API_GATEWAY_URL}"
echo

if ! command -v curl >/dev/null 2>&1; then
    echo "ERROR: curl is required."
    exit 1
fi

if ! command -v python >/dev/null 2>&1; then
    echo "ERROR: python is required."
    exit 1
fi


# ============================================================
# 1. GATEWAY AVAILABILITY
# ============================================================

section "1. API Gateway availability"

STATUS="$(
    curl \
        -k \
        -sS \
        -o /dev/null \
        -w '%{http_code}' \
        "${API_GATEWAY_URL}/api/v1/auth/csrf"
)"

if [[ "$STATUS" == "200" ]]; then
    pass "API Gateway reaches User Service CSRF endpoint"
else
    fail "API Gateway CSRF endpoint - HTTP $STATUS"
    exit 1
fi


# ============================================================
# 2. PUBLIC PRODUCT CATALOGUE
# ============================================================

section "2. Public catalogue through API Gateway"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/products"
)"

if [[ "$STATUS" == "200" ]]; then
    pass "Public product catalogue is reachable through Gateway"
else
    fail "Public product catalogue through Gateway - HTTP $STATUS"
    cat "$RESPONSE"
    exit 1
fi


# ============================================================
# 3. ADMIN LOGIN
# ============================================================

section "3. Admin authentication through API Gateway"

if login \
    "$ADMIN_COOKIE" \
    "$ADMIN_EMAIL" \
    "$ADMIN_PASSWORD"; then

    ADMIN_JWT="$(extract_access_token "$ADMIN_COOKIE")"

    if [[ -n "$ADMIN_JWT" ]]; then
        pass "Admin login through Gateway"
        pass "Gateway-issued admin access cookie received"
    else
        fail "Gateway-issued admin access cookie received"
        exit 1
    fi

else

    fail "Admin login through Gateway"
    cat "$RESPONSE"
    exit 1

fi


# ============================================================
# 4. USER LOGIN
# ============================================================

section "4. User authentication through API Gateway"

if login \
    "$USER_COOKIE" \
    "$USER_EMAIL" \
    "$USER_PASSWORD"; then

    USER_JWT="$(extract_access_token "$USER_COOKIE")"

    if [[ -n "$USER_JWT" ]]; then
        pass "User login through Gateway"
        pass "Gateway-issued user access cookie received"
    else
        fail "Gateway-issued user access cookie received"
        exit 1
    fi

else

    fail "User login through Gateway"
    cat "$RESPONSE"
    exit 1

fi


# ============================================================
# 5. USER PROFILE THROUGH GATEWAY
# ============================================================

section "5. User profile through API Gateway"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/users/me" \
        -b "$USER_COOKIE"
)"

if [[ "$STATUS" == "200" ]]; then
    pass "Authenticated /users/me through Gateway"
else
    fail "/users/me through Gateway - HTTP $STATUS"
    cat "$RESPONSE"
fi


# ============================================================
# 6. COOKIE -> BEARER RELAY
# ============================================================
#
# This is specifically a Gateway test.
#
# /orders is not public.
#
# The request contains ONLY the browser cookie.
#
# Gateway security authenticates the cookie and the global filter
# must relay the access token downstream as:
#
#     Authorization: Bearer <JWT>
#
# Order Service therefore proves the relay path works.
# ============================================================

section "6. Gateway access-token cookie relay"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/orders?page=0&size=20" \
        -b "$USER_COOKIE"
)"

if [[ "$STATUS" == "200" ]]; then
    pass "Gateway converts access-token cookie into downstream bearer authentication"
else
    fail "Cookie-to-bearer relay expected HTTP 200, got $STATUS"
    cat "$RESPONSE"
fi


# ============================================================
# 7. ORDER AUTHORIZATION BOUNDARY
# ============================================================

section "7. Unauthenticated order request"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/orders"
)"

if [[ "$STATUS" == "401" || "$STATUS" == "403" ]]; then
    pass "Unauthenticated order request is rejected"
else
    fail "Unauthenticated order request expected 401/403, got $STATUS"
    cat "$RESPONSE"
fi


# ============================================================
# 8. CREATE TEMPORARY PRODUCT
# ============================================================

section "8. Product creation through API Gateway"

PRODUCT_BODY='{
    "name": "FULL-E2E-REGRESSION-PRODUCT",
    "description": "Temporary product created by full-system Gateway regression.",
    "price": 1999.00,
    "currency": "INR",
    "stockQuantity": 20
}'


STATUS="$(
    request_status \
        -X POST \
        "${API_GATEWAY_URL}/api/v1/products" \
        -H "Authorization: Bearer ${ADMIN_JWT}" \
        -H 'Content-Type: application/json' \
        -d "$PRODUCT_BODY"
)"

if [[ "$STATUS" == "200" || "$STATUS" == "201" ]]; then

    PRODUCT_ID="$(
        json_value "$RESPONSE" productId 2>/dev/null || true
    )"

    if [[ -n "$PRODUCT_ID" ]]; then

        cp "$RESPONSE" "$PRODUCT_RESPONSE"

        pass "Temporary product creation through Gateway"

        echo "  Product ID: $PRODUCT_ID"

    else

        fail "Product creation returned no productId"
        cat "$RESPONSE"
        exit 1

    fi

else

    fail "Product creation through Gateway - HTTP $STATUS"
    cat "$RESPONSE"
    exit 1

fi


# ============================================================
# 9. NORMAL USER CANNOT CREATE PRODUCT
# ============================================================

section "9. Product authorization boundary"

STATUS="$(
    request_status \
        -X POST \
        "${API_GATEWAY_URL}/api/v1/products" \
        -H "Authorization: Bearer ${USER_JWT}" \
        -H 'Content-Type: application/json' \
        -d "$PRODUCT_BODY"
)"

if [[ "$STATUS" == "403" ]]; then
    pass "Normal user cannot create products"
else
    fail "Normal user product creation expected 403, got $STATUS"
    cat "$RESPONSE"
fi


# ============================================================
# 10. READ PRODUCT
# ============================================================

section "10. Product lookup through API Gateway"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/products/${PRODUCT_ID}"
)"

if [[ "$STATUS" == "200" ]]; then
    pass "Created product is readable through Gateway"
else
    fail "Product lookup through Gateway - HTTP $STATUS"
    cat "$RESPONSE"
    exit 1
fi


INITIAL_STOCK="$(
    json_value "$RESPONSE" stockQuantity 2>/dev/null || true
)"

if [[ "$INITIAL_STOCK" == "20" ]]; then
    pass "Initial product stock is 20"
else
    fail "Initial product stock expected 20, got ${INITIAL_STOCK:-<missing>}"
fi



# ============================================================
# 11. FULL CHECKOUT
# ============================================================

section "11. Full Order -> Inventory -> Payment checkout"

ORDER_BODY="{
    \"items\": [
        {
            \"productId\": \"${PRODUCT_ID}\",
            \"quantity\": 1
        }
    ],
    \"paymentMethod\": \"CARD\"
}"


IDEMPOTENCY_KEY="full-system-e2e-$(date +%s%N)"


STATUS="$(
    request_status \
        -X POST \
        "${API_GATEWAY_URL}/api/v1/orders" \
        -H "Authorization: Bearer ${USER_JWT}" \
        -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
        -H 'Content-Type: application/json' \
        -d "$ORDER_BODY"
)"


if [[ "$STATUS" == "201" ]]; then

    cp "$RESPONSE" "$ORDER_RESPONSE"

    ORDER_ID="$(
        json_value "$RESPONSE" orderId 2>/dev/null || true
    )"

    ORDER_STATUS="$(
        json_value "$RESPONSE" status 2>/dev/null || true
    )"

    PAYMENT_STATUS="$(
        json_value "$RESPONSE" paymentStatus 2>/dev/null || true
    )"

    PAYMENT_ID="$(
        json_value "$RESPONSE" paymentId 2>/dev/null || true
    )"


    if [[ "$ORDER_STATUS" == "CONFIRMED" ]]; then
        pass "Order reaches CONFIRMED state"
    else
        fail "Order expected CONFIRMED, got ${ORDER_STATUS:-<missing>}"
        cat "$RESPONSE"
        exit 1
    fi


    if [[ "$PAYMENT_STATUS" == "CAPTURED" ]]; then
        pass "Payment reaches CAPTURED state"
    else
        fail "Payment expected CAPTURED, got ${PAYMENT_STATUS:-<missing>}"
        cat "$RESPONSE"
        exit 1
    fi


    if [[ -n "$ORDER_ID" ]]; then
        pass "Order ID returned"
        echo "  Order ID: $ORDER_ID"
    else
        fail "Order ID returned"
        exit 1
    fi


    if [[ -n "$PAYMENT_ID" ]]; then
        pass "Payment ID returned"
        echo "  Payment ID: $PAYMENT_ID"
    else
        fail "Payment ID returned"
        exit 1
    fi

else

    fail "Full checkout through Gateway - HTTP $STATUS"
    cat "$RESPONSE"
    exit 1

fi


# ============================================================
# 12. INVENTORY VERIFICATION
# ============================================================

section "12. Inventory state after successful checkout"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/products/${PRODUCT_ID}"
)"

if [[ "$STATUS" == "200" ]]; then

    FINAL_STOCK="$(
        json_value "$RESPONSE" stockQuantity 2>/dev/null || true
    )"

    if [[ "$FINAL_STOCK" == "19" ]]; then
        pass "Inventory stock reduced from 20 to 19"
    else
        fail "Inventory stock expected 19, got ${FINAL_STOCK:-<missing>}"
        cat "$RESPONSE"
    fi

else

    fail "Product lookup after checkout - HTTP $STATUS"
    cat "$RESPONSE"

fi


# ============================================================
# 13. PAYMENT LOOKUP
# ============================================================

section "13. Payment lookup through API Gateway"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/payments/${PAYMENT_ID}" \
        -H "Authorization: Bearer ${USER_JWT}"
)"

if [[ "$STATUS" == "200" ]]; then

    cp "$RESPONSE" "$PAYMENT_RESPONSE"

    LOOKUP_PAYMENT_ID="$(
        json_value "$RESPONSE" paymentId 2>/dev/null || true
    )"

    LOOKUP_PAYMENT_STATUS="$(
        json_value "$RESPONSE" status 2>/dev/null || true
    )"


    if [[ "$LOOKUP_PAYMENT_ID" == "$PAYMENT_ID" ]]; then
        pass "Customer can retrieve own payment through Gateway"
    else
        fail "Payment lookup returned wrong payment ID"
    fi


    if [[ "$LOOKUP_PAYMENT_STATUS" == "CAPTURED" ]]; then
        pass "Payment lookup confirms CAPTURED"
    else
        fail "Payment lookup expected CAPTURED, got ${LOOKUP_PAYMENT_STATUS:-<missing>}"
    fi

else

    fail "Payment lookup through Gateway - HTTP $STATUS"
    cat "$RESPONSE"

fi


# ============================================================
# 14. ORDER LOOKUP
# ============================================================

section "14. Order lookup through API Gateway"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/orders/${ORDER_ID}" \
        -H "Authorization: Bearer ${USER_JWT}"
)"

if [[ "$STATUS" == "200" ]]; then

    LOOKUP_ORDER_ID="$(
        json_value "$RESPONSE" orderId 2>/dev/null || true
    )"

    LOOKUP_ORDER_STATUS="$(
        json_value "$RESPONSE" status 2>/dev/null || true
    )"

    LOOKUP_PAYMENT_STATUS="$(
        json_value "$RESPONSE" paymentStatus 2>/dev/null || true
    )"

    LOOKUP_PAYMENT_ID="$(
        json_value "$RESPONSE" paymentId 2>/dev/null || true
    )"

    if [[ "$LOOKUP_ORDER_ID" == "$ORDER_ID" ]]; then
        pass "Customer can retrieve own order"
    else
        fail "Order lookup returned wrong order ID"
    fi

    if [[ "$LOOKUP_ORDER_STATUS" == "CONFIRMED" ]]; then
        pass "Order lookup confirms CONFIRMED"
    else
        fail "Order lookup expected CONFIRMED, got ${LOOKUP_ORDER_STATUS:-<missing>}"
    fi

    if [[ "$LOOKUP_PAYMENT_STATUS" == "CAPTURED" ]]; then
        pass "Order lookup confirms CAPTURED payment"
    else
        fail "Order lookup expected CAPTURED payment, got ${LOOKUP_PAYMENT_STATUS:-<missing>}"
    fi

    if [[ "$LOOKUP_PAYMENT_ID" == "$PAYMENT_ID" ]]; then
        pass "Order lookup returns correct payment ID"
    else
        fail "Order lookup returned wrong payment ID"
    fi

else

    fail "Order lookup through Gateway - HTTP $STATUS"
    cat "$RESPONSE"

fi

# ============================================================
# 15. ORDER HISTORY
# ============================================================

section "15. Order history through API Gateway"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/orders?page=0&size=20" \
        -H "Authorization: Bearer ${USER_JWT}"
)"

if [[ "$STATUS" == "200" ]]; then
    pass "Order history through Gateway"
else
    fail "Order history through Gateway - HTTP $STATUS"
    cat "$RESPONSE"
fi


# ============================================================
# 16. IDEMPOTENCY REPLAY
# ============================================================

section "16. Checkout idempotency replay"

STATUS="$(
    request_status \
        -X POST \
        "${API_GATEWAY_URL}/api/v1/orders" \
        -H "Authorization: Bearer ${USER_JWT}" \
        -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
        -H 'Content-Type: application/json' \
        -d "$ORDER_BODY"
)"

REPLAY_ORDER_ID="$(
    json_value "$RESPONSE" orderId 2>/dev/null || true
)"

if [[ "$STATUS" == "201" && "$REPLAY_ORDER_ID" == "$ORDER_ID" ]]; then

    pass "Duplicate checkout replays original order"

else

    fail "Idempotency replay expected 201 + same order"

    echo "  HTTP: $STATUS"
    echo "  Original: $ORDER_ID"
    echo "  Replay  : ${REPLAY_ORDER_ID:-<missing>}"

    cat "$RESPONSE"

fi


# ============================================================
# 17. IDEMPOTENCY CONFLICT
# ============================================================

section "17. Idempotency conflict"

CONFLICT_BODY="{
    \"items\": [
        {
            \"productId\": \"${PRODUCT_ID}\",
            \"quantity\": 2
        }
    ],
    \"paymentMethod\": \"CARD\"
}"


STATUS="$(
    request_status \
        -X POST \
        "${API_GATEWAY_URL}/api/v1/orders" \
        -H "Authorization: Bearer ${USER_JWT}" \
        -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
        -H 'Content-Type: application/json' \
        -d "$CONFLICT_BODY"
)"


if [[ "$STATUS" == "409" ]]; then

    pass "Idempotency key reuse with different order is rejected"

else

    fail "Idempotency conflict expected 409, got $STATUS"
    cat "$RESPONSE"

fi


# ============================================================
# 18. INTERNAL PAYMENT API
# ============================================================

section "18. Payment internal API boundary"

FAKE_PAYMENT_ID="$(
    python - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"


STATUS="$(
    request_status \
        -X POST \
        "${API_GATEWAY_URL}/api/v1/payments/internal/${FAKE_PAYMENT_ID}/capture" \
        -H "Authorization: Bearer ${USER_JWT}" \
        -H "Idempotency-Key: gateway-internal-test-$(date +%s%N)"
)"


#
# Because the Gateway explicitly denies this route, the expected
# Gateway-level result is 403.
#
if [[ "$STATUS" == "403" ]]; then

    pass "Internal Payment API is denied by Gateway"

else

    fail "Internal Payment API expected Gateway HTTP 403, got $STATUS"
    cat "$RESPONSE"

fi


# ============================================================
# 19. INTERNAL INVENTORY API
# ============================================================

section "19. Inventory internal API boundary"

FAKE_RESERVATION_ID="$(
    python - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"


STATUS="$(
    request_status \
        -X POST \
        "${API_GATEWAY_URL}/api/v1/inventory/reservations/${FAKE_RESERVATION_ID}/commit" \
        -H "Authorization: Bearer ${USER_JWT}"
)"


if [[ "$STATUS" == "403" ]]; then

    pass "Internal Inventory API is denied by Gateway"

else

    fail "Internal Inventory API expected Gateway HTTP 403, got $STATUS"
    cat "$RESPONSE"

fi


# ============================================================
# 20. CONFIRMED ORDER CANNOT BE CANCELLED
# ============================================================

section "20. Order lifecycle boundary"

STATUS="$(
    request_status \
        -X POST \
        "${API_GATEWAY_URL}/api/v1/orders/${ORDER_ID}/cancel" \
        -H "Authorization: Bearer ${USER_JWT}" \
        -H 'Content-Type: application/json' \
        -d '{"reason":"Full-system lifecycle regression"}'
)"


if [[ "$STATUS" == "409" ]]; then

    pass "Confirmed order cancellation is rejected"

else

    fail "Confirmed order cancellation expected 409, got $STATUS"
    cat "$RESPONSE"

fi

# ============================================================
# 21. DELETE TEMPORARY PRODUCT
# ============================================================

section "21. Cleanup through API Gateway"

DELETED_PRODUCT_ID="$PRODUCT_ID"

STATUS="$(
    request_status \
        -X DELETE \
        "${API_GATEWAY_URL}/api/v1/products/${DELETED_PRODUCT_ID}" \
        -H "Authorization: Bearer ${ADMIN_JWT}"
)"

if [[ "$STATUS" == "204" ]]; then

    pass "Temporary product deleted through Gateway"

    PRODUCT_ID=""

else

    fail "Temporary product deletion expected 204, got $STATUS"
    cat "$RESPONSE"

fi

# ============================================================
# 22. VERIFY DELETION
# ============================================================

section "22. Verify temporary product deletion"

STATUS="$(
    request_status \
        -X GET \
        "${API_GATEWAY_URL}/api/v1/products/${DELETED_PRODUCT_ID}"
)"

if [[ "$STATUS" == "404" ]]; then

    pass "Deleted product is no longer accessible"

else

    fail "Deleted product expected 404, got $STATUS"
    cat "$RESPONSE"

fi

# ============================================================
# FINAL SUMMARY
# ============================================================

echo
echo "============================================================"
echo " FULL SYSTEM E2E SUMMARY"
echo "============================================================"
echo

echo "Total tests : $TOTAL"
echo "Passed      : $PASSED"
echo "Failed      : $FAILED"

echo

if [[ "$FAILED" -eq 0 ]]; then

    echo "ALL FULL-SYSTEM E2E TESTS PASSED"
    exit 0

fi


echo "FULL-SYSTEM E2E TESTS FAILED"
exit 1
