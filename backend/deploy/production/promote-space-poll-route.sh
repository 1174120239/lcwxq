#!/usr/bin/env bash
set -euo pipefail

# Promote only authenticated poll submissions. Dynamic reads and publishing
# already use their existing exact routes; unrelated legacy APIs stay intact.
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-space-poll-$STAMP"
HEADER=replacement-space-poll
ROUTE=pollVote

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

route_count=$(grep -Fc "location = /SFreeSpace/$ROUTE {" "$CONF" || true)
header_count=$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)
[[ "$route_count" -le 1 && "$header_count" -le 1 ]] || {
    echo "Duplicate poll route or header detected" >&2
    exit 3
}

if [[ "$route_count" == 1 && "$header_count" == 1 ]]; then
    BACKUP=already-promoted
elif [[ "$route_count" != 0 || "$header_count" != 0 ]]; then
    echo "Partial poll route promotion detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Poll route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    cat >>"$CONF" <<'NGINX'

# Authenticated dynamic poll submissions use the replacement backend.
location = /SFreeSpace/pollVote {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-space-poll always;
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
fi

observed=''
body=''
for _ in $(seq 1 10); do
    response=$(mktemp)
    observed=$(curl -sk --max-time 20 -D - -o "$response" -G \
        "$PUBLIC_URL/SFreeSpace/$ROUTE" \
        --data-urlencode 'token=route-audit-invalid' \
        --data-urlencode 'pollId=1' \
        --data-urlencode 'optionIds=1' \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
    body=$(<"$response")
    rm -f "$response"
    [[ "$observed" == "$HEADER" ]] && break
    sleep 1
done
if [[ "$observed" != "$HEADER" ]]; then
    echo "Backend header mismatch for $ROUTE: ${observed:-<missing>}" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 21
fi
grep -Eq '"code"[[:space:]]*:' <<<"$body" || {
    echo "Public $ROUTE did not return an API envelope" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 22
}

echo "$ROUTE=$observed"
echo "rollback=$BACKUP"
sha256sum "$CONF"
