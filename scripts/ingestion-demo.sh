#!/usr/bin/env bash
# ingestion-demo.sh — Phase B3: Data Ingestion Jobs manual demo
#
# Prerequisites:
#   docker compose up -d
#   FMP_API_KEY set in environment or .env
#   Application running on localhost:8080 with MARKET_DATA_SOURCE=fmp
#
# Usage:
#   chmod +x scripts/ingestion-demo.sh
#   ./scripts/ingestion-demo.sh

set -euo pipefail

BASE="http://localhost:8080"

echo "=== Step 1: Login as admin ==="
TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
echo "Token acquired."

AUTH="-H \"Authorization: Bearer $TOKEN\""

echo ""
echo "=== Step 2: Trigger bulk-profile-sync ==="
curl -s -X POST "$BASE/api/v1/admin/jobs/bulk-profile-sync/run" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
echo "(Job running async — waiting 30s...)"
sleep 30

echo ""
echo "=== Step 3: Check health indicator ==="
curl -s "$BASE/actuator/health" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool | grep -A 40 '"ingestionJobs"'

echo ""
echo "=== Step 4: Trigger quote-refresh ==="
curl -s -X POST "$BASE/api/v1/admin/jobs/quote-refresh/run" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
echo "(Job running async — waiting 10s...)"
sleep 10

echo ""
echo "=== Step 5: Check health again ==="
HEALTH=$(curl -s "$BASE/actuator/health" -H "Authorization: Bearer $TOKEN")
echo "$HEALTH" | python3 -m json.tool | grep -A 30 '"ingestionJobs"'

echo ""
echo "=== Done. Copy the health output above into the PR description. ==="
