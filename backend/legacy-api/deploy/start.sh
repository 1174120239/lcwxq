#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt}"
JAR_PATH="${JAR_PATH:-${APP_DIR}/StarFreeApi.jar}"
CONFIG_PATH="${CONFIG_PATH:-${APP_DIR}/application.properties}"
JAVA_BIN="${JAVA_BIN:-/opt/jdk1.8.0_311/bin/java}"

if [[ ! -x "$JAVA_BIN" ]]; then
    JAVA_BIN="$(command -v java || true)"
fi

if [[ -z "$JAVA_BIN" || ! -x "$JAVA_BIN" ]]; then
    echo "Java runtime was not found. Install Java 8+ or set JAVA_BIN." >&2
    exit 1
fi
if [[ ! -r "$JAR_PATH" ]]; then
    echo "Legacy JAR is not readable: $JAR_PATH" >&2
    exit 1
fi
if [[ ! -r "$CONFIG_PATH" ]]; then
    echo "Legacy configuration is not readable: $CONFIG_PATH" >&2
    exit 1
fi
if grep -q 'CHANGE_ME' "$CONFIG_PATH"; then
    echo "Legacy configuration still contains CHANGE_ME placeholders." >&2
    exit 1
fi

exec "$JAVA_BIN" \
    -Dfile.encoding=UTF-8 \
    -jar "$JAR_PATH" \
    --server.address=127.0.0.1 \
    --spring.config.location="file:${CONFIG_PATH}"
