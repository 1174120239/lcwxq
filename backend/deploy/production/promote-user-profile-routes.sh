#!/usr/bin/env bash
set -euo pipefail

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-user-profile-$STAMP"
ROUTES=(userStatus userInfo)
HEADERS=(replacement-user-status replacement-user-info)

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

promoted=0
for index in "${!ROUTES[@]}"; do
    route=${ROUTES[$index]}
    header=${HEADERS[$index]}
    route_count=$(grep -Fc "location = /SFreeUsers/$route {" "$CONF" || true)
    header_count=$(grep -Fc "add_header X-Starfree-Backend $header always;" "$CONF" || true)
    [[ "$route_count" -le 1 ]] || { echo "Duplicate route detected: $route" >&2; exit 3; }
    [[ "$header_count" -le 1 ]] || { echo "Duplicate header detected: $header" >&2; exit 3; }
    if [[ "$route_count" == 1 && "$header_count" == 1 ]]; then
        promoted=$((promoted + 1))
    elif [[ "$route_count" != 0 || "$header_count" != 0 ]]; then
        echo "Partial user profile route detected: $route" >&2
        exit 3
    fi
done

if [[ "$promoted" == 2 ]]; then
    BACKUP=already-promoted
elif [[ "$promoted" != 0 ]]; then
    echo "Partial user profile route promotion detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "User profile route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    cat >>"$CONF" <<'NGINX'

# User profile projections expose the stable campus and admission-year names.
# Only these read routes move to replacement; unrelated user APIs stay legacy.
location = /SFreeUsers/userStatus {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-user-status always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeUsers/userInfo {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-user-info always;
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

for index in "${!ROUTES[@]}"; do
    route=${ROUTES[$index]}
    expected=${HEADERS[$index]}
    observed=''
    for _ in $(seq 1 10); do
        observed=$(curl -sk --max-time 20 -D - -o /dev/null \
            "$PUBLIC_URL/SFreeUsers/$route" \
            | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
        [[ "$observed" == "$expected" ]] && break
        sleep 1
    done
    if [[ "$observed" != "$expected" ]]; then
        echo "Backend header mismatch for $route: ${observed:-<missing>}" >&2
        [[ "$BACKUP" == already-promoted ]] || rollback
        exit 21
    fi
    echo "$route=$observed"
done

echo "rollback=$BACKUP"
sha256sum "$CONF"
