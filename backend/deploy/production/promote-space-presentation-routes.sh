#!/usr/bin/env bash
set -euo pipefail

# Promotes only dynamic presentation reads and staff mutations. The database
# migration and replacement JAR must already be deployed and verified.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-space-presentation-$STAMP"
HEADER=replacement-space-presentation
ROUTES=(spacePresentation spacePresentationList)

for required in awk cat cp curl grep nginx seq sha256sum sleep; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

route_count=0
for route in "${ROUTES[@]}"; do
    count=$(grep -Fc "location = /SFreeSpace/$route {" "$CONF" || true)
    [[ "$count" -le 1 ]] || { echo "Duplicate route detected: $route" >&2; exit 3; }
    [[ "$count" == 1 ]] && route_count=$((route_count + 1))
done
header_count=$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)

if [[ "$route_count" == "${#ROUTES[@]}" && "$header_count" == "${#ROUTES[@]}" ]]; then
    BACKUP=already-promoted
elif [[ "$route_count" != 0 || "$header_count" != 0 ]]; then
    echo "Partial dynamic-presentation route promotion detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback_pending=true
    rollback() {
        echo "Dynamic-presentation route promotion failed; restoring $BACKUP" >&2
        rollback_pending=false
        trap - ERR
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    rollback_on_error() {
        status=$?
        if [[ "$rollback_pending" == true ]]; then
            rollback || true
        fi
        exit "$status"
    }
    trap rollback_on_error ERR

    cat >>"$CONF" <<'NGINX'

# Dynamic presentation is handled by the replacement backend. Keep these
# exact so unrelated SFreeSpace endpoints continue through existing routes.
NGINX
    for route in "${ROUTES[@]}"; do
        cat >>"$CONF" <<NGINX
location = /SFreeSpace/$route {
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
    nginx -s reload || { rollback; exit 20; }
    rollback_pending=false
    trap - ERR
fi

for route in "${ROUTES[@]}"; do
    observed=''
    for _ in $(seq 1 10); do
        observed=$(curl -sk --max-time 20 -D - -o /dev/null \
            "$PUBLIC_URL/SFreeSpace/$route" \
            | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
        [[ "$observed" == "$HEADER" ]] && break
        sleep 1
    done
    if [[ "$observed" != "$HEADER" ]]; then
        echo "Backend header mismatch for $route: ${observed:-<missing>}" >&2
        [[ "$BACKUP" == already-promoted ]] || rollback
        exit 21
    fi
    echo "$route=$observed"
done

list_body=$(curl -sk --max-time 20 "$PUBLIC_URL/SFreeSpace/spacePresentationList")
grep -Eq '"code"[[:space:]]*:[[:space:]]*1' <<<"$list_body" || {
    echo "Public spacePresentationList did not return a successful API envelope" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 22
}

mutation_body=$(curl -sk --max-time 20 \
    "$PUBLIC_URL/SFreeSpace/spacePresentation?token=codex_invalid_token&id=0")
grep -Eq '"code"[[:space:]]*:' <<<"$mutation_body" || {
    echo "Public spacePresentation did not return an API envelope" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 23
}

echo "rollback=$BACKUP"
sha256sum "$CONF"
