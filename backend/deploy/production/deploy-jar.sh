#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <expected-sha256>" >&2
    exit 2
fi

APP_DIR=/opt/starfree-replacement
SERVICE=starfree-replacement.service
NEW_JAR="$APP_DIR/starfree-replacement.jar.new"
ACTIVE_JAR="$APP_DIR/starfree-replacement.jar"
EXPECTED_SHA256="$1"
BACKUP_JAR="$APP_DIR/starfree-replacement.jar.rollback-$(date +%Y%m%d-%H%M%S)"
HEALTH_URL=http://127.0.0.1:18082/health

actual_sha256="$(sha256sum "$NEW_JAR" | awk '{print $1}')"
echo "uploaded_sha256=$actual_sha256"
if [[ "$actual_sha256" != "$EXPECTED_SHA256" ]]; then
    echo "Uploaded JAR checksum mismatch" >&2
    exit 20
fi

cp -p "$ACTIVE_JAR" "$BACKUP_JAR"
mv "$NEW_JAR" "$ACTIVE_JAR"
systemctl restart "$SERVICE"

healthy=false
for _ in $(seq 1 40); do
    if curl -fsS "$HEALTH_URL" >/tmp/starfree-replacement-health.json 2>/dev/null; then
        healthy=true
        break
    fi
    sleep 1
done

if [[ "$healthy" != true ]]; then
    echo "Deployment health check failed; rolling back to $BACKUP_JAR" >&2
    cp -p "$BACKUP_JAR" "$ACTIVE_JAR"
    systemctl restart "$SERVICE"
    sleep 3
    curl -fsS "$HEALTH_URL"
    exit 21
fi

echo "service_active=$(systemctl is-active "$SERVICE")"
echo "main_pid=$(systemctl show "$SERVICE" -p MainPID | sed 's/^MainPID=//')"
echo "installed_sha256=$(sha256sum "$ACTIVE_JAR" | awk '{print $1}')"
echo "rollback_jar=$BACKUP_JAR"
cat /tmp/starfree-replacement-health.json
echo
