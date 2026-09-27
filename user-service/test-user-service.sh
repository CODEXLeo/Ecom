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
set -o pipefail

BASE_URL="${BASE_URL:-https://localhost:8443}"
ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"

USER_JAR="$(mktemp)"
ADMIN_JAR="$(mktemp)"
LOCKOUT_JAR="$(mktemp)"
TMP="$(mktemp -d)"

PASS_COUNT=0
FAIL_COUNT=0

cleanup() {
    rm -f "$USER_JAR" "$ADMIN_JAR" "$LOCKOUT_JAR"
    rm -rf "$TMP"
}

trap cleanup EXIT

pass() {
    echo "  [PASS] $1"
    PASS_COUNT=$((PASS_COUNT + 1))
}

fail() {
    echo "  [FAIL] $1"
    FAIL_COUNT=$((FAIL_COUNT + 1))
}

section() {
    echo
    echo "============================================================"
    echo "$1"
    echo "============================================================"
}

check_status() {
    local actual="$1"
    local expected="$2"
    local description="$3"

    if [[ "$actual" == "$expected" ]]; then
        pass "$description ($actual)"
    else
        fail "$description - expected $expected, got $actual"
    fi
}

cookie_value() {
    local jar="$1"
    local name="$2"

    awk -v n="$name" '$6 == n {print $7}' "$jar" | tail -n 1
}

csrf() {
    local jar="$1"

    curl -k -sS \
        -b "$jar" \
        -c "$jar" \
        "$BASE_URL/api/v1/auth/csrf" >/dev/null

    cookie_value "$jar" "XSRF-TOKEN"
}

login() {
    local jar="$1"
    local email="$2"
    local password="$3"
    local token="$4"

    curl -k -sS \
        -o /dev/null \
        -w "%{http_code}" \
        -b "$jar" \
        -c "$jar" \
        -X POST \
        "$BASE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -H "X-XSRF-TOKEN: $token" \
        -d "{\"email\":\"$email\",\"password\":\"$password\"}"
}

register_user() {
    local jar="$1"
    local token="$2"
    local first_name="$3"
    local last_name="$4"
    local email="$5"
    local password="$6"
    local output="$7"

    curl -k -sS \
        -o "$output" \
        -w "%{http_code}" \
        -b "$jar" \
        -c "$jar" \
        -X POST \
        "$BASE_URL/api/v1/auth/register" \
        -H "Content-Type: application/json" \
        -H "X-XSRF-TOKEN: $token" \
        -d "{\"firstName\":\"$first_name\",\"lastName\":\"$last_name\",\"email\":\"$email\",\"password\":\"$password\"}"
}

refresh() {
    local jar="$1"
    local token="$2"

    curl -k -sS \
        -o /dev/null \
        -w "%{http_code}" \
        -b "$jar" \
        -c "$jar" \
        -X POST \
        "$BASE_URL/api/v1/auth/refresh" \
        -H "X-XSRF-TOKEN: $token"
}

clear_jar() {
    local jar="$1"

    : > "$jar"
}

echo
echo "Microservice User Service - Extended HTTP Regression Suite"
echo "Base URL: $BASE_URL"
echo

if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required."
    exit 1
fi

if [[ -z "$ADMIN_EMAIL" ]]; then
    read -r -p "Admin email: " ADMIN_EMAIL
fi

if [[ -z "$ADMIN_PASSWORD" ]]; then
    read -r -s -p "Admin password: " ADMIN_PASSWORD
    echo
fi

STAMP="$(date +%Y%m%d%H%M%S)-${BASHPID}"

USER_EMAIL="regression-${STAMP}@example.com"
USER_PASSWORD="${TEST_USER_PASSWORD:-}"
NEW_PASSWORD="${TEST_USER_NEW_PASSWORD:-}"

LOCKOUT_EMAIL="lockout-${STAMP}@example.com"
LOCKOUT_PASSWORD="${TEST_USER_LOCKOUT_PASSWORD:-}"

USER_ID=""
LOCKOUT_USER_ID=""

# ============================================================
# 0. SERVICE AVAILABILITY
# ============================================================

section "0. Service availability"

STATUS=$(curl -k -sS -o /dev/null -w "%{http_code}" \
    "$BASE_URL/api/v1/auth/csrf")

check_status "$STATUS" "200" "CSRF endpoint is reachable"

if [[ "$STATUS" != "200" ]]; then
    exit 1
fi

# ============================================================
# 1. REGISTRATION AND VALIDATION
# ============================================================

section "1. Registration and validation"

TOKEN="$(csrf "$USER_JAR")"

if [[ -n "$TOKEN" ]]; then
    pass "Registration CSRF token acquired"
else
    fail "Registration CSRF token acquired"
fi

STATUS=$(register_user \
    "$USER_JAR" \
    "$TOKEN" \
    "Regression" \
    "User" \
    "$USER_EMAIL" \
    "$USER_PASSWORD" \
    "$TMP/register.json")

check_status "$STATUS" "201" "Registration succeeds"

USER_ID="$(sed -n 's/.*"userId":"\([^"]*\)".*/\1/p' "$TMP/register.json")"

if [[ -n "$USER_ID" ]]; then
    pass "Registration returns userId"
else
    fail "Registration returns userId"
fi

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(register_user \
    "$USER_JAR" \
    "$TOKEN" \
    "Duplicate" \
    "User" \
    "$USER_EMAIL" \
    "$USER_PASSWORD" \
    "$TMP/duplicate.json")

check_status "$STATUS" "409" "Duplicate registration is rejected"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(register_user \
    "$USER_JAR" \
    "$TOKEN" \
    "Bad" \
    "Password" \
    "weak-${STAMP}@example.com" \
    "weakpass" \
    "$TMP/weak.json")

check_status "$STATUS" "400" "Weak registration password is rejected"

# ============================================================
# 2. LOGIN, EMAIL NORMALIZATION AND PROFILE
# ============================================================

section "2. Login, email normalization and profile"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(login \
    "$USER_JAR" \
    "${USER_EMAIL^^}" \
    "$USER_PASSWORD" \
    "$TOKEN")

check_status "$STATUS" "200" "Login accepts normalized email"

[[ -n "$(cookie_value "$USER_JAR" "__Host-access_token")" ]] \
    && pass "__Host-access_token issued" \
    || fail "__Host-access_token issued"

[[ -n "$(cookie_value "$USER_JAR" "__Host-refresh_token")" ]] \
    && pass "__Host-refresh_token issued" \
    || fail "__Host-refresh_token issued"

STATUS=$(curl -k -sS \
    -o "$TMP/me.json" \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    "$BASE_URL/api/v1/users/me")

check_status "$STATUS" "200" "Authenticated /users/me succeeds"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    -X PATCH \
    "$BASE_URL/api/v1/users/me" \
    -H "Content-Type: application/json" \
    -d '{"firstName":"NoCsrf","lastName":"Rejected"}')

check_status "$STATUS" "403" "Profile update without CSRF is rejected"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(curl -k -sS \
    -o "$TMP/profile.json" \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    -c "$USER_JAR" \
    -X PATCH \
    "$BASE_URL/api/v1/users/me" \
    -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $TOKEN" \
    -d '{"firstName":"Regression","lastName":"Verified"}')

check_status "$STATUS" "200" "Profile update with CSRF succeeds"

# ============================================================
# 3. PASSWORD CHANGE
# ============================================================

section "3. Password change"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    -c "$USER_JAR" \
    -X PATCH \
    "$BASE_URL/api/v1/users/me/password" \
    -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $TOKEN" \
    -d '{"currentPassword":"WrongCurrent@1","newPassword":"$TEST_USER_NEW_PASSWORD"}')

check_status "$STATUS" "400" "Wrong current password is rejected"

# Acquire a fresh CSRF token before the next state-changing request.
TOKEN="$(csrf "$USER_JAR")"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    -c "$USER_JAR" \
    -X PATCH \
    "$BASE_URL/api/v1/users/me/password" \
    -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $TOKEN" \
    -d "{\"currentPassword\":\"$USER_PASSWORD\",\"newPassword\":\"$USER_PASSWORD\"}")

check_status "$STATUS" "400" "Password reuse is rejected"

# Fresh CSRF token again.
TOKEN="$(csrf "$USER_JAR")"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    -c "$USER_JAR" \
    -X PATCH \
    "$BASE_URL/api/v1/users/me/password" \
    -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $TOKEN" \
    -d "{\"currentPassword\":\"$USER_PASSWORD\",\"newPassword\":\"$NEW_PASSWORD\"}")

check_status "$STATUS" "204" "Password change succeeds"

# Password change must revoke the refresh session.
TOKEN="$(csrf "$USER_JAR")"

STATUS=$(refresh "$USER_JAR" "$TOKEN")

check_status "$STATUS" "401" "Password change revokes refresh sessions"

# Old password must no longer work.
clear_jar "$USER_JAR"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(login \
    "$USER_JAR" \
    "$USER_EMAIL" \
    "$USER_PASSWORD" \
    "$TOKEN")

check_status "$STATUS" "401" "Old password is rejected"

# New password must work.
clear_jar "$USER_JAR"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(login \
    "$USER_JAR" \
    "$USER_EMAIL" \
    "$NEW_PASSWORD" \
    "$TOKEN")

check_status "$STATUS" "200" "New password authenticates"

# ============================================================
# 4. REFRESH ROTATION AND REUSE DETECTION
# ============================================================

section "4. Refresh rotation and reuse detection"

OLD_REFRESH="$(cookie_value "$USER_JAR" "__Host-refresh_token")"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(refresh "$USER_JAR" "$TOKEN")

check_status "$STATUS" "204" "Refresh rotation succeeds"

NEW_REFRESH="$(cookie_value "$USER_JAR" "__Host-refresh_token")"

if [[ -n "$OLD_REFRESH" && "$OLD_REFRESH" != "$NEW_REFRESH" ]]; then
    pass "Refresh token rotates to a new value"
else
    fail "Refresh token rotates to a new value"
fi

# Replay the old refresh token.
#
# This intentionally bypasses the current cookie jar refresh token
# and sends the revoked token directly.
TOKEN="$(csrf "$USER_JAR")"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -X POST \
    "$BASE_URL/api/v1/auth/refresh" \
    -H "Cookie: __Host-refresh_token=$OLD_REFRESH; XSRF-TOKEN=$TOKEN" \
    -H "X-XSRF-TOKEN: $TOKEN")

check_status "$STATUS" "401" "Replayed refresh token is rejected"

# The reuse detection must revoke the complete family.
TOKEN="$(csrf "$USER_JAR")"

STATUS=$(refresh "$USER_JAR" "$TOKEN")

check_status "$STATUS" "401" "Refresh-token family is revoked after reuse detection"

# A fresh login must establish a completely new family.
TOKEN="$(csrf "$USER_JAR")"

STATUS=$(login \
    "$USER_JAR" \
    "$USER_EMAIL" \
    "$NEW_PASSWORD" \
    "$TOKEN")

check_status "$STATUS" "200" "Fresh login establishes a new refresh family"

# ============================================================
# 5. ACCOUNT LOCKOUT
# ============================================================

section "5. Account lockout"

TOKEN="$(csrf "$LOCKOUT_JAR")"

STATUS=$(register_user \
    "$LOCKOUT_JAR" \
    "$TOKEN" \
    "Lockout" \
    "Test" \
    "$LOCKOUT_EMAIL" \
    "$LOCKOUT_PASSWORD" \
    "$TMP/lockout-register.json")

check_status "$STATUS" "201" "Lockout test user is registered"

LOCKOUT_USER_ID="$(sed \
    -n 's/.*"userId":"\([^"]*\)".*/\1/p' \
    "$TMP/lockout-register.json")"

if [[ -n "$LOCKOUT_USER_ID" ]]; then
    pass "Lockout registration returns userId"
else
    fail "Lockout registration returns userId"
fi

for attempt in 1 2 3 4 5; do
    TOKEN="$(csrf "$LOCKOUT_JAR")"

    STATUS=$(login \
        "$LOCKOUT_JAR" \
        "$LOCKOUT_EMAIL" \
        'DefinitelyWrong@999' \
        "$TOKEN")

    check_status \
        "$STATUS" \
        "401" \
        "Wrong password attempt $attempt is rejected"
done

TOKEN="$(csrf "$LOCKOUT_JAR")"

STATUS=$(login \
    "$LOCKOUT_JAR" \
    "$LOCKOUT_EMAIL" \
    "$LOCKOUT_PASSWORD" \
    "$TOKEN")

check_status \
    "$STATUS" \
    "401" \
    "Correct password is rejected while account is locked"

# ============================================================
# 6. ADMIN AUTHENTICATION AND AUTHORIZATION
# ============================================================

section "6. Admin authentication and authorization"

TOKEN="$(csrf "$ADMIN_JAR")"

STATUS=$(login \
    "$ADMIN_JAR" \
    "$ADMIN_EMAIL" \
    "$ADMIN_PASSWORD" \
    "$TOKEN")

check_status "$STATUS" "200" "Admin login succeeds"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    "$BASE_URL/api/v1/admin/users")

check_status "$STATUS" "401" "Unauthenticated admin API is rejected"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    "$BASE_URL/api/v1/admin/users")

check_status "$STATUS" "403" "Normal user is forbidden from admin API"

STATUS=$(curl -k -sS \
    -o "$TMP/users.json" \
    -w "%{http_code}" \
    -b "$ADMIN_JAR" \
    "$BASE_URL/api/v1/admin/users")

check_status "$STATUS" "200" "Admin can list users"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$ADMIN_JAR" \
    "$BASE_URL/api/v1/admin/users/$USER_ID")

check_status "$STATUS" "200" "Admin can retrieve a user"

TOKEN="$(csrf "$ADMIN_JAR")"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$ADMIN_JAR" \
    -X POST \
    "$BASE_URL/api/v1/admin/users/$USER_ID/sessions/revoke" \
    -H "X-XSRF-TOKEN: $TOKEN")

check_status "$STATUS" "204" "Admin can revoke all user sessions"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(refresh "$USER_JAR" "$TOKEN")

check_status "$STATUS" "401" "Admin-revoked user session cannot refresh"

TOKEN="$(csrf "$ADMIN_JAR")"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$ADMIN_JAR" \
    -X PATCH \
    "$BASE_URL/api/v1/admin/users/$USER_ID/status?enabled=false" \
    -H "X-XSRF-TOKEN: $TOKEN")

check_status "$STATUS" "200" "Admin can disable a user"

clear_jar "$USER_JAR"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(login \
    "$USER_JAR" \
    "$USER_EMAIL" \
    "$NEW_PASSWORD" \
    "$TOKEN")

check_status "$STATUS" "401" "Disabled user cannot log in"

TOKEN="$(csrf "$ADMIN_JAR")"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$ADMIN_JAR" \
    -X PATCH \
    "$BASE_URL/api/v1/admin/users/$USER_ID/status?enabled=true" \
    -H "X-XSRF-TOKEN: $TOKEN")

check_status "$STATUS" "200" "Admin can re-enable a user"

# This endpoint is deliberately applied to the LOCKOUT user.
TOKEN="$(csrf "$ADMIN_JAR")"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$ADMIN_JAR" \
    -X POST \
    "$BASE_URL/api/v1/admin/users/$LOCKOUT_USER_ID/unlock" \
    -H "X-XSRF-TOKEN: $TOKEN")

check_status "$STATUS" "200" "Admin can unlock a user"

clear_jar "$LOCKOUT_JAR"

TOKEN="$(csrf "$LOCKOUT_JAR")"

STATUS=$(login \
    "$LOCKOUT_JAR" \
    "$LOCKOUT_EMAIL" \
    "$LOCKOUT_PASSWORD" \
    "$TOKEN")

check_status \
    "$STATUS" \
    "200" \
    "Admin-unlocked user can authenticate"

# ============================================================
# 7. LOGOUT AND CSRF
# ============================================================

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(login \
    "$USER_JAR" \
    "$USER_EMAIL" \
    "$NEW_PASSWORD" \
    "$TOKEN")

check_status \
    "$STATUS" \
    "200" \
    "Disposable user can log in before logout"

# Logout without CSRF must fail.
STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    -X POST \
    "$BASE_URL/api/v1/auth/logout")

check_status \
    "$STATUS" \
    "403" \
    "Logout without CSRF is rejected"

# Logout with a fresh CSRF token.
TOKEN="$(csrf "$USER_JAR")"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    -c "$USER_JAR" \
    -X POST \
    "$BASE_URL/api/v1/auth/logout" \
    -H "X-XSRF-TOKEN: $TOKEN")

check_status \
    "$STATUS" \
    "204" \
    "Logout with CSRF succeeds"

STATUS=$(curl -k -sS \
    -o /dev/null \
    -w "%{http_code}" \
    -b "$USER_JAR" \
    "$BASE_URL/api/v1/users/me")

check_status \
    "$STATUS" \
    "401" \
    "Cleared access cookie no longer authenticates"

TOKEN="$(csrf "$USER_JAR")"

STATUS=$(refresh "$USER_JAR" "$TOKEN")

check_status \
    "$STATUS" \
    "401" \
    "Refresh is rejected after logout"

# ============================================================
# FINAL TEST SUMMARY
# ============================================================

section "FINAL TEST SUMMARY"

echo "Passed : $PASS_COUNT"
echo "Failed : $FAIL_COUNT"

if [[ "$FAIL_COUNT" -eq 0 ]]; then
    echo "ALL EXECUTED TESTS PASSED"
    exit 0
fi

echo "SOME TESTS FAILED"
exit 1
```
