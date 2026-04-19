#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RUN_BUILD_CHECKS=true
if [[ "${1:-}" == "--skip-build" ]]; then
  RUN_BUILD_CHECKS=false
fi

echo "==> Pre-release audit started"

fail() {
  echo "❌ $1" >&2
  exit 1
}

check_not_tracked() {
  local pattern="$1"
  local message="$2"
  if [[ -n "$(git ls-files -- "$pattern")" ]]; then
    fail "$message"
  fi
}

echo "==> Checking tracked build artifacts"
check_not_tracked "DiveHubAndroid/.gradle/**" "Tracked Gradle cache detected (DiveHubAndroid/.gradle)."
check_not_tracked "DiveHubAndroid/app/build/**" "Tracked Android build output detected (DiveHubAndroid/app/build)."
check_not_tracked "backend/dist/**" "Tracked backend build output detected (backend/dist)."
check_not_tracked "admin-web/.next/**" "Tracked Next.js build output detected (admin-web/.next)."

echo "==> Checking tracked local secrets"
check_not_tracked "backend/.env" "Tracked backend/.env detected."
check_not_tracked "admin-web/.env.local" "Tracked admin-web/.env.local detected."
check_not_tracked "DiveHub/GoogleService-Info.plist" "Tracked iOS GoogleService-Info.plist detected."

echo "==> Checking forbidden debug endpoints and headers"
if git ls-files \
  | python3 -c '
import re, sys
pattern = re.compile(r"127\.0\.0\.1:1024/ingest|X-Debug-Session-Id")
skip_suffix = (".md",)
skip_contains = ("/.git/", "/node_modules/", "/.next/", "/build/", "/.gradle/", "/agent-transcripts/")
for raw in sys.stdin:
    p = raw.strip()
    if not p:
        continue
    if p == "scripts/pre_release_audit.sh":
        continue
    if p.endswith(skip_suffix) or any(s in p for s in skip_contains):
        continue
    try:
        with open(p, "r", encoding="utf-8", errors="ignore") as f:
            if pattern.search(f.read()):
                print(p)
                sys.exit(0)
    except Exception:
        continue
sys.exit(1)
' >/dev/null; then
  fail "Found forbidden debug telemetry endpoint/header in source files."
fi

if [[ "$RUN_BUILD_CHECKS" == "true" ]]; then
  echo "==> Checking backend and admin-web production builds"
  (cd backend && npm run verify:prod-build)
  (cd admin-web && npm run build)
fi

echo "✅ Pre-release audit passed"
