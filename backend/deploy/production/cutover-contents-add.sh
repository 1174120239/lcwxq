#!/usr/bin/env bash
set -euo pipefail

# Route contentsAdd through the replacement. The Java routing policy owns only
# ordinary post/video requests and delegates all closed features to port 8081.
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
SMOKE_SCRIPT=${SMOKE_SCRIPT:-/opt/starfree-replacement/verify-contents-add.sh}
BACKUP="$CONF.rollback-contents-add-$(date +%Y%m%d-%H%M%S)"

[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }
[[ -x "$SMOKE_SCRIPT" || -r "$SMOKE_SCRIPT" ]] || { echo "Smoke script missing: $SMOKE_SCRIPT" >&2; exit 2; }
if grep -q '^location = /SFreeContents/contentsAdd {' "$CONF"; then
    echo "contentsAdd route already exists; refusing a duplicate cutover" >&2
    exit 3
fi

rollback() {
    echo "contentsAdd cutover failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
}

cp -p "$CONF" "$BACKUP"
cat >>"$CONF" <<'NGINX'

# Ordinary post/video publishing is local. Paid content, drafts, linked Space
# posts, attached shop rows, and unknown types are delegated internally to 8081.
location = /SFreeContents/contentsAdd {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-content-add always;
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
    header="$(curl -sk -X POST -D - -o /dev/null 'https://api.lcxqy.cn/SFreeContents/contentsAdd' \
        --data-urlencode token=cca_invalid_token \
        --data-urlencode 'params={"title":"route-probe","sid":-1,"type":"post"}' \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $0}')"
    [[ "$header" == 'X-Starfree-Backend: replacement-content-add' ]] && break
    sleep 1
done
if [[ "$header" != 'X-Starfree-Backend: replacement-content-add' ]]; then
    echo "contentsAdd header mismatch after reload: ${header:-<missing>}" >&2
    rollback
    exit 21
fi

if ! VERIFY_PUBLIC=1 EXPECTED_PUBLIC_BACKEND=replacement-content-add bash "$SMOKE_SCRIPT"; then
    rollback
    exit 22
fi

sha256sum "$CONF"
echo "contents_add_backend=replacement-content-add"
echo "rollback=$BACKUP"
