#!/usr/bin/env bash
set -euo pipefail

# Exposes the server-side AstrBot OneBot v11 reverse WebSocket endpoint for a
# NapCat instance running on the operator's own computer. The access token is
# stored outside the repository and validated by AstrBot, not by Nginx.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
VERIFY_HOST=${VERIFY_HOST:-api.lcxqy.cn}
SECRETS_FILE=${SECRETS_FILE:-/srv/lcxqy/qqbot/secrets.env}
ROUTE=/onebot/v11/ws
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$CONF.rollback-onebot-$STAMP"
HEADER=onebot-v11

for required in awk cp curl grep mktemp nginx rm sleep; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }
[[ -f "$SECRETS_FILE" ]] || { echo "QQBot secrets file missing: $SECRETS_FILE" >&2; exit 2; }

set -a
source "$SECRETS_FILE"
set +a
[[ "${LCXQY_ONEBOT_TOKEN:-}" =~ ^[0-9a-f]{64}$ ]] || {
    echo "OneBot token is missing or invalid." >&2
    exit 2
}

location_count="$(grep -Fc "location = $ROUTE {" "$CONF" || true)"
header_count="$(grep -Fc "add_header X-LCXQY-Transport $HEADER always;" "$CONF" || true)"
if [[ "$location_count" == 1 && "$header_count" == 1 ]]; then
    already_present=true
elif [[ "$location_count" == 0 && "$header_count" == 0 ]]; then
    already_present=false
else
    echo "Partial or duplicate OneBot route detected." >&2
    exit 3
fi

if [[ "$already_present" == false ]]; then
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "OneBot route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }

    cat >>"$CONF" <<'NGINX'

location = /onebot/v11/ws {
    limit_except GET { deny all; }
    proxy_pass http://127.0.0.1:6199/ws;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Authorization $http_authorization;
    proxy_set_header X-Self-ID $http_x_self_id;
    proxy_set_header X-Client-Role $http_x_client_role;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_read_timeout 3600;
    proxy_send_timeout 3600;
    add_header X-LCXQY-Transport onebot-v11 always;
}
NGINX

    if ! nginx -t; then
        rollback
        exit 4
    fi
    if ! nginx -s reload; then
        rollback
        exit 5
    fi
fi

headers_unauthorized="$(mktemp)"
headers_authorized="$(mktemp)"
cleanup() {
    rm -f "$headers_unauthorized" "$headers_authorized"
}
trap cleanup EXIT

handshake() {
    local headers=$1
    shift
    curl -skS --resolve "$VERIFY_HOST:443:127.0.0.1" \
        --max-time 3 -o /dev/null -D "$headers" \
        -H 'Connection: Upgrade' \
        -H 'Upgrade: websocket' \
        -H 'Sec-WebSocket-Version: 13' \
        -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
        -H 'X-Self-ID: 10000' \
        -H 'X-Client-Role: Universal' \
        "$@" "https://$VERIFY_HOST$ROUTE" || true
}

ready=false
for attempt in {1..15}; do
    handshake "$headers_unauthorized"
    handshake "$headers_authorized" -H "Authorization: Bearer $LCXQY_ONEBOT_TOKEN"
    unauthorized_code="$(awk 'toupper($1) ~ /^HTTP\// {code=$2} END {print code+0}' "$headers_unauthorized")"
    authorized_code="$(awk 'toupper($1) ~ /^HTTP\// {code=$2} END {print code+0}' "$headers_authorized")"
    if [[ "$unauthorized_code" == 401 && "$authorized_code" == 101 ]] \
        && grep -Fi "X-LCXQY-Transport: $HEADER" "$headers_authorized" >/dev/null; then
        ready=true
        break
    fi
    sleep 1
done

if [[ "$ready" != true ]]; then
    echo "OneBot public WebSocket verification failed." >&2
    echo "unauthorized_code=${unauthorized_code:-0}" >&2
    echo "authorized_code=${authorized_code:-0}" >&2
    if [[ "$already_present" == false ]]; then
        rollback
    fi
    exit 6
fi

if [[ "$already_present" == true ]]; then
    echo "OneBot route is already promoted and verified."
else
    echo "OneBot route promoted; backup: $BACKUP"
fi
echo "public_websocket=wss://$VERIFY_HOST$ROUTE"
echo "unauthorized_code=$unauthorized_code"
echo "authorized_code=$authorized_code"
