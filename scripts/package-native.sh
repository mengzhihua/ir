#!/usr/bin/env bash
# Build a platform-native app-image with bundled JRE via jpackage.
# Linux/macOS/Windows 各自在对应系统上运行（GitHub Actions 矩阵）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="ir"
TITLE="IR 供应链控制塔"
VERSION="1.0.0"
ARTIFACT="ir-backend"
PORT="8090"
DIST="$ROOT/release"
JAR="$ROOT/backend/target/${ARTIFACT}-${VERSION}.jar"
test -f "$JAR"

JP=""
if command -v jpackage >/dev/null 2>&1; then
  JP="jpackage"
elif [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jpackage" ]]; then
  JP="$JAVA_HOME/bin/jpackage"
elif [[ -x "$(dirname "$(command -v java)")/jpackage" ]]; then
  JP="$(dirname "$(command -v java)")/jpackage"
else
  echo "jpackage not found"
  exit 1
fi

UNAME="$(uname -s 2>/dev/null || echo unknown)"
case "$UNAME" in
  Linux*) PLATFORM="linux-x64" ;;
  Darwin*) PLATFORM="macos-x64" ;;
  MINGW*|MSYS*|CYGWIN*|Windows_NT*) PLATFORM="windows-x64" ;;
  *) PLATFORM="unknown" ;;
esac

INPUT="$DIST/native-input"
OUT="$DIST/native-out"
rm -rf "$INPUT" "$OUT"
mkdir -p "$INPUT" "$OUT"
cp "$JAR" "$INPUT/"

ARGS=(
  --type app-image
  --name "$NAME"
  --app-version "$VERSION"
  --description "$TITLE"
  --vendor "Supply Chain"
  --input "$INPUT"
  --main-jar "${ARTIFACT}-${VERSION}.jar"
  --dest "$OUT"
  --java-options "-Dfile.encoding=UTF-8"
  --java-options "-Duser.dir=\$APPDIR"
  --add-modules ALL-MODULE-PATH
)
ARGS+=(--arguments "--server.port=8090")

if [[ "$PLATFORM" == "windows-x64" ]]; then
  ARGS+=(--win-console)
fi

echo "jpackage ${ARGS[*]}"
"$JP" "${ARGS[@]}"

ARCHIVE="$DIST/${NAME}-${VERSION}-${PLATFORM}"
rm -rf "$ARCHIVE"
mkdir -p "$ARCHIVE"
if [[ -d "$OUT/$NAME.app" ]]; then
  cp -R "$OUT/$NAME.app" "$ARCHIVE/"
elif [[ -d "$OUT/$NAME" ]]; then
  cp -R "$OUT/$NAME" "$ARCHIVE/"
else
  cp -R "$OUT"/. "$ARCHIVE/"
fi
cat > "$ARCHIVE/README.txt" <<EOF
$TITLE  native  $VERSION  ($PLATFORM)

本目录已捆绑 Java 运行时，无需再安装 JDK。

Linux:
  ./$NAME/bin/$NAME

Windows:
  双击 $NAME\\\\$NAME.exe

macOS:
  双击 $NAME.app

浏览器打开 http://127.0.0.1:$PORT
EOF

if command -v zip >/dev/null 2>&1; then
  (cd "$DIST" && zip -qr "${NAME}-${VERSION}-${PLATFORM}.zip" "${NAME}-${VERSION}-${PLATFORM}")
else
  python3 - "$ARCHIVE" "$DIST/${NAME}-${VERSION}-${PLATFORM}.zip" <<'PY'
import sys, zipfile, pathlib
src, dest = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as z:
    for p in src.rglob("*"):
        if p.is_file():
            z.write(p, p.relative_to(src.parent))
PY
fi
echo "PACKAGED $DIST/${NAME}-${VERSION}-${PLATFORM}.zip"
