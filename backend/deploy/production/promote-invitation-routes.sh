#!/usr/bin/env bash
set -euo pipefail

# Promotes only the simple invitation config and dashboard endpoints.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-invitation-$STAMP"
HEADER=replacement-invitation
ROUTES=(config me)

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

route_count=0
for route in "${ROUTES[@]}"; do
    count=$(grep -Fc "location = /SFreeInvitation/$route {" "$CONF" || true)
    [[ "$count" -le 1 ]] || { echo "Duplicate route detected: $route" >&2; exit 3; }
    [[ "$count" == 1 ]] && route_count=$((route_count + 1))
done
header_count=$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)

if [[ "$route_count" == "${#ROUTES[@]}" && "$header_count" == "${#ROUTES[@]}" ]]; then
    BACKUP=already-promoted
elif [[ "$route_count" != 0 || "$header_count" != 0 ]]; then
    echo "Partial invitation route promotion detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Invitation route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }

    cat >>"$CONF" <<'NGINX'

# Simple invitation sharing is provided by the replacement backend.
NGINX
    for route in "${ROUTES[@]}"; do
        cat >>"$CONF" <<NGINX
location = /SFreeInvitation/$route {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend $HEADER always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST \$remote_addr;
}

NGINX
    done
    nginx -t || { rollback; exit 20; }
    nginx -s reload
fi

observed=''
for _ in $(seq 1 10); do
    observed=$(curl -sk --max-time 20 -D - -o /dev/null \
        "$PUBLIC_URL/SFreeInvitation/config" \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
    [[ "$observed" == "$HEADER" ]] && break
    sleep 1
done
if [[ "$observed" != "$HEADER" ]]; then
    echo "Backend header mismatch for invitation config: ${observed:-<missing>}" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 21
fi

body=$(curl -sk --max-time 20 "$PUBLIC_URL/SFreeInvitation/config")
grep -Eq '"code"[[:space:]]*:[[:space:]]*1' <<<"$body" || {
    echo "Invitation config did not return a successful API envelope" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 22
}

echo "config=$observed"
echo "rollback=$BACKUP"
sha256sum "$CONF"
