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


set -u

# ================================================================
# PAYMENT SERVICE MANUAL E2E / RELIABILITY TEST
# ================================================================
#
# This is intentionally NOT an integration-test suite.
#
# It exercises the real running services over HTTPS:
#
#     User Service      : 8443
#     Payment Service   : 8446
#
# It validates:
#
#   1. Payment Service health
#   2. Authentication boundary
#   3. mTLS service boundary
#   4. Internal authorization
#   5. Payment authorization
#   6. Payment lookup ownership
#   7. Provider-side durability
#   8. Provider idempotency
#   9. Payment capture
#  10. Duplicate capture
#  11. Invalid second capture
#  12. Provider state durability
#
# Requirements:
#
#   - curl
#   - openssl
#   - python
#
# Environment variables:
#
#   USER_EMAIL
#   USER_PASSWORD
#
# Example:
#
#   USER_EMAIL="$USER_EMAIL" \
#   USER_PASSWORD="$USER_PASSWORD" \
#   ./scripts/test-payment-service.sh
#
# ================================================================


# ================================================================
# CONFIGURATION
# ================================================================

USER_SERVICE_URL="https://localhost:8443"
PAYMENT_SERVICE_URL="https://localhost:8446"

ORDER_CLIENT_CERT="/c/Users/swata/.config/microservice-orders/tls/order-client-cert.pem"
ORDER_CLIENT_KEY="/c/Users/swata/.config/microservice-orders/tls/order-client-key.pem"

COOKIE_FILE="$(mktemp)"
CSRF_COOKIE_FILE="$(mktemp)"
LOGIN_RESPONSE_FILE="$(mktemp)"
RESPONSE_FILE="$(mktemp)"
RESPONSE_HEADERS_FILE="$(mktemp)"

cleanup() {
    rm -f \
        "$COOKIE_FILE" \
        "$CSRF_COOKIE_FILE" \
        "$LOGIN_RESPONSE_FILE" \
        "$RESPONSE_FILE" \
        "$RESPONSE_HEADERS_FILE"
}

trap cleanup EXIT


# ================================================================
# REQUIREMENTS
# ================================================================

require_command() {
    local command_name="$1"

    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "ERROR: required command not found: $command_name"
        exit 1
    fi
}

require_command curl
require_command openssl
require_command python


# ================================================================
# TEST STATE
# ================================================================

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0


TEST_USER_ID=""
PAYMENT_ID=""
PROVIDER_PAYMENT_ID=""


# ================================================================
# TEST HELPERS
# ================================================================

pass() {
    local description="$1"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    PASSED_TESTS=$((PASSED_TESTS + 1))

    printf '[PASS] %s\n' "$description"
}


fail() {
    local description="$1"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    FAILED_TESTS=$((FAILED_TESTS + 1))

    printf '[FAIL] %s\n' "$description"
}


assert_status() {
    local actual="$1"
    local expected="$2"
    local description="$3"

    if [[ "$actual" == "$expected" ]]; then
        pass "$description"
    else
        fail "$description"
        echo "       expected HTTP: $expected"
        echo "       actual HTTP  : $actual"
        echo "       response     :"
        sed 's/^/       /' "$RESPONSE_FILE"
    fi
}


get_status() {
    curl \
        --silent \
        --show-error \
        --insecure \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$@"
}


get_status_with_headers() {
    curl \
        --silent \
        --show-error \
        --insecure \
        --dump-header "$RESPONSE_HEADERS_FILE" \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$@"
}


json_value() {
    local json_file="$1"
    local field="$2"

    python - "$json_file" "$field" <<'PY'
import json
import sys

file_name = sys.argv[1]
field = sys.argv[2]

with open(file_name, "r", encoding="utf-8") as file:
    data = json.load(file)

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


assert_json_value() {
    local json_file="$1"
    local field="$2"
    local expected="$3"
    local description="$4"

    local actual

    actual="$(json_value "$json_file" "$field" 2>/dev/null || true)"

    if [[ "$actual" == "$expected" ]]; then
        pass "$description"
    else
        fail "$description"
        echo "       expected: $expected"
        echo "       actual  : $actual"
    fi
}


# ================================================================
# PRE-FLIGHT
# ================================================================

echo
echo "================================================"
echo " Payment Service Manual Validation"
echo "================================================"
echo

if [[ -z "${USER_EMAIL:-}" ]]; then
    echo "ERROR: USER_EMAIL is not set."
    echo
    echo 'Example:'
    echo 'USER_EMAIL="$USER_EMAIL" USER_PASSWORD="$USER_PASSWORD" ./scripts/test-payment-service.sh'
    exit 1
fi

if [[ -z "${USER_PASSWORD:-}" ]]; then
    echo "ERROR: USER_PASSWORD is not set."
    exit 1
fi

if [[ ! -f "$ORDER_CLIENT_CERT" ]]; then
    echo "ERROR: Order client certificate not found:"
    echo "       $ORDER_CLIENT_CERT"
    exit 1
fi

if [[ ! -f "$ORDER_CLIENT_KEY" ]]; then
    echo "ERROR: Order client key not found:"
    echo "       $ORDER_CLIENT_KEY"
    exit 1
fi


# ================================================================
# TEST 1
# PAYMENT SERVICE HEALTH
# ================================================================

echo
echo "[1] Payment Service health"

status="$(
    get_status \
        "$PAYMENT_SERVICE_URL/actuator/health"
)"

assert_status \
    "$status" \
    "200" \
    "Payment Service health endpoint"


# ================================================================
# TEST 2
# INTERNAL ENDPOINT WITHOUT CERTIFICATE
# ================================================================

echo
echo "[2] Internal endpoint without client certificate"

FAKE_PAYMENT_ID="$(python - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

status="$(
    get_status \
        -X POST \
        -H "Idempotency-Key: no-cert-test-$(date +%s%N)" \
        "$PAYMENT_SERVICE_URL/api/v1/payments/internal/$FAKE_PAYMENT_ID/capture"
)"

if [[ "$status" == "401" || "$status" == "403" || "$status" == "400" || "$status" == "404" ]]; then
    pass "Internal endpoint is not publicly callable without service identity"
else
    fail "Internal endpoint unexpectedly accepted unauthenticated request"
    echo "       HTTP: $status"
    cat "$RESPONSE_FILE"
fi


# ================================================================
# TEST 3
# USER AUTHENTICATION
# ================================================================

echo
echo "[3] Authenticate test user"

CSRF_STATUS="$(
    get_status_with_headers \
        -c "$CSRF_COOKIE_FILE" \
        -b "$CSRF_COOKIE_FILE" \
        "$USER_SERVICE_URL/api/v1/auth/csrf"
)"

assert_status \
    "$CSRF_STATUS" \
    "200" \
    "User Service CSRF bootstrap"


if [[ "$CSRF_STATUS" != "200" ]]; then
    echo
    echo "Cannot continue without CSRF bootstrap."
    exit 1
fi


CSRF_TOKEN="$(
    awk '$6 == "XSRF-TOKEN" { print $7; exit }' \
        "$CSRF_COOKIE_FILE"
)"

if [[ -z "$CSRF_TOKEN" ]]; then
    echo "ERROR: XSRF-TOKEN cookie was not returned."
    exit 1
fi


LOGIN_STATUS="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        --cookie "$CSRF_COOKIE_FILE" \
        --cookie-jar "$COOKIE_FILE" \
        -H "Content-Type: application/json" \
        -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
        -d "{
              \"email\": \"$USER_EMAIL\",
              \"password\": \"$USER_PASSWORD\"
            }" \
        --output "$LOGIN_RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$USER_SERVICE_URL/api/v1/auth/login"
)"

assert_status \
    "$LOGIN_STATUS" \
    "200" \
    "User login"


if [[ "$LOGIN_STATUS" != "200" ]]; then
    echo
    echo "Login response:"
    cat "$LOGIN_RESPONSE_FILE"
    exit 1
fi


# ================================================================
# EXTRACT ACCESS TOKEN
# ================================================================

ACCESS_TOKEN="$(
    awk '$6 == "__Host-access_token" { print $7; exit }' \
        "$COOKIE_FILE"
)"

if [[ -z "$ACCESS_TOKEN" ]]; then
    echo "ERROR: access token cookie not found."
    echo
    echo "Cookie file:"
    cat "$COOKIE_FILE"
    exit 1
fi


# ================================================================
# DECODE JWT SUB
# ================================================================

TEST_USER_ID="$(
    python - "$ACCESS_TOKEN" <<'PY'
import base64
import json
import sys

token = sys.argv[1]

parts = token.split(".")

if len(parts) != 3:
    raise SystemExit("Invalid JWT")

payload = parts[1]

payload += "=" * (-len(payload) % 4)

decoded = base64.urlsafe_b64decode(payload)

claims = json.loads(decoded)

sub = claims.get("sub")

if not sub:
    raise SystemExit("JWT does not contain sub")

print(sub)
PY
)"

if [[ -z "$TEST_USER_ID" ]]; then
    echo "ERROR: unable to determine authenticated user ID."
    exit 1
fi

echo
echo "Authenticated user:"
echo "$TEST_USER_ID"


# ================================================================
# TEST 4
# USER JWT CANNOT CALL INTERNAL ENDPOINT
# ================================================================

echo
echo "[4] User JWT cannot call internal payment command"

status="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -H "Idempotency-Key: jwt-only-test-$(date +%s%N)" \
        -X POST \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$PAYMENT_SERVICE_URL/api/v1/payments/internal/$FAKE_PAYMENT_ID/capture"
)"

if [[ "$status" == "403" || "$status" == "401" || "$status" == "404" ]]; then
    pass "User JWT cannot access service-only payment command"
else
    fail "User JWT unexpectedly reached internal payment command"
    echo "       HTTP: $status"
    cat "$RESPONSE_FILE"
fi


# ================================================================
# TEST 5
# SERVICE mTLS AUTHORIZE
# ================================================================

echo
echo "[5] Authorize payment through Order Service identity"

ORDER_ID="$(
    python - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

PAYMENT_ID="$(
    python - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

AUTH_IDEMPOTENCY_KEY="manual-authorize-$(date +%s%N)"

AUTH_BODY="{
    \"orderId\": \"$ORDER_ID\",
    \"userId\": \"$TEST_USER_ID\",
    \"paymentMethod\": \"CARD\",
    \"amount\": 1999.00,
    \"currency\": \"INR\"
}"


AUTH_STATUS="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        --cert "$ORDER_CLIENT_CERT" \
        --key "$ORDER_CLIENT_KEY" \
        -H "Content-Type: application/json" \
        -H "Idempotency-Key: $AUTH_IDEMPOTENCY_KEY" \
        -d "$AUTH_BODY" \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$PAYMENT_SERVICE_URL/api/v1/payments/internal/authorize"
)"

assert_status \
    "$AUTH_STATUS" \
    "201" \
    "Order-service mTLS payment authorization"


if [[ "$AUTH_STATUS" != "201" ]]; then
    echo
    echo "Authorization response:"
    cat "$RESPONSE_FILE"
    exit 1
fi


# ================================================================
# READ PAYMENT ID
# ================================================================

CREATED_PAYMENT_ID="$(
    json_value \
        "$RESPONSE_FILE" \
        "paymentId"
)"

if [[ -z "$CREATED_PAYMENT_ID" ]]; then
    echo "ERROR: paymentId missing from authorization response."
    cat "$RESPONSE_FILE"
    exit 1
fi

PAYMENT_ID="$CREATED_PAYMENT_ID"


PROVIDER_PAYMENT_ID="$(
    json_value \
        "$RESPONSE_FILE" \
        "providerPaymentId"
)"

if [[ -z "$PROVIDER_PAYMENT_ID" ]]; then
    echo "ERROR: providerPaymentId missing."
    cat "$RESPONSE_FILE"
    exit 1
fi


assert_json_value \
    "$RESPONSE_FILE" \
    "status" \
    "AUTHORIZED" \
    "Payment becomes AUTHORIZED"


# ================================================================
# TEST 6
# DUPLICATE AUTHORIZE
# ================================================================

echo
echo "[6] Repeat authorization with same idempotency key"

DUPLICATE_AUTH_STATUS="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        --cert "$ORDER_CLIENT_CERT" \
        --key "$ORDER_CLIENT_KEY" \
        -H "Content-Type: application/json" \
        -H "Idempotency-Key: $AUTH_IDEMPOTENCY_KEY" \
        -d "$AUTH_BODY" \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$PAYMENT_SERVICE_URL/api/v1/payments/internal/authorize"
)"

assert_status \
    "$DUPLICATE_AUTH_STATUS" \
    "201" \
    "Duplicate authorization is idempotent"


DUPLICATE_PAYMENT_ID="$(
    json_value \
        "$RESPONSE_FILE" \
        "paymentId"
)"

if [[ "$DUPLICATE_PAYMENT_ID" == "$PAYMENT_ID" ]]; then
    pass "Duplicate authorization returned the same payment"
else
    fail "Duplicate authorization created a different payment"
fi


# ================================================================
# TEST 7
# CUSTOMER PAYMENT LOOKUP
# ================================================================

echo
echo "[7] Customer reads own payment"

LOOKUP_STATUS="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$PAYMENT_SERVICE_URL/api/v1/payments/$PAYMENT_ID"
)"

assert_status \
    "$LOOKUP_STATUS" \
    "200" \
    "Authenticated customer can read own payment"


# ================================================================
# TEST 8
# CAPTURE WITH mTLS
# ================================================================

echo
echo "[8] Capture authorized payment"

CAPTURE_KEY="manual-capture-$(date +%s%N)"

CAPTURE_STATUS="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        --cert "$ORDER_CLIENT_CERT" \
        --key "$ORDER_CLIENT_KEY" \
        -H "Idempotency-Key: $CAPTURE_KEY" \
        -X POST \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$PAYMENT_SERVICE_URL/api/v1/payments/internal/$PAYMENT_ID/capture"
)"

assert_status \
    "$CAPTURE_STATUS" \
    "200" \
    "Authorized payment can be captured"


assert_json_value \
    "$RESPONSE_FILE" \
    "status" \
    "CAPTURED" \
    "Payment becomes CAPTURED"


# ================================================================
# TEST 9
# DUPLICATE CAPTURE
# ================================================================

echo
echo "[9] Repeat capture with same idempotency key"

DUPLICATE_CAPTURE_STATUS="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        --cert "$ORDER_CLIENT_CERT" \
        --key "$ORDER_CLIENT_KEY" \
        -H "Idempotency-Key: $CAPTURE_KEY" \
        -X POST \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$PAYMENT_SERVICE_URL/api/v1/payments/internal/$PAYMENT_ID/capture"
)"

assert_status \
    "$DUPLICATE_CAPTURE_STATUS" \
    "200" \
    "Duplicate capture is idempotent"


assert_json_value \
    "$RESPONSE_FILE" \
    "status" \
    "CAPTURED" \
    "Duplicate capture remains CAPTURED"


# ================================================================
# TEST 10
# SECOND CAPTURE WITH DIFFERENT KEY
# ================================================================

echo
echo "[10] Capture already captured payment with new key"

SECOND_CAPTURE_KEY="manual-second-capture-$(date +%s%N)"

SECOND_CAPTURE_STATUS="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        --cert "$ORDER_CLIENT_CERT" \
        --key "$ORDER_CLIENT_KEY" \
        -H "Idempotency-Key: $SECOND_CAPTURE_KEY" \
        -X POST \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$PAYMENT_SERVICE_URL/api/v1/payments/internal/$PAYMENT_ID/capture"
)"

assert_status \
    "$SECOND_CAPTURE_STATUS" \
    "502" \
    "Invalid provider capture returns 502"


# ================================================================
# TEST 11
# PAYMENT STATE MUST REMAIN CAPTURED
# ================================================================

echo
echo "[11] Verify failed second capture did not corrupt payment state"

VERIFY_STATUS="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$PAYMENT_SERVICE_URL/api/v1/payments/$PAYMENT_ID"
)"

assert_status \
    "$VERIFY_STATUS" \
    "200" \
    "Payment remains readable after provider failure"


assert_json_value \
    "$RESPONSE_FILE" \
    "status" \
    "CAPTURED" \
    "Payment remains CAPTURED after failed duplicate operation"


# ================================================================
# TEST 12
# SAME FAILED PROVIDER OPERATION MUST REPLAY
# ================================================================

echo
echo "[12] Repeat failed provider operation with same key"

REPLAY_FAILED_CAPTURE_STATUS="$(
    curl \
        --silent \
        --show-error \
        --insecure \
        --cert "$ORDER_CLIENT_CERT" \
        --key "$ORDER_CLIENT_KEY" \
        -H "Idempotency-Key: $SECOND_CAPTURE_KEY" \
        -X POST \
        --output "$RESPONSE_FILE" \
        --write-out '%{http_code}' \
        "$PAYMENT_SERVICE_URL/api/v1/payments/internal/$PAYMENT_ID/capture"
)"

assert_status \
    "$REPLAY_FAILED_CAPTURE_STATUS" \
    "502" \
    "Provider failure is replayed consistently"


# ================================================================
# FINAL RESULT
# ================================================================

echo
echo "================================================"
echo " Payment Service Test Result"
echo "================================================"
echo
echo "Total tests : $TOTAL_TESTS"
echo "Passed      : $PASSED_TESTS"
echo "Failed      : $FAILED_TESTS"
echo

if [[ "$FAILED_TESTS" -eq 0 ]]; then
    echo "ALL TESTS PASSED"
    exit 0
fi

echo "SOME TESTS FAILED"
exit 1