#!/usr/bin/env bash
set -euo pipefail

# Cut over only /SFreeSpace/spaceLikes. Every other Space write and all retained
# payment/verification/upload/shop/chat endpoints continue to use the original
# port-8081 catch-all. A timestamped config copy is the immediate rollback file.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
BACKUP="$CONF.rollback-space-like-$(date +%Y%m%d-%H%M%S)"
SMOKE_SCRIPT=${SMOKE_SCRIPT:-/opt/starfree-replacement/verify-space-like.sh}

[[ -f "$CONF" ]] || { echo "Nginx include not found: $CONF" >&2; exit 2; }
if grep -q '^location = /SFreeSpace/spaceLikes {' "$CONF"; then
    echo "Space-like route already exists; refusing a duplicate cutover" >&2
    exit 3
fi

cp -p "$CONF" "$BACKUP"
cat >>"$CONF" <<'NGINX'

# First Space write moved to the replacement. Authentication remains compatible
# because both services share the Java-serialized Redis session and userlog table.
location = /SFreeSpace/spaceLikes {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-space-like always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}
NGINX

if ! nginx -t; then
    echo "Nginx test failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    exit 20
fi
nginx -s reload

header="$(curl -sk -X POST -D - -o /dev/null \
    'https://api.lcxqy.cn/SFreeSpace/spaceLikes?token=codex_invalid_token&id=0' \
    | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $0}')"
if [[ "$header" != 'X-Starfree-Backend: replacement-space-like' ]]; then
    echo "Space-like route header mismatch: ${header:-<missing>}" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
    exit 21
fi

# A header proves routing, while this disposable-data smoke test proves actual
# public Redis authentication, first-like success, duplicate rejection, shared
# legacy logs, correct counter value, and cleanup. Restore Nginx automatically
# if any part of that final gate fails.
if ! VERIFY_PUBLIC=1 EXPECTED_PUBLIC_BACKEND=replacement-space-like \
        bash "$SMOKE_SCRIPT"; then
    echo "Public Space-like smoke test failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
    exit 22
fi

sha256sum "$CONF"
echo "space_like_backend=replacement-space-like"
echo "rollback=$BACKUP"
