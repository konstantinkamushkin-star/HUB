#!/usr/bin/env bash

set -euo pipefail

API_BASE_URL="${1:-http://127.0.0.1:3000}"
WEB_BASE_URL="${2:-$API_BASE_URL}"

echo "==> Post-deploy smoke checks"
echo "API base: ${API_BASE_URL}"
echo "Web base: ${WEB_BASE_URL}"

fail() {
  echo "❌ $1" >&2
  exit 1
}

check_json_status() {
  local url="$1"
  local expected="$2"
  local body
  body="$(curl -fsS "$url")" || fail "Request failed: $url"
  python3 -c '
import json, sys
raw, expected = sys.argv[1], sys.argv[2]
try:
    parsed = json.loads(raw)
except Exception:
    sys.exit(2)
if str(parsed.get("status", "")).lower() != expected.lower():
    sys.exit(1)
' "$body" "$expected" || fail "Unexpected JSON status for $url"
}

check_ping_endpoint() {
  local url="$1"
  local body
  body="$(curl -fsS "$url")" || fail "Request failed: $url"
  python3 -c '
import json, sys
raw = sys.argv[1]
try:
    parsed = json.loads(raw)
except Exception:
    sys.exit(2)
ok = any(str(v).lower() in ("pong", "ok", "healthy") for v in parsed.values())
sys.exit(0 if ok else 1)
' "$body" || fail "Ping endpoint did not return an expected payload: $url"
}

check_html_page() {
  local url="$1"
  local headers
  headers="$(curl -fsSI "$url")" || fail "Request failed: $url"
  echo "$headers" | python3 -c '
import sys
headers = sys.stdin.read().lower()
if " 200 " not in headers and not headers.startswith("http/2 200") and not headers.startswith("http/1.1 200"):
    sys.exit(1)
if "content-type:" not in headers or "text/html" not in headers:
    sys.exit(2)
' || fail "Expected 200 text/html for $url"
}

check_json_status "${API_BASE_URL}/api/health" "healthy"
check_ping_endpoint "${API_BASE_URL}/api/dive-sites/ping"
check_html_page "${WEB_BASE_URL}/privacy"
check_html_page "${WEB_BASE_URL}/agreement"

echo "✅ Post-deploy smoke checks passed"
