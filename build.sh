#!/usr/bin/env sh
# Builds the single deliverable: dist/newxr.jar (Angular console + Spring Boot server).
# Needs Node.js 20+ and JDK 25. Usage: ./build.sh [--skip-tests]
set -eu

ROOT=$(cd "$(dirname "$0")" && pwd)
GRADLE_ARGS="bootJar"
case "${1:-}" in
  --skip-tests) ;;
  "") GRADLE_ARGS="test bootJar" ;;
  *) echo "Usage: $0 [--skip-tests]" >&2; exit 2 ;;
esac

need() { command -v "$1" >/dev/null 2>&1 || { echo "error: $1 is required ($2)" >&2; exit 1; }; }
need node "Node.js 20 or newer"
need npm "comes with Node.js"
need java "JDK 25"

echo "==> Frontend (newxr-frontend)"
cd "$ROOT/newxr-frontend"
[ -d node_modules ] || npm ci
npx ng build

echo "==> Backend (newxr-backend)"
cd "$ROOT/newxr-backend"
# shellcheck disable=SC2086
./gradlew --no-daemon $GRADLE_ARGS

mkdir -p "$ROOT/dist"
cp "$ROOT/newxr-backend/build/libs/newxr.jar" "$ROOT/dist/newxr.jar"
echo "==> Built dist/newxr.jar ($(du -h "$ROOT/dist/newxr.jar" | cut -f1))"
echo "    Run it with: java -jar dist/newxr.jar --httpPort=8080"
