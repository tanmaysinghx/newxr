#!/usr/bin/env bash
# End-to-end smoke test of a fresh install on the embedded H2 database.
#
#   ./smoke-test.sh [path/to/newxr.jar] [port]
#
# Starts the jar on a throwaway Newxr home, runs the setup wizard through the API with sample
# content, signs in, and checks the main endpoint of every app plus the public pages. Exits
# non-zero on the first failure. Needs curl and python3.
set -euo pipefail

JAR=${1:-dist/newxr.jar}
PORT=${2:-18099}
BASE="http://localhost:$PORT"
[ -f "$JAR" ] || { echo "error: $JAR not found — run ./build.sh first" >&2; exit 1; }

WORK=$(mktemp -d -t newxr-smoke.XXXXXX)
HOME_DIR="$WORK/home"
JAR_ABS=$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")
PID=""
cleanup() {
  [ -n "$PID" ] && kill "$PID" 2>/dev/null && wait "$PID" 2>/dev/null || true
  # Only ever the mktemp directory created above.
  case "$WORK" in /tmp/newxr-smoke.*|"${TMPDIR:-/tmp}"/newxr-smoke.*) rm -rf -- "$WORK" ;; esac
}
trap cleanup EXIT

pass=0
ok() { pass=$((pass + 1)); printf '  ok  %s\n' "$1"; }
fail() { printf '  FAIL %s\n' "$1" >&2; echo "--- last 40 log lines ---" >&2; tail -40 "$WORK/newxr.log" >&2; exit 1; }

echo "==> Starting $JAR on port $PORT (home $HOME_DIR)"
java -jar "$JAR_ABS" --httpPort="$PORT" --newxrHome="$HOME_DIR" > "$WORK/newxr.log" 2>&1 &
PID=$!
for _ in $(seq 1 90); do
  curl -sf "$BASE/actuator/health" > /dev/null 2>&1 && break
  kill -0 "$PID" 2>/dev/null || fail "server exited during startup"
  sleep 1
done
curl -sf "$BASE/actuator/health" > /dev/null || fail "server did not become healthy in 90 s"
ok "server healthy"

JAR_COOKIES="$WORK/cookies"
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR_COOKIES" | tail -1; }
get() { curl -s -o "$WORK/body" -w '%{http_code}' -b "$JAR_COOKIES" -c "$JAR_COOKIES" "$BASE$1"; }
post() { curl -s -o "$WORK/body" -w '%{http_code}' -b "$JAR_COOKIES" -c "$JAR_COOKIES" -H "X-XSRF-TOKEN: $(xsrf)" \
  -H 'Content-Type: application/json' -X "${3:-POST}" "$BASE$1" -d "$2"; }
expect() { local code=$1 want=$2 what=$3; [ "$code" = "$want" ] && ok "$what" || { echo "  body: $(head -c 300 "$WORK/body")" >&2; fail "$what (HTTP $code, wanted $want)"; }; }
json() { python3 -c "import json,sys; d=json.load(open('$WORK/body')); $1"; }

echo "==> Setup"
expect "$(get /api/public/setup/status)" 200 "setup status"
json 'assert d["setupRequired"], "setup should be required on a fresh home"' || fail "fresh install reports setup not required"
KEY=$(tr -d '\n' < "$HOME_DIR/secrets/initialSetupKey")
expect "$(post /api/public/setup/initialize "{\"setupKey\":\"$KEY\",\"licenseAccepted\":true,\"licenseId\":\"Apache-2.0\",
  \"admin\":{\"email\":\"admin@smoke.test\",\"name\":\"Smoke Admin\",\"password\":\"Smoke-test-2026\"},
  \"organization\":{\"name\":\"Smoke\"},\"signIn\":{\"builtinEnabled\":true,\"portalEnabled\":false,\"autoProvision\":false},
  \"kafka\":{\"enabled\":false},\"sampleData\":true}")" 200 "setup wizard (with sample content)"
[ ! -f "$HOME_DIR/secrets/initialSetupKey" ] && ok "setup key removed" || fail "setup key still on disk"
expect "$(get /api/public/setup/status)" 200 "setup status after"
json 'assert not d["setupRequired"]' || fail "setup still required"

echo "==> Sign-in"
expect "$(post /api/public/auth/login '{"email":"admin@smoke.test","password":"wrong-Password-1"}')" 401 "wrong password refused"
expect "$(post /api/public/auth/login '{"email":"admin@smoke.test","password":"Smoke-test-2026"}')" 200 "admin signs in"
json 'assert d["role"] == "ADMIN" and len(d["apps"]) == 7, d' || fail "admin should see 7 apps"

echo "==> Apps"
check() { expect "$(get "$1")" 200 "$2"; [ -n "${3:-}" ] && { json "$3" || fail "$2: unexpected content"; }; return 0; }
check /api/hub/home "home" ""
check "/api/hub/search?q=offline" "global search" 'assert d["total"] > 0'
check /api/track/projects "track projects" 'assert any(p["key"] == "ORB" for p in d)'
check /api/track/issues/ORB-387 "track issue" 'assert d["key"] == "ORB-387"'
check /api/pages/spaces "pages spaces" 'assert len(d) > 0'
check /api/boards "boards" 'assert len(d) > 0'
check /api/goals "goals" 'assert len(d) > 0'
check /api/desk/desks/HELP/requests "desk queue" 'assert len(d) > 0'
check /api/desk/help "help center" 'assert len(d["desks"]) == 4'
check /api/pulse/teams/OPS/alerts "pulse alerts" 'assert len(d) > 0'
check /api/pulse/teams/OPS/oncall "pulse on-call" 'assert d["current"] is not None'
check /api/code/repos/orbit-mobile/pulls/284 "code pull request" 'assert d["files"] and d["mergeable"]'
check /api/code/linked/ORB-387 "track ↔ code link" 'assert any(l["kind"] == "pr" for l in d)'
check /api/notifications "notifications" ""
check /api/admin/overview "admin overview" 'assert d["admins"] == 1'
check /api/admin/audit "audit log" 'assert any(e["action"] == "signin.password" for e in d)'

echo "==> Public pages"
expect "$(curl -s -o "$WORK/body" -w '%{http_code}' "$BASE/api/public/status/orbit")" 200 "public status page (no session)"
expect "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/track/projects")" 401 "API refuses anonymous calls"
expect "$(curl -s -o "$WORK/body" -w '%{http_code}' "$BASE/")" 200 "console served"
grep -q "<nx-root\|<app-root" "$WORK/body" && ok "console HTML" || fail "console HTML missing"

if grep -qiE "^\S+ +ERROR|Exception in thread" "$WORK/newxr.log"; then
  grep -iE "^\S+ +ERROR" "$WORK/newxr.log" | head -5 >&2
  fail "errors in the server log"
fi
ok "no errors in the server log"

echo "==> All $pass checks passed"
