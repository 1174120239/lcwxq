#!/usr/bin/env bash
set -euo pipefail

# Move only contentsInfo. Every other content write and read keeps its current route.
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
SMOKE_SCRIPT=${SMOKE_SCRIPT:-/opt/starfree-replacement/verify-contents-info.sh}
BACKUP="$CONF.rollback-contents-info-$(date +%Y%m%d-%H%M%S)"

[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }
[[ -x "$SMOKE_SCRIPT" || -r "$SMOKE_SCRIPT" ]] || { echo "Smoke script missing: $SMOKE_SCRIPT" >&2; exit 2; }
if grep -q '^location = /SFreeContents/contentsInfo {' "$CONF"; then
    echo "contentsInfo route already exists; refusing a duplicate cutover" >&2
    exit 3
fi

rollback() {
    echo "contentsInfo cutover failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
}

cp -p "$CONF" "$BACKUP"
cat >>"$CONF" <<'NGINX'

# Full article detail uses the shared Redis session and 15-minute read key.
# Content publishing, editing, moderation, and deletion still use the legacy API.
location = /SFreeContents/contentsInfo {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-content-info always;
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
    header="$(curl -sk -G -D - -o /dev/null 'https://api.lcxqy.cn/SFreeContents/contentsInfo' \
        --data-urlencode key=0 \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $0}')"
    [[ "$header" == 'X-Starfree-Backend: replacement-content-info' ]] && break
    sleep 1
done
if [[ "$header" != 'X-Starfree-Backend: replacement-content-info' ]]; then
    echo "contentsInfo header mismatch after reload: ${header:-<missing>}" >&2
    rollback
    exit 21
fi

if ! VERIFY_PUBLIC=1 EXPECTED_PUBLIC_BACKEND=replacement-content-info bash "$SMOKE_SCRIPT"; then
    rollback
    exit 22
fi

sha256sum "$CONF"
echo "contents_info_backend=replacement-content-info"
echo "rollback=$BACKUP"
