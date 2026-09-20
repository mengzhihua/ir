#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="ir"
VERSION="1.0.0"
ARTIFACT="ir-backend"
DIST="$ROOT/release"
STAGE="$DIST/${NAME}-${VERSION}"
STATIC="$ROOT/backend/src/main/resources/static"

cleanup_static() {
  rm -rf "$STATIC"
}
trap cleanup_static EXIT

cd "$ROOT/frontend"
if [[ -f package-lock.json ]]; then
  npm ci --no-audit --no-fund
else
  npm install --no-audit --no-fund
fi
npm run build

rm -rf "$STATIC"
mkdir -p "$STATIC"
cp -R "$ROOT/frontend/dist/." "$STATIC/"

cd "$ROOT/backend"
if [[ "${SKIP_TESTS:-0}" == "1" ]]; then
  mvn -q -DskipTests package
else
  mvn -q package
fi

JAR="$ROOT/backend/target/${ARTIFACT}-${VERSION}.jar"
test -f "$JAR"

rm -rf "$STAGE"
mkdir -p "$STAGE"
cp "$JAR" "$STAGE/"
cp "$ROOT/scripts/release-start.sh" "$STAGE/start.sh"
cp "$ROOT/scripts/release-smoke.sh" "$STAGE/smoke.sh"
cp "$ROOT/scripts/release-README.txt" "$STAGE/README.txt"
chmod +x "$STAGE/start.sh" "$STAGE/smoke.sh"

cd "$DIST"
rm -f "${NAME}-${VERSION}.zip"
zip -qr "${NAME}-${VERSION}.zip" "${NAME}-${VERSION}"
echo "PACKAGED $DIST/${NAME}-${VERSION}.zip"
