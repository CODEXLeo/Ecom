#!/usr/bin/env bash

# ============================================================
# Microservice Product Service - Automated E2E Test
# ============================================================
#
# Services:
#   User Service    https://localhost:8443
#   Product Service https://localhost:8444
#
# What this script does:
#
#   1. Login ADMIN through User Service
#   2. Automatically obtain CSRF token
#   3. Automatically capture HttpOnly access-token cookie
#   4. Login USER through User Service
#   5. Automatically capture HttpOnly access-token cookie
#   6. Test public GET
#   7. Test protected endpoints without JWT
#   8. Test protected endpoints with ROLE_USER
#   9. Create temporary product with ROLE_ADMIN
#  10. Test product GET
#  11. Test product PATCH
#  12. Test stock PATCH
#  13. Test status PATCH
#  14. Test DELETE
#  15. Clean up temporary product
#
# Usage:
#
#   ./test-product-service.sh
#
# Optional environment variables:
#
#   ADMIN_EMAIL
#   ADMIN_PASSWORD
#   USER_EMAIL
#   USER_PASSWORD
#
# Example:
#
#   export ADMIN_EMAIL='java@jvm.com'
#   export ADMIN_PASSWORD='Java25@jvm'
#   export USER_EMAIL='user@jvm.com'
#   export USER_PASSWORD='UserPassword'
#
#   ./test-product-service.sh
#
# ============================================================

set -u
set -o pipefail

# ------------------------------------------------------------
# Configuration
# ------------------------------------------------------------

USER_SERVICE="https://localhost:8443"
PRODUCT_SERVICE="https://localhost:8444"

AUTH_BASE="${USER_SERVICE}/api/v1/auth"
PRODUCTS_URL="${PRODUCT_SERVICE}/api/v1/products"
INVENTORY_URL="${PRODUCT_SERVICE}/api/v1/inventory"

ORDER_CLIENT_CERT="${ORDER_CLIENT_CERT:-/c/Users/swata/.config/microservice-orders/tls/order-client-cert.pem}"
ORDER_CLIENT_KEY="${ORDER_CLIENT_KEY:-/c/Users/swata/.config/microservice-orders/tls/order-client-key.pem}"

# ------------------------------------------------------------
# Test credentials
# ------------------------------------------------------------

ADMIN_EMAIL="${ADMIN_EMAIL:-java@jvm.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"

USER_EMAIL="${USER_EMAIL:-}"
USER_PASSWORD="${USER_PASSWORD:-}"

# ------------------------------------------------------------
# Temporary working directory
# ------------------------------------------------------------

TMP_DIR="$(mktemp -d)"

ADMIN_COOKIES="${TMP_DIR}/admin-cookies.txt"
USER_COOKIES="${TMP_DIR}/user-cookies.txt"

ADMIN_HEADERS="${TMP_DIR}/admin-headers.txt"
USER_HEADERS="${TMP_DIR}/user-headers.txt"

ADMIN_RESPONSE="${TMP_DIR}/admin-response.json"
USER_RESPONSE="${TMP_DIR}/user-response.json"

PRODUCT_RESPONSE="${TMP_DIR}/product-response.json"
PRODUCT_HEADERS="${TMP_DIR}/product-headers.txt"
INVENTORY_RESPONSE="${TMP_DIR}/inventory-response.json"

# ------------------------------------------------------------
# Terminal colors
# ------------------------------------------------------------

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
RESET='\033[0m'

# ------------------------------------------------------------
# Test counters
# ------------------------------------------------------------

TOTAL=0
PASSED=0
FAILED=0

# ------------------------------------------------------------
# Product state
# ------------------------------------------------------------

PRODUCT_ID=""

# ------------------------------------------------------------
# JWTs
# ------------------------------------------------------------

ADMIN_JWT=""
USER_JWT=""

# ------------------------------------------------------------
# Helper functions
# ------------------------------------------------------------

header() {
    echo
    echo -e "${BLUE}${BOLD}============================================================${RESET}"
    echo -e "${BLUE}${BOLD}$1${RESET}"
    echo -e "${BLUE}${BOLD}============================================================${RESET}"
    echo
}

section() {
    echo
    echo -e "${MAGENTA}${BOLD}--- $1 ---${RESET}"
}

info() {
    echo -e "${YELLOW}$1${RESET}"
}

success() {
    echo -e "${GREEN}$1${RESET}"
}

error() {
    echo -e "${RED}$1${RESET}"
}

test_name() {
    echo
    echo -e "${CYAN}TEST:${RESET} $1"
}

# ------------------------------------------------------------
# Secure password prompt
# ------------------------------------------------------------

read_password_if_empty() {

    if [[ -z "$ADMIN_PASSWORD" ]]; then
        read -r -s -p "Admin password for ${ADMIN_EMAIL}: " ADMIN_PASSWORD
        echo
    fi

    if [[ -z "$USER_EMAIL" ]]; then
        read -r -p "Normal USER email: " USER_EMAIL
    fi

    if [[ -z "$USER_PASSWORD" ]]; then
        read -r -s -p "Normal USER password: " USER_PASSWORD
        echo
    fi
}

# ------------------------------------------------------------
# Extract cookie from Netscape cookie jar
#
# Format:
#
# domain TRUE path secure expiration name value
#
# ------------------------------------------------------------

get_cookie() {
    local cookie_file="$1"
    local cookie_name="$2"

    awk -v name="$cookie_name" '
        NF >= 7 && $6 == name {
            print $7
            exit
        }
    ' "$cookie_file"
}

# ------------------------------------------------------------
# Extract CSRF token
# ------------------------------------------------------------

get_csrf_token() {
    local cookie_file="$1"

    get_cookie "$cookie_file" "XSRF-TOKEN"
}

# ------------------------------------------------------------
# Extract access token
# ------------------------------------------------------------

get_access_token() {
    local cookie_file="$1"

    get_cookie "$cookie_file" "__Host-access_token"
}

# ------------------------------------------------------------
# Login
# ------------------------------------------------------------

login_user() {

    local email="$1"
    local password="$2"
    local cookie_file="$3"
    local header_file="$4"
    local response_file="$5"
    local label="$6"

    section "Login: ${label}"

    rm -f "$cookie_file" "$header_file" "$response_file"

    # --------------------------------------------------------
    # Step 1:
    # GET CSRF endpoint.
    #
    # Spring's CookieCsrfTokenRepository issues XSRF-TOKEN.
    # --------------------------------------------------------

    echo "Obtaining CSRF token..."

    local csrf_status

    csrf_status="$(
        curl -k -sS \
            -c "$cookie_file" \
            -b "$cookie_file" \
            -o /dev/null \
            -w "%{http_code}" \
            "${AUTH_BASE}/csrf"
    )"

    if [[ "$csrf_status" != "200" ]]; then
        error "CSRF endpoint returned HTTP ${csrf_status}"
        return 1
    fi

    local csrf_token

    csrf_token="$(get_csrf_token "$cookie_file")"

    if [[ -z "$csrf_token" ]]; then
        error "Could not extract XSRF-TOKEN from cookie jar."
        echo
        cat "$cookie_file"
        return 1
    fi

    success "CSRF token obtained."

    # --------------------------------------------------------
    # Step 2:
    # Login.
    # --------------------------------------------------------

    echo "Logging in as ${email}..."

    local login_body

    login_body="$(
        printf '{"email":"%s","password":"%s"}' \
            "$email" \
            "$password"
    )"

    local login_status

    login_status="$(
        curl -k -sS \
            -D "$header_file" \
            -c "$cookie_file" \
            -b "$cookie_file" \
            -o "$response_file" \
            -w "%{http_code}" \
            -X POST \
            "${AUTH_BASE}/login" \
            -H "Accept: application/json" \
            -H "Content-Type: application/json" \
            -H "X-XSRF-TOKEN: ${csrf_token}" \
            -d "$login_body"
    )"

    echo "Login HTTP status: ${login_status}"

    if [[ "$login_status" != "200" ]]; then
        error "${label} login failed."

        echo
        echo "Response:"
        cat "$response_file"
        echo

        return 1
    fi

    success "${label} login successful."

    # --------------------------------------------------------
    # Step 3:
    # Extract HttpOnly access-token.
    # --------------------------------------------------------

    local access_token

    access_token="$(get_access_token "$cookie_file")"

    if [[ -z "$access_token" ]]; then
        error "Login succeeded but __Host-access_token was not found."

        echo
        echo "Cookie jar:"
        cat "$cookie_file"
        echo

        return 1
    fi

    success "${label} access token captured."

    # Return token through global variable.
    if [[ "$label" == "ADMIN" ]]; then
        ADMIN_JWT="$access_token"
    else
        USER_JWT="$access_token"
    fi

    return 0
}

# ------------------------------------------------------------
# Execute test
# ------------------------------------------------------------

run_test() {

    local description="$1"
    local expected="$2"
    local method="$3"
    local url="$4"
    local token="${5:-}"
    local body="${6:-}"
    local idempotency_key="${7:-}"

    test_name "$description"

    local response_file="${TMP_DIR}/test-response"
    local header_file="${TMP_DIR}/test-headers"

    rm -f "$response_file" "$header_file"

    local curl_args=(
        -k
        -sS
        -D "$header_file"
        -o "$response_file"
        -w "%{http_code}"
        -X "$method"
        "$url"
        -H "Accept: application/json"
    )

    if [[ -n "$token" ]]; then
        curl_args+=(
            -H "Authorization: Bearer ${token}"
        )
    fi

    if [[ -n "$body" ]]; then
        curl_args+=(
            -H "Content-Type: application/json"
            -d "$body"
        )
    fi

    if [[ -n "$idempotency_key" ]]; then
        curl_args+=(
            -H "Idempotency-Key: ${idempotency_key}"
        )
    fi

    local actual

    actual="$(curl "${curl_args[@]}")"

    echo "Expected: HTTP ${expected}"
    echo "Actual:   HTTP ${actual}"

    if [[ -s "$response_file" ]]; then
        echo
        echo "Response:"
        cat "$response_file"
        echo
    fi

    if [[ "$actual" == "$expected" ]]; then
        success "PASS"
        ((PASSED++))
        ((TOTAL++))
        return 0
    fi

    error "FAIL"
    ((FAILED++))
    ((TOTAL++))

    return 1
}

# ------------------------------------------------------------
# Execute test accepting multiple successful statuses
# ------------------------------------------------------------

run_test_success() {

    local description="$1"
    local method="$2"
    local url="$3"
    local token="${4:-}"
    local body="${5:-}"

    test_name "$description"

    local response_file="${TMP_DIR}/success-response"
    local header_file="${TMP_DIR}/success-headers"

    rm -f "$response_file" "$header_file"

    local curl_args=(
        -k
        -sS
        -D "$header_file"
        -o "$response_file"
        -w "%{http_code}"
        -X "$method"
        "$url"
        -H "Accept: application/json"
    )

    if [[ -n "$token" ]]; then
        curl_args+=(
            -H "Authorization: Bearer ${token}"
        )
    fi

    if [[ -n "$body" ]]; then
        curl_args+=(
            -H "Content-Type: application/json"
            -d "$body"
        )
    fi

    local actual

    actual="$(curl "${curl_args[@]}")"

    echo "Actual: HTTP ${actual}"

    if [[ -s "$response_file" ]]; then
        echo
        echo "Response:"
        cat "$response_file"
        echo
    fi

    case "$actual" in
        200|201|202|204)
            success "PASS"
            ((PASSED++))
            ((TOTAL++))
            return 0
            ;;
        *)
            error "FAIL - expected successful 2xx response"
            ((FAILED++))
            ((TOTAL++))
            return 1
            ;;
    esac
}

# ------------------------------------------------------------
# Extract productId from JSON
# ------------------------------------------------------------

extract_product_id() {

    local file="$1"

    if command -v jq >/dev/null 2>&1; then
        jq -r '.productId // empty' "$file"
        return
    fi

    if command -v python >/dev/null 2>&1; then
        python - "$file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    data = json.load(f)

value = data.get("productId")

if value:
    print(value)
PY
        return
    fi

    error "Neither jq nor Python is available."
    return 1
}

# ------------------------------------------------------------
# Extract reservationId from JSON
# ------------------------------------------------------------

extract_reservation_id() {

    local file="$1"

    if command -v jq >/dev/null 2>&1; then
        jq -r '.reservationId // empty' "$file"
        return
    fi

    python - "$file" <<'PYJSON'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    data = json.load(f)

value = data.get("reservationId")

if value:
    print(value)
PYJSON
}

# ------------------------------------------------------------
# Inventory mTLS test helper
# ------------------------------------------------------------

run_inventory_test() {

    local description="$1"
    local expected="$2"
    local method="$3"
    local url="$4"
    local token="${5:-}"
    local body="${6:-}"
    local key="${7:-}"
    local response_file="${TMP_DIR}/inventory-response-${RANDOM}.json"

    test_name "$description"

    local curl_args=(
        -k
        -sS
        -o "$response_file"
        -w "%{http_code}"
        -X "$method"
        "$url"
        -H "Accept: application/json"
        --cert "$ORDER_CLIENT_CERT"
        --key "$ORDER_CLIENT_KEY"
    )

    if [[ -n "$token" ]]; then
        curl_args+=(
            -H "Authorization: Bearer ${token}"
        )
    fi

    if [[ -n "$key" ]]; then
        curl_args+=(
            -H "Idempotency-Key: ${key}"
        )
    fi

    if [[ -n "$body" ]]; then
        curl_args+=(
            -H "Content-Type: application/json"
            -d "$body"
        )
    fi

    local actual
    actual="$(curl "${curl_args[@]}")"

    echo "Expected: HTTP ${expected}"
    echo "Actual:   HTTP ${actual}"

    if [[ -s "$response_file" ]]; then
        echo
        echo "Response:"
        cat "$response_file"
        echo
    fi

    if [[ "$actual" == "$expected" ]]; then
        success "PASS"
        ((PASSED++))
        ((TOTAL++))
        return 0
    fi

    error "FAIL"
    ((FAILED++))
    ((TOTAL++))
    return 1
}

# ------------------------------------------------------------
# Cleanup Product
# ------------------------------------------------------------

cleanup_product() {

    if [[ -z "$PRODUCT_ID" ]]; then
        return
    fi

    echo
    echo -e "${YELLOW}${BOLD}Cleaning up temporary product:${RESET}"
    echo "$PRODUCT_ID"

    local url="${PRODUCTS_URL}/${PRODUCT_ID}"

    local status

    status="$(
        curl -k -sS \
            -o /dev/null \
            -w "%{http_code}" \
            -X DELETE \
            "$url" \
            -H "Authorization: Bearer ${ADMIN_JWT}" \
            -H "Accept: application/json"
    )"

    echo "Cleanup DELETE status: ${status}"

    case "$status" in
        200|204)
            success "Temporary product cleanup successful."
            ;;
        404)
            info "Temporary product was already removed/not found."
            ;;
        *)
            error "WARNING: temporary product cleanup returned HTTP ${status}"
            echo
            echo "Product ID: ${PRODUCT_ID}"
            ;;
    esac

    rm -rf "$TMP_DIR"
}

trap cleanup_product EXIT

# ============================================================
# START
# ============================================================

header "Microservice Product Service - Automated Test"

echo "User Service:"
echo "  ${USER_SERVICE}"

echo

echo "Product Service:"
echo "  ${PRODUCT_SERVICE}"

echo

echo "Admin account:"
echo "  ${ADMIN_EMAIL}"

echo

# ------------------------------------------------------------
# Check dependencies
# ------------------------------------------------------------

section "Checking Dependencies"

if ! command -v curl >/dev/null 2>&1; then
    error "curl is required."
    exit 1
fi

success "curl found."

if command -v jq >/dev/null 2>&1; then
    success "jq found."
elif command -v python >/dev/null 2>&1; then
    success "Python found. Using Python for JSON parsing."
else
    error "Either jq or Python is required."
    exit 1
fi

if [[ ! -f "$ORDER_CLIENT_CERT" || ! -f "$ORDER_CLIENT_KEY" ]]; then
    error "Order Service client certificate/key required for inventory mTLS tests."
    echo "CERT: $ORDER_CLIENT_CERT"
    echo "KEY : $ORDER_CLIENT_KEY"
    exit 1
fi

success "Order Service mTLS client certificate/key found."

# ------------------------------------------------------------
# Get credentials
# ------------------------------------------------------------

section "Credentials"

read_password_if_empty

# ------------------------------------------------------------
# Login ADMIN
# ------------------------------------------------------------

if ! login_user \
    "$ADMIN_EMAIL" \
    "$ADMIN_PASSWORD" \
    "$ADMIN_COOKIES" \
    "$ADMIN_HEADERS" \
    "$ADMIN_RESPONSE" \
    "ADMIN"; then

    error "Cannot continue without ADMIN JWT."
    exit 1
fi

# ------------------------------------------------------------
# Login USER
# ------------------------------------------------------------

if ! login_user \
    "$USER_EMAIL" \
    "$USER_PASSWORD" \
    "$USER_COOKIES" \
    "$USER_HEADERS" \
    "$USER_RESPONSE" \
    "USER"; then

    error "Cannot continue without USER JWT."
    exit 1
fi

# ------------------------------------------------------------
# Token information
# ------------------------------------------------------------

section "Authentication Tokens"

echo "ADMIN access token:"
echo "  Captured: YES"
echo "  Length: ${#ADMIN_JWT}"

echo

echo "USER access token:"
echo "  Captured: YES"
echo "  Length: ${#USER_JWT}"

# ------------------------------------------------------------
# Public endpoint
# ------------------------------------------------------------

header "1. Public GET Tests"

run_test \
    "GET /api/v1/products without JWT" \
    200 \
    GET \
    "$PRODUCTS_URL"

# ------------------------------------------------------------
# Product payload
# ------------------------------------------------------------

PRODUCT_BODY='{
  "name": "AUTOTEST - Keychron K8 QMK",
  "description": "Temporary product created by automated Product Service test.",
  "price": 15990.00,
  "currency": "INR",
  "stockQuantity": 100
}'

# ------------------------------------------------------------
# No JWT
# ------------------------------------------------------------

header "2. Authentication Tests"

run_test \
    "POST /api/v1/products without JWT → 401" \
    401 \
    POST \
    "$PRODUCTS_URL" \
    "" \
    "$PRODUCT_BODY"

run_test \
    "POST /api/v1/products with 3-decimal price → 401 without JWT" \
    401 \
    POST \
    "$PRODUCTS_URL" \
    "" \
    '{"name":"AUTOTEST precision","description":"precision boundary","price":10.999,"currency":"INR","stockQuantity":1}'

# ------------------------------------------------------------
# USER authorization
# ------------------------------------------------------------

header "3. ROLE_USER Authorization Tests"

run_test \
    "POST /api/v1/products with ROLE_USER → 403" \
    403 \
    POST \
    "$PRODUCTS_URL" \
    "$USER_JWT" \
    "$PRODUCT_BODY"

# ------------------------------------------------------------
# ADMIN creates product
# ------------------------------------------------------------

header "4. ROLE_ADMIN Product Creation"

rm -f "$PRODUCT_RESPONSE" "$PRODUCT_HEADERS"

CREATE_STATUS="$(
    curl -k -sS \
        -D "$PRODUCT_HEADERS" \
        -o "$PRODUCT_RESPONSE" \
        -w "%{http_code}" \
        -X POST \
        "$PRODUCTS_URL" \
        -H "Accept: application/json" \
        -H "Authorization: Bearer ${ADMIN_JWT}" \
        -H "Content-Type: application/json" \
        -d "$PRODUCT_BODY"
)"

echo "HTTP Status: ${CREATE_STATUS}"

echo
echo "Response:"
cat "$PRODUCT_RESPONSE"
echo

case "$CREATE_STATUS" in
    200|201)
        success "ADMIN product creation succeeded."
        ((PASSED++))
        ((TOTAL++))
        ;;
    *)
        error "ADMIN product creation failed."
        ((FAILED++))
        ((TOTAL++))

        echo
        echo "The test cannot continue without a productId."
        exit 1
        ;;
esac

# ------------------------------------------------------------
# Extract productId
# ------------------------------------------------------------

PRODUCT_ID="$(extract_product_id "$PRODUCT_RESPONSE")"

if [[ -z "$PRODUCT_ID" ]]; then
    error "Could not extract productId."
    exit 1
fi

success "Temporary product created:"
echo "  ${PRODUCT_ID}"

PRODUCT_URL="${PRODUCTS_URL}/${PRODUCT_ID}"

# ------------------------------------------------------------
# Inventory / mTLS / idempotency
# ------------------------------------------------------------

header "5. Internal Inventory mTLS + Idempotency"

run_test \
    "POST /inventory/reservations without service certificate → 401" \
    401 \
    POST \
    "${INVENTORY_URL}/reservations" \
    "" \
    "{\"orderId\":\"00000000-0000-0000-0000-000000000001\",\"productId\":\"${PRODUCT_ID}\",\"quantity\":1}"

run_test \
    "POST /inventory/reservations with ROLE_USER but no service certificate → 403 (valid request headers)" \
    403 \
    POST \
    "${INVENTORY_URL}/reservations" \
    "$USER_JWT" \
    "{\"orderId\":\"00000000-0000-0000-0000-000000000002\",\"productId\":\"${PRODUCT_ID}\",\"quantity\":1}" \
    "product-e2e-${PRODUCT_ID}-authz-check-$(date +%s%N)"

ORDER_ID_1="00000000-0000-0000-0000-000000000101"
RESERVE_KEY_1="product-e2e-${PRODUCT_ID}-reserve-1-$(date +%s%N)"
RESERVE_BODY_1="{\"orderId\":\"${ORDER_ID_1}\",\"productId\":\"${PRODUCT_ID}\",\"quantity\":2}"

rm -f "$INVENTORY_RESPONSE"
STATUS_RESERVE_1="$(curl -k -sS --cert "$ORDER_CLIENT_CERT" --key "$ORDER_CLIENT_KEY" \
    -o "$INVENTORY_RESPONSE" -w '%{http_code}' -X POST \
    "${INVENTORY_URL}/reservations" \
    -H 'Accept: application/json' \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${RESERVE_KEY_1}" \
    -d "$RESERVE_BODY_1")"

echo "Expected: HTTP 201"
echo "Actual:   HTTP ${STATUS_RESERVE_1}"
cat "$INVENTORY_RESPONSE"
echo
if [[ "$STATUS_RESERVE_1" == "201" ]]; then
    success "PASS - Inventory reservation created"
    ((PASSED++)); ((TOTAL++))
else
    error "FAIL - Inventory reservation creation"
    ((FAILED++)); ((TOTAL++))
    exit 1
fi

RESERVATION_ID_1="$(extract_reservation_id "$INVENTORY_RESPONSE")"
if [[ -z "$RESERVATION_ID_1" ]]; then
    error "Reservation ID was not returned."
    exit 1
fi

# Same key + same request must replay.
run_inventory_test \
    "Same inventory idempotency key replays reservation" \
    201 \
    POST \
    "${INVENTORY_URL}/reservations" \
    "" \
    "$RESERVE_BODY_1" \
    "$RESERVE_KEY_1"

# Same key + different request must conflict.
run_inventory_test \
    "Inventory idempotency key reuse with different quantity → 409" \
    409 \
    POST \
    "${INVENTORY_URL}/reservations" \
    "" \
    "{\"orderId\":\"${ORDER_ID_1}\",\"productId\":\"${PRODUCT_ID}\",\"quantity\":3}" \
    "$RESERVE_KEY_1"

run_inventory_test \
    "Commit inventory reservation" \
    200 \
    POST \
    "${INVENTORY_URL}/reservations/${RESERVATION_ID_1}/commit"

run_inventory_test \
    "Repeated commit is idempotent" \
    200 \
    POST \
    "${INVENTORY_URL}/reservations/${RESERVATION_ID_1}/commit"

ORDER_ID_2="00000000-0000-0000-0000-000000000102"
RESERVE_KEY_2="product-e2e-${PRODUCT_ID}-reserve-2-$(date +%s%N)"
RESERVE_BODY_2="{\"orderId\":\"${ORDER_ID_2}\",\"productId\":\"${PRODUCT_ID}\",\"quantity\":3}"

rm -f "$INVENTORY_RESPONSE"
STATUS_RESERVE_2="$(curl -k -sS --cert "$ORDER_CLIENT_CERT" --key "$ORDER_CLIENT_KEY" \
    -o "$INVENTORY_RESPONSE" -w '%{http_code}' -X POST \
    "${INVENTORY_URL}/reservations" \
    -H 'Accept: application/json' \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${RESERVE_KEY_2}" \
    -d "$RESERVE_BODY_2")"

echo "Expected: HTTP 201"
echo "Actual:   HTTP ${STATUS_RESERVE_2}"
cat "$INVENTORY_RESPONSE"
echo
if [[ "$STATUS_RESERVE_2" == "201" ]]; then
    success "PASS - Second inventory reservation created"
    ((PASSED++)); ((TOTAL++))
else
    error "FAIL - Second inventory reservation creation"
    ((FAILED++)); ((TOTAL++))
    exit 1
fi

RESERVATION_ID_2="$(extract_reservation_id "$INVENTORY_RESPONSE")"
if [[ -z "$RESERVATION_ID_2" ]]; then
    error "Second reservation ID was not returned."
    exit 1
fi

run_inventory_test \
    "Release inventory reservation" \
    200 \
    POST \
    "${INVENTORY_URL}/reservations/${RESERVATION_ID_2}/release"

run_inventory_test \
    "Repeated release is idempotent" \
    200 \
    POST \
    "${INVENTORY_URL}/reservations/${RESERVATION_ID_2}/release"

# ------------------------------------------------------------
# GET product after inventory lifecycle
# ------------------------------------------------------------

header "6. Inventory Stock Consistency"

test_name "Committed + released reservations leave available stock at 98"
STOCK_RESPONSE="${TMP_DIR}/stock-consistency.json"
STOCK_STATUS="$(curl -k -sS -o "$STOCK_RESPONSE" -w '%{http_code}' -X GET "$PRODUCT_URL" -H 'Accept: application/json')"
STOCK_VALUE="$(if command -v jq >/dev/null 2>&1; then jq -r '.stockQuantity // empty' "$STOCK_RESPONSE"; else python - "$STOCK_RESPONSE" <<'PYJSON2'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    print(json.load(f).get('stockQuantity', ''))
PYJSON2
fi)"
echo "Expected: HTTP 200 and stockQuantity=98"
echo "Actual:   HTTP ${STOCK_STATUS}, stockQuantity=${STOCK_VALUE}"
if [[ "$STOCK_STATUS" == "200" && "$STOCK_VALUE" == "98" ]]; then
    success "PASS"
    ((PASSED++)); ((TOTAL++))
else
    error "FAIL"
    cat "$STOCK_RESPONSE"
    echo
    ((FAILED++)); ((TOTAL++))
fi

# ------------------------------------------------------------
# GET product
# ------------------------------------------------------------

header "7. GET Product By ID"

run_test \
    "GET /api/v1/products/{productId} without JWT → 200" \
    200 \
    GET \
    "$PRODUCT_URL"

# ------------------------------------------------------------
# PATCH product
# ------------------------------------------------------------

header "8. Product PATCH Authorization"

UPDATE_BODY='{
  "name": "AUTOTEST - Keychron K8 QMK V2",
  "description": "Updated by automated test.",
  "price": 14990.00,
  "currency": "INR"
}'

run_test \
    "PATCH /products/{id} with ROLE_USER → 403" \
    403 \
    PATCH \
    "$PRODUCT_URL" \
    "$USER_JWT" \
    "$UPDATE_BODY"

run_test_success \
    "PATCH /products/{id} with ROLE_ADMIN → Success" \
    PATCH \
    "$PRODUCT_URL" \
    "$ADMIN_JWT" \
    "$UPDATE_BODY"

# ------------------------------------------------------------
# Stock
# ------------------------------------------------------------

header "9. Stock PATCH Authorization"

STOCK_BODY='{
  "stockQuantity": 150
}'

run_test \
    "PATCH /products/{id}/stock with ROLE_USER → 403" \
    403 \
    PATCH \
    "${PRODUCT_URL}/stock" \
    "$USER_JWT" \
    "$STOCK_BODY"

run_test_success \
    "PATCH /products/{id}/stock with ROLE_ADMIN → Success" \
    PATCH \
    "${PRODUCT_URL}/stock" \
    "$ADMIN_JWT" \
    "$STOCK_BODY"

# ------------------------------------------------------------
# Status
# ------------------------------------------------------------

header "10. Status PATCH Authorization"

STATUS_BODY='{
  "status": "INACTIVE"
}'

run_test \
    "PATCH /products/{id}/status with ROLE_USER → 403" \
    403 \
    PATCH \
    "${PRODUCT_URL}/status" \
    "$USER_JWT" \
    "$STATUS_BODY"

run_test_success \
    "PATCH /products/{id}/status with ROLE_ADMIN → Success" \
    PATCH \
    "${PRODUCT_URL}/status" \
    "$ADMIN_JWT" \
    "$STATUS_BODY"

# ------------------------------------------------------------
# DELETE
# ------------------------------------------------------------

header "11. DELETE Authorization"

run_test \
    "DELETE /products/{id} with ROLE_USER → 403" \
    403 \
    DELETE \
    "$PRODUCT_URL" \
    "$USER_JWT"

run_test_success \
    "DELETE /products/{id} with ROLE_ADMIN → Success" \
    DELETE \
    "$PRODUCT_URL" \
    "$ADMIN_JWT"

# Product was deleted/deactivated.
PRODUCT_ID=""

# ------------------------------------------------------------
# Final summary
# ------------------------------------------------------------

header "FINAL TEST SUMMARY"

echo -e "${BOLD}Total tests : ${TOTAL}${RESET}"
echo -e "${GREEN}${BOLD}Passed      : ${PASSED}${RESET}"
echo -e "${RED}${BOLD}Failed      : ${FAILED}${RESET}"

echo

if [[ "$FAILED" -eq 0 ]]; then

    echo -e "${GREEN}${BOLD}============================================================${RESET}"
    echo -e "${GREEN}${BOLD}ALL TESTS PASSED${RESET}"
    echo -e "${GREEN}${BOLD}============================================================${RESET}"
    echo

    exit 0

else

    echo -e "${RED}${BOLD}============================================================${RESET}"
    echo -e "${RED}${BOLD}TEST FAILURES DETECTED${RESET}"
    echo -e "${RED}${BOLD}============================================================${RESET}"
    echo

    exit 1
fi
