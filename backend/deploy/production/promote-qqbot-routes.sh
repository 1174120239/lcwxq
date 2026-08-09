#!/usr/bin/env bash
set -euo pipefail

# Promotes the NapCat/AstrBot dynamic assistant endpoints to the replacement
# backend. Run after migration 006 and the replacement JAR are deployed.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
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
)

for required in cat cp curl grep mktemp nginx printf rm; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

complete=true
empty=true
header_total="$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)"
for route in "${ROUTES[@]}"; do
    location_count="$(grep -Fc "location = /SFreeBot/$route {" "$CONF" || true)"
    [[ "$location_count" == 1 ]] || complete=false
    [[ "$location_count" == 0 ]] || empty=false
done
[[ "$header_total" == "${#ROUTES[@]}" ]] || complete=false
[[ "$header_total" == 0 ]] || empty=false

if [[ "$complete" == true ]]; then
    echo "QQBot routes are already promoted."
    exit 0
fi
if [[ "$empty" != true ]]; then
    echo "Partial or duplicate QQBot routes detected." >&2
    exit 3
fi

cp -p "$CONF" "$BACKUP"
rollback() {
    echo "QQBot route promotion failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
}

for route in "${ROUTES[@]}"; do
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

if ! curl -skS -D "$headers" -o "$body" -X POST "$PUBLIC_URL/SFreeBot/config"; then
    rollback
    exit 6
fi
if ! grep -Fi "X-Starfree-Backend: $HEADER" "$headers" >/dev/null; then
    rollback
    exit 7
fi
if ! grep -F '"code"' "$body" >/dev/null; then
    rollback
    exit 8
fi

if ! curl -skS -D "$headers" -o "$body" "$PUBLIC_URL/SFreeBot/bindPage?token=invalid"; then
    rollback
    exit 9
fi
if ! grep -Fi "X-Starfree-Backend: $HEADER" "$headers" >/dev/null; then
    rollback
    exit 10
fi

echo "QQBot routes promoted; backup: $BACKUP"
