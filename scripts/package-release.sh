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
  mvn -q -DskipTests clean package
else
  mvn -q clean package
fi

JAR="$ROOT/backend/target/${ARTIFACT}-${VERSION}.jar"
test -f "$JAR"

rm -rf "$STAGE"
mkdir -p "$STAGE"
cp "$JAR" "$STAGE/"
cp "$ROOT/scripts/release-start.sh" "$STAGE/start.sh"
cp "$ROOT/scripts/release-start.command" "$STAGE/start.command"
cp "$ROOT/scripts/release-start.bat" "$STAGE/start.bat"
cp "$ROOT/scripts/release-smoke.sh" "$STAGE/smoke.sh"
cp "$ROOT/scripts/release-README.txt" "$STAGE/README.txt"
chmod +x "$STAGE/start.sh" "$STAGE/start.command" "$STAGE/smoke.sh" || true

zip_portable() {
  local out="$DIST/${NAME}-${VERSION}.zip"
  rm -f "$out"
  if command -v zip >/dev/null 2>&1; then
    (cd "$DIST" && zip -qr "${NAME}-${VERSION}.zip" "${NAME}-${VERSION}")
  else
    python3 - "$STAGE" "$out" <<'PY'
import sys, zipfile, pathlib
src, dest = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
    for p in src.rglob("*"):
        if p.is_file():
            z.write(p, p.relative_to(src.parent))
PY
  fi
}
zip_portable
echo "PACKAGED $DIST/${NAME}-${VERSION}.zip  (portable / server JAR)"

if [[ "${SKIP_NATIVE:-0}" != "1" ]]; then
  bash "$ROOT/scripts/package-native.sh" || {
    if [[ "${REQUIRE_NATIVE:-0}" == "1" ]]; then
      echo "native package failed"
      exit 1
    fi
    echo "WARN: native package skipped (jpackage 不可用或失败)。便携 zip 仍可用。"
  }
fi
