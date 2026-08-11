#!/usr/bin/env bash
set -euo pipefail

COMPONENT=
BACKUP=

health_check() {
    case "$1" in
        replacement-backend) curl -fsS --max-time 15 http://127.0.0.1:18082/health >/dev/null ;;
        legacy-api) curl -fsS --max-time 15 http://127.0.0.1:8081/ >/dev/null ;;
        *) return 2 ;;
    esac
}

service_check() {
    case "$1" in
        replacement-backend) systemctl is-active --quiet starfree-replacement.service ;;
        legacy-api) systemctl is-active --quiet starfree-legacy.service ;;
        *) return 2 ;;
    esac
}

wait_for_component() {
    local component="$1"
    local timeout_seconds="${2:-60}"
    local deadline=$((SECONDS + timeout_seconds))
    while (( SECONDS < deadline )); do
        if service_check "$component" && health_check "$component"; then
            return 0
        fi
        sleep 2
    done
    service_check "$component" && health_check "$component"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --component) COMPONENT=${2:-}; shift 2 ;;
        --backup) BACKUP=${2:-}; shift 2 ;;
        -h|--help) echo 'Usage: lcxqy-rollback --component COMPONENT --backup BACKUP_DIR'; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

[[ "$(id -u)" -eq 0 ]] || { echo 'Run as root.' >&2; exit 1; }
[[ -d "$BACKUP" ]] || { echo "Backup directory not found: $BACKUP" >&2; exit 2; }
BACKUP=$(realpath -e "$BACKUP")
case "$BACKUP" in /srv/lcxqy/backups/*) ;; *) echo 'Backup must be under /srv/lcxqy/backups.' >&2; exit 2 ;; esac
case "$COMPONENT" in
    replacement-backend)
        [[ -f "$BACKUP/starfree-replacement.jar" ]] || exit 3
        install -m 0644 "$BACKUP/starfree-replacement.jar" /opt/starfree-replacement/starfree-replacement.jar
        systemctl restart starfree-replacement.service
        wait_for_component replacement-backend 60
        ;;
    legacy-api)
        [[ -f "$BACKUP/StarFreeApi.jar" ]] || exit 3
        install -m 0644 "$BACKUP/StarFreeApi.jar" /opt/StarFreeApi.jar
        systemctl restart starfree-legacy.service
        wait_for_component legacy-api 60
        ;;
    admin)
        [[ -f "$BACKUP/admin.tar.gz" ]] || exit 3
        if tar -tzf "$BACKUP/admin.tar.gz" | grep -E '(^/|(^|/)\.\.(/|$))' >/dev/null; then
            echo 'Unsafe path in admin backup.' >&2
            exit 3
        fi
        rm -rf /www/wwwroot/admin.lcxqy.cn
        mkdir -p /www/wwwroot
        tar --no-same-owner --no-same-permissions -xzf "$BACKUP/admin.tar.gz" -C /www/wwwroot
        curl -fsS --max-time 20 "${ADMIN_HEALTH_URL:-https://admin.lcxqy.cn/}" >/dev/null
        ;;
    *) echo "Unsupported component: $COMPONENT" >&2; exit 2 ;;
esac
if [[ -f "$BACKUP/current-$COMPONENT.link" ]]; then
    ln -sfn "$(<"$BACKUP/current-$COMPONENT.link")" "/srv/lcxqy/current/$COMPONENT"
elif [[ -f "$BACKUP/current-$COMPONENT.no-previous" ]]; then
    rm -f "/srv/lcxqy/current/$COMPONENT"
fi
echo "rollback=success component=$COMPONENT backup=$BACKUP"
