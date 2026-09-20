#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
PORT="${SERVER_PORT:-8090}"

if ! command -v java >/dev/null 2>&1; then
  echo "未检测到 Java。便携包 / 服务端部署需要 JDK 17："
  echo "  https://adoptium.net/"
  echo "不想安装 Java 时，请使用 GitHub Release 中带运行时的 linux/mac/windows 原生包。"
  exit 1
fi

echo "正在启动 IR 供应链控制塔"
echo "浏览器打开 http://127.0.0.1:$PORT"
echo "服务端也可直接：java -jar ir-backend-1.0.0.jar --server.port=$PORT"

if [[ "${SKIP_BROWSER:-0}" != "1" ]]; then
  (
    sleep 8
    url="http://127.0.0.1:${PORT}"
    if command -v xdg-open >/dev/null 2>&1; then xdg-open "$url" || true
    elif command -v open >/dev/null 2>&1; then open "$url" || true
    fi
  ) >/dev/null 2>&1 &
fi

exec java ${JAVA_OPTS:-} -jar ir-backend-1.0.0.jar --server.port="$PORT"
