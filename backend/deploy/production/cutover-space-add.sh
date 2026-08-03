#!/usr/bin/env bash
set -euo pipefail

# Cut over only addSpace. Editing, moderation, deletion, payment, verification,
# upload, shop/VIP, and chat continue through the legacy port-8081 catch-all.
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
SMOKE_SCRIPT=${SMOKE_SCRIPT:-/opt/starfree-replacement/verify-space-add.sh}
BACKUP="$CONF.rollback-space-add-$(date +%Y%m%d-%H%M%S)"

[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }
[[ -x "$SMOKE_SCRIPT" || -r "$SMOKE_SCRIPT" ]] || { echo "Smoke script missing: $SMOKE_SCRIPT" >&2; exit 2; }
if grep -q '^location = /SFreeSpace/addSpace {' "$CONF"; then
    echo "addSpace route already exists; refusing a duplicate cutover" >&2
    exit 3
fi

rollback() {
    echo "addSpace cutover failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
}

cp -p "$CONF" "$BACKUP"
cat >>"$CONF" <<'NGINX'

# Space publishing moved only after Redis-only authentication, shared legacy
# quota, audit/experience, private-row, plugin rejection, and cleanup checks.
location = /SFreeSpace/addSpace {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-space-add always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}
NGINX

nginx -t || { rollback; exit 20; }
nginx -s reload

# Nginx reload is asynchronous. Do not mistake a stale worker response for a
# failed cutover; wait briefly for the exact location to become observable.
header=''
for _ in $(seq 1 10); do
    header="$(curl -sk -G -D - -o /dev/null 'https://api.lcxqy.cn/SFreeSpace/addSpace' \
        --data-urlencode token=codex_invalid_token \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $0}')"
    [[ "$header" == 'X-Starfree-Backend: replacement-space-add' ]] && break
    sleep 1
done
if [[ "$header" != 'X-Starfree-Backend: replacement-space-add' ]]; then
    echo "addSpace header mismatch after reload: ${header:-<missing>}" >&2
    rollback
    exit 21
fi

if ! VERIFY_PUBLIC=1 EXPECTED_PUBLIC_BACKEND=replacement-space-add \
        bash "$SMOKE_SCRIPT"; then
    rollback
    exit 22
fi

sha256sum "$CONF"
echo "space_add_backend=replacement-space-add"
echo "rollback=$BACKUP"
