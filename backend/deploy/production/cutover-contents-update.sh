#!/usr/bin/env bash
set -euo pipefail

# Route only contentsUpdate through the replacement. The Java routing policy
# delegates paid, draft, shop-linked, and unsupported content to port 8081.
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
SMOKE_SCRIPT=${SMOKE_SCRIPT:-/opt/starfree-replacement/verify-contents-update.sh}
BACKUP="$CONF.rollback-contents-update-$(date +%Y%m%d-%H%M%S)"

[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }
[[ -x "$SMOKE_SCRIPT" || -r "$SMOKE_SCRIPT" ]] || { echo "Smoke script missing: $SMOKE_SCRIPT" >&2; exit 2; }
if grep -q '^location = /SFreeContents/contentsUpdate {' "$CONF"; then
    echo "contentsUpdate route already exists; refusing a duplicate cutover" >&2
    exit 3
fi

rollback() {
    echo "contentsUpdate cutover failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
}

cp -p "$CONF" "$BACKUP"
cat >>"$CONF" <<'NGINX'

# Ordinary post/video edits are local. Paid content, drafts, shop-linked rows,
# and unsupported content types are delegated internally to port 8081.
location = /SFreeContents/contentsUpdate {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-content-update always;
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
header=''
for _ in $(seq 1 10); do
    header="$(curl -sk -X POST -D - -o /dev/null 'https://api.lcxqy.cn/SFreeContents/contentsUpdate' \
        --data-urlencode token=ccu_invalid_token \
        --data-urlencode 'params={"cid":2147483647,"title":"route-probe","sid":-1}' \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $0}')"
    [[ "$header" == 'X-Starfree-Backend: replacement-content-update' ]] && break
    sleep 1
done
if [[ "$header" != 'X-Starfree-Backend: replacement-content-update' ]]; then
    echo "contentsUpdate header mismatch after reload: ${header:-<missing>}" >&2
    rollback
    exit 21
fi

if ! VERIFY_PUBLIC=1 EXPECTED_PUBLIC_BACKEND=replacement-content-update bash "$SMOKE_SCRIPT"; then
    rollback
    exit 22
fi

sha256sum "$CONF"
echo "contents_update_backend=replacement-content-update"
echo "rollback=$BACKUP"
