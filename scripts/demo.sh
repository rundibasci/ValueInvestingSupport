#!/usr/bin/env bash
# demo.sh — Val2: Connected Valuation Demo
#
# Exercises the full production path: login → seed FMP data → analyze → logout.
# Shareable with stakeholders as a standalone validation script.
#
# Prerequisites:
#   Application running with MARKET_DATA_SOURCE=fmp and a valid FMP_API_KEY.
#   An admin user must exist (email: admin@example.com, password: Admin1234!).
#   jq must be installed (brew install jq / apt install jq).
#
# Usage:
#   chmod +x scripts/demo.sh
#   ./scripts/demo.sh [BASE_URL] [ADMIN_EMAIL] [ADMIN_PASSWORD]
#
# Examples:
#   ./scripts/demo.sh                                         # localhost
#   ./scripts/demo.sh http://staging.example.com             # staging
#   ./scripts/demo.sh http://localhost:8080 me@co.com Pass1! # custom creds

set -euo pipefail

BASE="${1:-http://localhost:8080}"
ADMIN_EMAIL="${2:-admin@example.com}"
ADMIN_PASSWORD="${3:-Admin1234!}"
TICKERS="${SEED_TICKERS:-AAPL,KO}"

check_status() {
    local status="$1" label="$2"
    if [ "$status" -lt 200 ] || [ "$status" -ge 300 ]; then
        echo "ERROR: $label returned HTTP $status" >&2
        exit 1
    fi
}

echo "========================================"
echo " Val2 Demo — Value Investing Platform"
echo " Base URL : $BASE"
echo " Tickers  : $TICKERS"
echo "========================================"
echo ""

# ------------------------------------------------------------------
echo "=== Step 1: Login ==="
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
LOGIN_BODY=$(echo "$LOGIN_RESPONSE" | head -n -1)
LOGIN_STATUS=$(echo "$LOGIN_RESPONSE" | tail -n 1)
check_status "$LOGIN_STATUS" "POST /auth/login"
TOKEN=$(echo "$LOGIN_BODY" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "$LOGIN_BODY" | jq -r '.refreshToken // empty')
echo "Logged in as $ADMIN_EMAIL"
echo ""

# ------------------------------------------------------------------
echo "=== Step 2: Seed FMP data and run valuation for [$TICKERS] ==="
SEED_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BASE/api/v1/admin/seed?tickers=$TICKERS" \
  -H "Authorization: Bearer $TOKEN")
SEED_BODY=$(echo "$SEED_RESPONSE" | head -n -1)
SEED_STATUS=$(echo "$SEED_RESPONSE" | tail -n 1)
check_status "$SEED_STATUS" "POST /api/v1/admin/seed"
echo "$SEED_BODY" | jq .
echo ""

# ------------------------------------------------------------------
FIRST_TICKER=$(echo "$TICKERS" | cut -d',' -f1)
echo "=== Step 3: Quick analysis for $FIRST_TICKER ==="
ANALYSIS_RESPONSE=$(curl -s -w "\n%{http_code}" \
  "$BASE/api/v1/securities/$FIRST_TICKER/quick-analysis" \
  -H "Authorization: Bearer $TOKEN")
ANALYSIS_BODY=$(echo "$ANALYSIS_RESPONSE" | head -n -1)
ANALYSIS_STATUS=$(echo "$ANALYSIS_RESPONSE" | tail -n 1)
check_status "$ANALYSIS_STATUS" "GET /api/v1/securities/$FIRST_TICKER/quick-analysis"
echo "$ANALYSIS_BODY" | jq .
echo ""

# ------------------------------------------------------------------
echo "=== Step 4: Logout ==="
if [ -n "$REFRESH_TOKEN" ]; then
    LOGOUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/logout" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" \
      -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
    check_status "$LOGOUT_STATUS" "POST /auth/logout"
    echo "Logged out."
else
    echo "No refresh token — skipping logout."
fi

echo ""
echo "========================================"
echo " Demo complete."
echo "========================================"
