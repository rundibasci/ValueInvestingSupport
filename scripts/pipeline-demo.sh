#!/usr/bin/env bash
# pipeline-demo.sh — Score Demo: Seed → Valuate → Score → Rank
#
# Runs the full pipeline for a list of tickers and prints a ranked table
# by Value Score. Shareable with stakeholders as a standalone validation script.
#
# Prerequisites:
#   Application running with MARKET_DATA_SOURCE=fmp and a valid FMP_API_KEY.
#   An admin user must exist (email: admin@example.com, password: Admin1234!).
#   jq must be installed (brew install jq / apt install jq).
#
# Usage:
#   chmod +x scripts/pipeline-demo.sh
#   ./scripts/pipeline-demo.sh [BASE_URL] [ADMIN_EMAIL] [ADMIN_PASSWORD] [TICKERS]
#
# Examples:
#   ./scripts/pipeline-demo.sh
#   ./scripts/pipeline-demo.sh http://localhost:8080
#   ./scripts/pipeline-demo.sh http://localhost:8080 me@co.com Pass1! AAPL,MSFT,KO,JNJ

set -euo pipefail

BASE="${1:-http://localhost:8080}"
ADMIN_EMAIL="${2:-admin@example.com}"
ADMIN_PASSWORD="${3:-Admin1234!}"
TICKERS="${4:-${SEED_TICKERS:-AAPL,MSFT,KO,JNJ}}"

check_status() {
    local status="$1" label="$2"
    if [ "$status" -lt 200 ] || [ "$status" -ge 300 ]; then
        echo "ERROR: $label returned HTTP $status" >&2
        exit 1
    fi
}

echo "========================================"
echo " Score Demo — Value Investing Platform"
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
echo "=== Step 2: Run pipeline (seed → valuate → score) for [$TICKERS] ==="
TICKERS_JSON=$(echo "$TICKERS" | jq -R '[split(",")[] | ltrimstr(" ") | rtrimstr(" ")]')
PIPELINE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BASE/api/v1/admin/pipeline-run" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"tickers\": $TICKERS_JSON}")
PIPELINE_BODY=$(echo "$PIPELINE_RESPONSE" | head -n -1)
PIPELINE_STATUS=$(echo "$PIPELINE_RESPONSE" | tail -n 1)
check_status "$PIPELINE_STATUS" "POST /api/v1/admin/pipeline-run"
echo ""

# ------------------------------------------------------------------
echo "=== Step 3: Ranked results (by Value Score DESC) ==="
echo ""
printf "%-6s  %-28s  %10s  %8s  %7s  %s\n" \
    "Symbol" "Company" "FairValue" "MoS%" "Score" "Recommendation"
printf "%-6s  %-28s  %10s  %8s  %7s  %s\n" \
    "------" "-------" "---------" "----" "-----" "--------------"
echo "$PIPELINE_BODY" | jq -r '.[] | [
  .symbol,
  ((.companyName // "-") | .[0:28]),
  (.compositeFairValue | if . then (. * 100 | round / 100 | tostring) else "N/A" end),
  (.marginOfSafety    | if . then (. * 100 | round / 100 | tostring) + "%" else "N/A" end),
  (.totalScore        | if . then (. | tostring) else "N/A" end),
  (.recommendation    // ("ERROR: " + (.error // "unknown")))
] | @tsv' | while IFS=$'\t' read -r sym name fv mos score rec; do
  printf "%-6s  %-28s  %10s  %8s  %7s  %s\n" "$sym" "$name" "$fv" "$mos" "$score" "$rec"
done
echo ""

# ------------------------------------------------------------------
echo "=== Step 4: Logout ==="
if [ -n "$REFRESH_TOKEN" ]; then
    LOGOUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/logout" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
    echo "Logged out (HTTP $LOGOUT_STATUS)"
else
    echo "No refresh token captured — skipping logout."
fi
echo ""
echo "========================================"
echo " Pipeline Demo Complete"
echo "========================================"
