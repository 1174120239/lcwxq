#!/usr/bin/env bash
set -euo pipefail

# The only production entrypoint used by the local script and GitHub Actions.
# It accepts a checksum-verified archive, stores a release record, creates a
# component backup, and restores the previous artifact after a failed health check.

REMOTE_ROOT=/srv/lcxqy
ARCHIVE=
EXPECTED_SHA256=
COMPONENT=
VERIFY_ONLY=false

usage() {
    echo "Usage: $0 --archive FILE --expected-sha256 HASH [--remote-root DIR]"
    echo "       $0 --verify --component COMPONENT"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --archive) ARCHIVE=${2:-}; shift 2 ;;
        --expected-sha256) EXPECTED_SHA256=${2:-}; shift 2 ;;
        --remote-root) REMOTE_ROOT=${2:-}; shift 2 ;;
        --component) COMPONENT=${2:-}; shift 2 ;;
        --verify) VERIFY_ONLY=true; shift ;;
        --run-migrations)
            echo 'Database migrations are not part of the generic deploy entrypoint.' >&2
            echo 'Use the reviewed migration script from backend/deploy/production separately.' >&2
            exit 30
            ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

[[ "$(id -u)" -eq 0 ]] || { echo 'Run as root or through a narrowly scoped sudo rule.' >&2; exit 1; }
[[ "$REMOTE_ROOT" == /srv/lcxqy ]] || { echo 'Remote root must be /srv/lcxqy.' >&2; exit 2; }

health_check_once() {
    case "$1" in
        replacement-backend) curl -fsS --max-time 15 http://127.0.0.1:18082/health >/dev/null ;;
        legacy-api) curl -fsS --max-time 15 http://127.0.0.1:8081/ >/dev/null ;;
        admin) curl -fsS --max-time 20 "${ADMIN_HEALTH_URL:-https://admin.lcxqy.cn/}" >/dev/null ;;
        *) echo "Unknown component: $1" >&2; return 2 ;;
    esac
}

service_check() {
    case "$1" in
        replacement-backend) systemctl is-active --quiet starfree-replacement.service ;;
        legacy-api) systemctl is-active --quiet starfree-legacy.service ;;
        admin) return 0 ;;
        *) return 2 ;;
    esac
}

wait_for_component() {
    local component="$1"
    local attempts=12
    local delay_seconds=5
    local attempt
    for ((attempt=1; attempt<=attempts; attempt++)); do
        if service_check "$component" && health_check_once "$component"; then
            return 0
        fi
        if (( attempt < attempts )); then
            sleep "$delay_seconds"
        fi
    done
    echo "Component did not become healthy within $((attempts * delay_seconds)) seconds: $component" >&2
    return 1
}

verify_component() {
    local component="$1"
    wait_for_component "$component"
    echo "component=$component"
    case "$component" in
        replacement-backend) sha256sum /opt/starfree-replacement/starfree-replacement.jar ;;
        legacy-api) sha256sum /opt/StarFreeApi.jar ;;
        admin) echo 'admin_target=/www/wwwroot/admin.lcxqy.cn' ;;
    esac
    echo 'health=ok'
}

if [[ "$VERIFY_ONLY" == true ]]; then
    [[ "$COMPONENT" =~ ^(replacement-backend|legacy-api|admin)$ ]] || {
        echo '--component is required with --verify' >&2; exit 2;
    }
    verify_component "$COMPONENT"
    exit 0
fi

[[ -r "$ARCHIVE" ]] || { echo "Release archive is not readable: $ARCHIVE" >&2; exit 2; }
[[ "$EXPECTED_SHA256" =~ ^[0-9a-fA-F]{64}$ ]] || { echo 'Invalid expected SHA-256.' >&2; exit 2; }
actual_sha256=$(sha256sum "$ARCHIVE" | awk '{print $1}')
[[ "$actual_sha256" == "${EXPECTED_SHA256,,}" ]] || { echo 'Release archive checksum mismatch.' >&2; exit 20; }

mkdir -p "$REMOTE_ROOT/releases" "$REMOTE_ROOT/backups" "$REMOTE_ROOT/current"
incoming=$(mktemp -d "$REMOTE_ROOT/.incoming.XXXXXX")
cleanup() { rm -rf "$incoming"; }
trap cleanup EXIT
if tar -tzf "$ARCHIVE" | grep -E '(^/|(^|/)\.\.(/|$))' >/dev/null; then
    echo 'Unsafe path in release archive.' >&2
    exit 21
fi
tar --no-same-owner --no-same-permissions -xzf "$ARCHIVE" -C "$incoming"
if find "$incoming" -type l -print -quit | grep -q .; then
    echo 'Symbolic links are not allowed in a release archive.' >&2
    exit 21
fi
[[ -r "$incoming/manifest.env" ]] || { echo 'manifest.env is missing.' >&2; exit 21; }
if grep -Ev '^(COMPONENT=(replacement-backend|legacy-api|admin|all)|COMMIT=[0-9a-f]{40}|CREATED_UTC=[0-9T:.+Z-]+)$' "$incoming/manifest.env" | grep -q .; then
    echo 'manifest.env contains an unsupported line.' >&2
    exit 21
fi
grep -Eq '^COMPONENT=(replacement-backend|legacy-api|admin|all)$' "$incoming/manifest.env" || { echo 'Invalid manifest component.' >&2; exit 21; }
grep -Eq '^COMMIT=[0-9a-f]{40}$' "$incoming/manifest.env" || { echo 'Invalid manifest commit.' >&2; exit 21; }
COMPONENT=$(sed -n 's/^COMPONENT=//p' "$incoming/manifest.env")
COMMIT=$(sed -n 's/^COMMIT=//p' "$incoming/manifest.env")
[[ $(grep -c '^COMPONENT=' "$incoming/manifest.env") -eq 1 ]] || { echo 'Duplicate component in manifest.' >&2; exit 21; }
[[ $(grep -c '^COMMIT=' "$incoming/manifest.env") -eq 1 ]] || { echo 'Duplicate commit in manifest.' >&2; exit 21; }

case "$COMPONENT" in
    replacement-backend|legacy-api|admin|all) ;;
    *) echo "Unsupported component: $COMPONENT" >&2; exit 22 ;;
esac
release_dir="$REMOTE_ROOT/releases/$COMMIT/$COMPONENT"
backup_dir="$REMOTE_ROOT/backups/$(date +%Y%m%d-%H%M%S)-$COMMIT-$COMPONENT"
mkdir -p "$release_dir" "$backup_dir"
cp -p "$ARCHIVE" "$release_dir/release.tgz"
printf '%s  release.tgz\n' "$actual_sha256" > "$release_dir/SHA256SUMS"

rollback_component() {
    local component="$1"
    case "$component" in
        replacement-backend)
            if [[ -f "$backup_dir/starfree-replacement.jar" ]]; then
                install -m 0644 "$backup_dir/starfree-replacement.jar" /opt/starfree-replacement/starfree-replacement.jar
                systemctl restart starfree-replacement.service || true
            elif [[ -f "$backup_dir/replacement-backend.no-previous" ]]; then
                rm -f /opt/starfree-replacement/starfree-replacement.jar
                systemctl stop starfree-replacement.service || true
            fi
            ;;
        legacy-api)
            if [[ -f "$backup_dir/StarFreeApi.jar" ]]; then
                install -m 0644 "$backup_dir/StarFreeApi.jar" /opt/StarFreeApi.jar
                systemctl restart starfree-legacy.service || true
            elif [[ -f "$backup_dir/legacy-api.no-previous" ]]; then
                rm -f /opt/StarFreeApi.jar
                systemctl stop starfree-legacy.service || true
            fi
            ;;
        admin)
            if [[ -f "$backup_dir/admin.tar.gz" ]]; then
                rm -rf /www/wwwroot/admin.lcxqy.cn
                mkdir -p /www/wwwroot
                tar -xzf "$backup_dir/admin.tar.gz" -C /www/wwwroot
            elif [[ -f "$backup_dir/admin.no-previous" ]]; then
                rm -rf /www/wwwroot/admin.lcxqy.cn
            fi
            ;;
    esac
}

backup_current_link() {
    local component="$1"
    local link="$REMOTE_ROOT/current/$component"
    if [[ -L "$link" ]]; then
        readlink "$link" > "$backup_dir/current-$component.link"
    else
        touch "$backup_dir/current-$component.no-previous"
    fi
}

restore_current_link() {
    local component="$1"
    local link="$REMOTE_ROOT/current/$component"
    if [[ -f "$backup_dir/current-$component.link" ]]; then
        ln -sfn "$(<"$backup_dir/current-$component.link")" "$link"
    elif [[ -f "$backup_dir/current-$component.no-previous" ]]; then
        rm -f "$link"
    fi
}

deploy_replacement() {
    local target=/opt/starfree-replacement/starfree-replacement.jar
    mkdir -p /opt/starfree-replacement || return 20
    if [[ -f "$target" ]]; then
        cp -p "$target" "$backup_dir/starfree-replacement.jar" || return 20
    else
        touch "$backup_dir/replacement-backend.no-previous" || return 20
    fi
    if ! install -m 0644 "$incoming/replacement-backend.jar" "$target"; then
        rollback_component replacement-backend
        return 20
    fi
    if ! systemctl restart starfree-replacement.service; then
        rollback_component replacement-backend
        return 20
    fi
    if ! wait_for_component replacement-backend; then
        rollback_component replacement-backend
        return 21
    fi
}

deploy_legacy() {
    local target=/opt/StarFreeApi.jar
    if [[ -f "$target" ]]; then
        cp -p "$target" "$backup_dir/StarFreeApi.jar" || return 20
    else
        touch "$backup_dir/legacy-api.no-previous" || return 20
    fi
    if ! install -m 0644 "$incoming/legacy-api.jar" "$target"; then
        rollback_component legacy-api
        return 20
    fi
    if ! systemctl restart starfree-legacy.service; then
        rollback_component legacy-api
        return 20
    fi
    if ! wait_for_component legacy-api; then
        rollback_component legacy-api
        return 21
    fi
}

deploy_admin() {
    local target=/www/wwwroot/admin.lcxqy.cn
    if [[ -d "$target" ]]; then
        tar -czf "$backup_dir/admin.tar.gz" -C /www/wwwroot "$(basename "$target")" || return 20
    else
        touch "$backup_dir/admin.no-previous" || return 20
    fi
    if tar -tzf "$incoming/admin.tar.gz" | grep -E '(^/|(^|/)\.\.(/|$))' >/dev/null; then
        echo 'Unsafe path in admin archive.' >&2
        return 23
    fi
    tar --no-same-owner --no-same-permissions -xzf "$incoming/admin.tar.gz" -C "$incoming" || return 23
    if find "$incoming/starfree-admin" -type l -print -quit | grep -q .; then
        echo 'Symbolic links are not allowed in the admin package.' >&2
        return 23
    fi
    if ! (cd "$incoming/starfree-admin" && TARGET_DIR="$target" bash deploy/install.sh); then
        rollback_component admin
        return 20
    fi
    if ! wait_for_component admin; then
        rollback_component admin
        return 21
    fi
}

deployed=()
deploy_one() {
    local component="$1"
    backup_current_link "$component" || return 20
    case "$component" in
        replacement-backend) [[ -f "$incoming/replacement-backend.jar" ]] || { echo 'Replacement JAR missing.' >&2; return 23; }; deploy_replacement ;;
        legacy-api) [[ -f "$incoming/legacy-api.jar" ]] || { echo 'Legacy JAR missing.' >&2; return 23; }; deploy_legacy ;;
        admin) [[ -f "$incoming/admin.tar.gz" ]] || { echo 'Admin archive missing.' >&2; return 23; }; deploy_admin ;;
    esac
    ln -sfn "$release_dir" "$REMOTE_ROOT/current/$component" || return 20
    deployed+=("$component")
}

if [[ "$COMPONENT" == all ]]; then
    for item in replacement-backend legacy-api admin; do
        if ! deploy_one "$item"; then
            for ((i=${#deployed[@]}-1; i>=0; i--)); do
                rollback_component "${deployed[$i]}"
                restore_current_link "${deployed[$i]}"
            done
            echo 'Combined deployment failed and completed components were restored.' >&2
            exit 24
        fi
    done
else
    deploy_one "$COMPONENT"
fi

echo "component=$COMPONENT"
echo "commit=$COMMIT"
echo "archive_sha256=$actual_sha256"
echo "backup=$backup_dir"
for item in "${deployed[@]}"; do verify_component "$item"; done
