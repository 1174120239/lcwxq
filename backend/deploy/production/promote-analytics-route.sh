#!/usr/bin/env bash
set -euo pipefail

# Promotes only the privacy-minimized App analytics ingestion endpoint.
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-analytics-$STAMP"
HEADER=replacement-analytics
ROUTE=/SFreeAnalytics/event

for required in cp curl grep nginx awk sha256sum; do
    command -v "$required" >/dev/null 2>&1 || { echo "Required command not found: $required" >&2; exit 2; }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

route_count=$(grep -Fc "location = $ROUTE {" "$CONF" || true)
header_count=$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)
if [[ "$route_count" == 1 && "$header_count" == 1 ]]; then
    BACKUP=already-promoted
elif [[ "$route_count" != 0 || "$header_count" != 0 ]]; then
    echo "Partial analytics route promotion detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Analytics route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    cat >>"$CONF" <<'NGINX'

# App lifecycle and download analytics only; website/H5 clients are rejected by the client and service allow-lists.
location = /SFreeAnalytics/event {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-analytics always;
    proxy_connect_timeout 10;
    proxy_read_timeout 20;
    proxy_send_timeout 20;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

NGINX
    nginx -t || { rollback; exit 20; }
    nginx -s reload
fi

observed=''
for _ in $(seq 1 10); do
    observed=$(curl -sk --max-time 20 -D - -o /dev/null "$PUBLIC_URL$ROUTE" \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
    [[ "$observed" == "$HEADER" ]] && break
    sleep 1
done
if [[ "$observed" != "$HEADER" ]]; then
    echo "Backend header mismatch for analytics route: ${observed:-<missing>}" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 21
fi

echo "route=$ROUTE backend=$observed"
echo "rollback=$BACKUP"
sha256sum "$CONF"
