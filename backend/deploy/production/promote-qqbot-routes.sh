#!/usr/bin/env bash
set -euo pipefail

# Promotes the NapCat/AstrBot dynamic assistant endpoints to the replacement
# backend. Run after migration 006 and the replacement JAR are deployed.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
VERIFY_HOST=${VERIFY_HOST:-api.lcxqy.cn}
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$CONF.rollback-qqbot-$STAMP"
HEADER=replacement-qqbot
ROUTES=(
    config
    chat
    bindChallenge
    bindPage
    bindLogin
    meStatus
    signin
    addSpace
    updateProfile
    registerGroup
    latestSpaces
    delivery
    qzoneBatch
    qzoneDelivery
)

for required in cat cp curl grep mktemp nginx printf rm sleep; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

missing_routes=()
existing_count=0
header_total="$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)"
for route in "${ROUTES[@]}"; do
    location_count="$(grep -Fc "location = /SFreeBot/$route {" "$CONF" || true)"
    if [[ "$location_count" -gt 1 ]]; then
        echo "Duplicate QQBot route detected: $route" >&2
        exit 3
    fi
    if [[ "$location_count" == 1 ]]; then
        existing_count=$((existing_count + 1))
    else
        missing_routes+=("$route")
    fi
done
if [[ "$header_total" != "$existing_count" ]]; then
    echo "QQBot route headers do not match existing exact routes." >&2
    exit 3
fi
if [[ "${#missing_routes[@]}" == 0 ]]; then
    echo "QQBot routes are already promoted."
    exit 0
fi

cp -p "$CONF" "$BACKUP"
rollback() {
    echo "QQBot route promotion failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
}

for route in "${missing_routes[@]}"; do
    printf '
location = /SFreeBot/%s {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-qqbot always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}
' "$route" >>"$CONF"
done

for route in "${ROUTES[@]}"; do
    [[ "$(grep -Fc "location = /SFreeBot/$route {" "$CONF" || true)" == 1 ]] || {
        rollback
        exit 4
    }
done
[[ "$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)" == "${#ROUTES[@]}" ]] || {
    rollback
    exit 4
}

if ! nginx -t; then
    rollback
    exit 4
fi
if ! nginx -s reload; then
    rollback
    exit 5
fi

headers="$(mktemp)"
body="$(mktemp)"
cleanup() {
    rm -f "$headers" "$body"
}
trap cleanup EXIT

config_ready=false
for attempt in {1..15}; do
    if curl -skS --resolve "$VERIFY_HOST:443:127.0.0.1" \
        -D "$headers" -o "$body" -X POST "https://$VERIFY_HOST/SFreeBot/config" \
        && grep -Fi "X-Starfree-Backend: $HEADER" "$headers" >/dev/null \
        && grep -F '"code"' "$body" >/dev/null; then
        config_ready=true
        break
    fi
    sleep 1
done
if [[ "$config_ready" != true ]]; then
    echo "QQBot config route header is missing." >&2
    cat "$headers" >&2
    rollback
    exit 6
fi

bind_ready=false
for attempt in {1..15}; do
    if curl -skS --resolve "$VERIFY_HOST:443:127.0.0.1" \
        -D "$headers" -o "$body" "https://$VERIFY_HOST/SFreeBot/bindPage?token=invalid" \
        && grep -Fi "X-Starfree-Backend: $HEADER" "$headers" >/dev/null; then
        bind_ready=true
        break
    fi
    sleep 1
done
if [[ "$bind_ready" != true ]]; then
    echo "QQBot bind page route header is missing." >&2
    cat "$headers" >&2
    rollback
    exit 7
fi

echo "QQBot routes promoted; backup: $BACKUP"
