#!/usr/bin/env bash
set -euo pipefail

# Promotes only the read-only community feed. Migrations 007 and 014 and the
# matching replacement JAR must be installed and verified before this route.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
LOCAL_URL=${LOCAL_URL:-http://127.0.0.1:18082}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-community-feed-$STAMP"
HEADER=replacement-community-feed
ROUTE=/SFreeFeed/feedList

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

local_body=$(curl -fsS --max-time 20 "$LOCAL_URL$ROUTE?page=1&limit=1") || {
    echo "Local community feed endpoint is not healthy" >&2
    exit 2
}
grep -Eq '"code"[[:space:]]*:[[:space:]]*1' <<<"$local_body" || {
    echo "Local community feed endpoint did not return a successful API envelope" >&2
    exit 2
}

route_count=$(grep -Fc "location = $ROUTE {" "$CONF" || true)
header_count=$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)
if [[ "$route_count" == 1 && "$header_count" == 1 ]]; then
    BACKUP=already-promoted
elif [[ "$route_count" == 0 && "$header_count" == 0 ]]; then
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Community feed route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }

    cat >>"$CONF" <<'NGINX'

# Read-only unified community feed. Keep this location exact.
location = /SFreeFeed/feedList {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-community-feed always;
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
else
    echo "Partial community feed route promotion detected" >&2
    exit 3
fi

observed=''
for _ in $(seq 1 10); do
    observed=$(curl -sk --max-time 20 -D - -o /dev/null \
        "$PUBLIC_URL$ROUTE?page=1&limit=1&type=question" \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
    [[ "$observed" == "$HEADER" ]] && break
    sleep 1
done
if [[ "$observed" != "$HEADER" ]]; then
    echo "Backend header mismatch: ${observed:-<missing>}" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 21
fi

body=$(curl -sk --max-time 20 "$PUBLIC_URL$ROUTE?page=1&limit=1")
grep -Eq '"code"[[:space:]]*:[[:space:]]*1' <<<"$body" || {
    echo "Public community feed did not return a successful API envelope" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 22
}

echo "feedList=$observed"
echo "rollback=$BACKUP"
sha256sum "$CONF"
