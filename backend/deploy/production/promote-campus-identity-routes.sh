#!/usr/bin/env bash
set -euo pipefail

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-campus-identity-$STAMP"
HEADER='add_header X-Starfree-Backend replacement-campus-identity always;'
ROUTES=(campusIdentityOptions campusIdentityManage campusIdentitySave)

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

route_count=0
for route in "${ROUTES[@]}"; do
    count=$(grep -Fc "location = /SFreeUsers/$route {" "$CONF" || true)
    [[ "$count" == 1 ]] && route_count=$((route_count + 1))
    [[ "$count" -le 1 ]] || { echo "Duplicate route detected: $route" >&2; exit 3; }
done
header_count=$(grep -Fc "$HEADER" "$CONF" || true)

if [[ "$route_count" == 3 && "$header_count" == 3 ]]; then
    BACKUP=already-promoted
elif [[ "$route_count" != 0 || "$header_count" != 0 ]]; then
    echo "Partial campus identity route promotion detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Campus identity route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    cat >>"$CONF" <<'NGINX'

# Campus and admission-year options use stable ids shared by registration and
# staff management. These exact routes avoid changing any other legacy user API.
location = /SFreeUsers/campusIdentityOptions {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-campus-identity always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeUsers/campusIdentityManage {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-campus-identity always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeUsers/campusIdentitySave {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-campus-identity always;
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

for route in "${ROUTES[@]}"; do
    observed=''
    for _ in $(seq 1 10); do
        observed=$(curl -sk --max-time 20 -D - -o /dev/null \
            "$PUBLIC_URL/SFreeUsers/$route" \
            | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
        [[ "$observed" == replacement-campus-identity ]] && break
        sleep 1
    done
    if [[ "$observed" != replacement-campus-identity ]]; then
        echo "Backend header mismatch for $route: ${observed:-<missing>}" >&2
        [[ "$BACKUP" == already-promoted ]] || rollback
        exit 21
    fi
    echo "$route=$observed"
done

echo "rollback=$BACKUP"
sha256sum "$CONF"
