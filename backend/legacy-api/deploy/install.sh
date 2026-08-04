#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
APP_DIR="${APP_DIR:-/opt}"
SYSTEMD_DIR="${SYSTEMD_DIR:-/etc/systemd/system}"
EXPECTED_SHA256="c2daa75c2c6a2968bea2d72783fc4a6844c666306daeacdf936e31dc9cb89c26"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

if [[ "$(id -u)" -ne 0 ]]; then
    echo "Run this installer as root." >&2
    exit 1
fi

SOURCE_JAR="${PACKAGE_DIR}/dist/StarFreeApi.jar"
SOURCE_CONFIG="${PACKAGE_DIR}/config/application.example.properties"
TARGET_JAR="${APP_DIR}/StarFreeApi.jar"
TARGET_CONFIG="${APP_DIR}/application.properties"

actual_sha256="$(sha256sum "$SOURCE_JAR" | awk '{print $1}')"
if [[ "$actual_sha256" != "$EXPECTED_SHA256" ]]; then
    echo "JAR SHA-256 mismatch: $actual_sha256" >&2
    exit 1
fi

if pgrep -f 'java .*StarFreeApi\.jar' >/dev/null 2>&1 && ! systemctl is-active --quiet starfree-legacy.service; then
    echo "An unmanaged StarFreeApi.jar process is running." >&2
    echo "Stop the old process before enabling starfree-legacy.service to avoid a port conflict." >&2
    exit 3
fi

install -d -m 0755 "$APP_DIR" /var/log/lcxqy
if [[ -f "$TARGET_JAR" ]]; then
    cp -p "$TARGET_JAR" "${TARGET_JAR}.backup-${TIMESTAMP}"
fi
install -m 0644 "$SOURCE_JAR" "$TARGET_JAR"
install -m 0755 "${SCRIPT_DIR}/start.sh" "${APP_DIR}/starfree-legacy-start.sh"
install -m 0644 "${SCRIPT_DIR}/starfree-legacy.service" "${SYSTEMD_DIR}/starfree-legacy.service"

if [[ ! -f "$TARGET_CONFIG" ]]; then
    install -m 0600 "$SOURCE_CONFIG" "$TARGET_CONFIG"
    echo "Created $TARGET_CONFIG from the safe template."
    echo "Fill every CHANGE_ME value, then run this installer again." >&2
    exit 2
fi
if grep -q 'CHANGE_ME' "$TARGET_CONFIG"; then
    echo "$TARGET_CONFIG still contains CHANGE_ME placeholders." >&2
    exit 2
fi

systemctl daemon-reload
systemctl enable --now starfree-legacy.service
sleep 3
systemctl is-active --quiet starfree-legacy.service
curl -fsS --max-time 10 http://127.0.0.1:8081/ >/dev/null
echo "Legacy API is active on 127.0.0.1:8081."
