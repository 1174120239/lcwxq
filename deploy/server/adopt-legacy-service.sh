#!/usr/bin/env bash
set -euo pipefail

START_SCRIPT=
UNIT_FILE=
EXPECTED_JAR_SHA256=c2daa75c2c6a2968bea2d72783fc4a6844c666306daeacdf936e31dc9cb89c26
TARGET_START=/opt/starfree-legacy-start.sh
TARGET_UNIT=/etc/systemd/system/starfree-legacy.service
BACKUP=/srv/lcxqy/backups/$(date +%Y%m%d-%H%M%S)-legacy-service-adoption
ORIGINAL_PID=
COMPLETED=false

usage() {
    echo "Usage: $0 --start-script FILE --unit-file FILE"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --start-script) START_SCRIPT=${2:-}; shift 2 ;;
        --unit-file) UNIT_FILE=${2:-}; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

[[ "$(id -u)" -eq 0 ]] || { echo 'Run as root.' >&2; exit 1; }
[[ -r "$START_SCRIPT" && -r "$UNIT_FILE" ]] || {
    echo 'The reviewed start script and systemd unit are required.' >&2
    exit 2
}
bash -n "$START_SCRIPT"
grep -Fxq 'ExecStart=/opt/starfree-legacy-start.sh' "$UNIT_FILE" || {
    echo 'Unexpected legacy systemd ExecStart.' >&2
    exit 2
}
[[ -r /opt/StarFreeApi.jar && -r /opt/application.properties ]] || {
    echo 'Existing legacy JAR or runtime configuration is missing.' >&2
    exit 2
}
actual_jar_sha256=$(sha256sum /opt/StarFreeApi.jar | awk '{print $1}')
[[ "$actual_jar_sha256" == "$EXPECTED_JAR_SHA256" ]] || {
    echo "Existing legacy JAR SHA-256 mismatch: $actual_jar_sha256" >&2
    exit 3
}

legacy_health() {
    curl -fsS --max-time 15 http://127.0.0.1:8081/ >/dev/null
}

wait_for_legacy() {
    local deadline=$((SECONDS + 60))
    while (( SECONDS < deadline )); do
        if systemctl is-active --quiet starfree-legacy.service && legacy_health; then
            return 0
        fi
        sleep 2
    done
    systemctl is-active --quiet starfree-legacy.service && legacy_health
}

restore_original_process() {
    systemctl disable --now starfree-legacy.service >/dev/null 2>&1 || true
    if [[ -f "$BACKUP/starfree-legacy-start.sh" ]]; then
        install -m 0755 "$BACKUP/starfree-legacy-start.sh" "$TARGET_START"
    else
        rm -f "$TARGET_START"
    fi
    if [[ -f "$BACKUP/starfree-legacy.service" ]]; then
        install -m 0644 "$BACKUP/starfree-legacy.service" "$TARGET_UNIT"
    else
        rm -f "$TARGET_UNIT"
    fi
    systemctl daemon-reload || true
    if ! legacy_health; then
        mkdir -p /var/log/lcxqy
        (
            cd /opt
            nohup /opt/jdk1.8.0_311/bin/java -jar StarFreeApi.jar \
                >>/var/log/lcxqy/starfree-legacy-manual-rollback.log 2>&1 &
        )
        local deadline=$((SECONDS + 60))
        while (( SECONDS < deadline )); do
            legacy_health && return 0
            sleep 2
        done
        legacy_health
    fi
}

cleanup() {
    if [[ "$COMPLETED" != true ]]; then
        echo 'Legacy service adoption failed; restoring the previous process model.' >&2
        restore_original_process || echo 'Legacy process restoration failed.' >&2
    fi
}

if systemctl is-active --quiet starfree-legacy.service; then
    legacy_health
    COMPLETED=true
    echo 'legacy_service=already_managed'
    echo "legacy_jar_sha256=$actual_jar_sha256"
    exit 0
fi

mapfile -t legacy_pids < <(pgrep -f '^java -jar StarFreeApi\.jar$' || true)
[[ ${#legacy_pids[@]} -eq 1 ]] || {
    echo "Expected one exact unmanaged legacy process, found ${#legacy_pids[@]}." >&2
    exit 3
}
ORIGINAL_PID=${legacy_pids[0]}
legacy_health

install -d -m 0700 "$BACKUP"
cp -p /opt/StarFreeApi.jar "$BACKUP/StarFreeApi.jar"
sha256sum "$BACKUP/StarFreeApi.jar" > "$BACKUP/StarFreeApi.jar.sha256"
ps -p "$ORIGINAL_PID" -o pid=,ppid=,lstart=,args= > "$BACKUP/original-process.txt"
if [[ -f "$TARGET_START" ]]; then cp -p "$TARGET_START" "$BACKUP/starfree-legacy-start.sh"; fi
if [[ -f "$TARGET_UNIT" ]]; then cp -p "$TARGET_UNIT" "$BACKUP/starfree-legacy.service"; fi
trap cleanup EXIT

install -m 0755 "$START_SCRIPT" "$TARGET_START"
install -m 0644 "$UNIT_FILE" "$TARGET_UNIT"
systemctl daemon-reload

kill -TERM "$ORIGINAL_PID"
deadline=$((SECONDS + 30))
while kill -0 "$ORIGINAL_PID" 2>/dev/null && (( SECONDS < deadline )); do sleep 1; done
if kill -0 "$ORIGINAL_PID" 2>/dev/null; then
    echo 'The unmanaged legacy process did not stop after SIGTERM.' >&2
    exit 20
fi

systemctl enable --now starfree-legacy.service
wait_for_legacy
COMPLETED=true
echo 'legacy_service=adopted'
echo "legacy_service_backup=$BACKUP"
echo "legacy_jar_sha256=$actual_jar_sha256"
echo 'legacy_health=ok'
