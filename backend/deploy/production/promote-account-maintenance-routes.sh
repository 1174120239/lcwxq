#!/usr/bin/env bash
set -euo pipefail

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-account-maintenance-$STAMP"

declare -A HEADERS=(
    [regConfig]=replacement-account-reg-config
    [userFoget]=replacement-account-password-reset
    [userEdit]=replacement-account-edit
    [setClientId]=replacement-account-client-id
)

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

complete=true
empty=true
for route in "${!HEADERS[@]}"; do
    location_count="$(grep -Fc "location = /SFreeUsers/$route {" "$CONF" || true)"
    header_count="$(grep -Fc "add_header X-Starfree-Backend ${HEADERS[$route]} always;" "$CONF" || true)"
    [[ "$location_count" == 1 && "$header_count" == 1 ]] || complete=false
    [[ "$location_count" == 0 && "$header_count" == 0 ]] || empty=false
done

if [[ "$complete" == true ]]; then
    BACKUP='already-promoted'
elif [[ "$empty" != true ]]; then
    echo "Partial or duplicate account maintenance routes detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Account route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    cat >>"$CONF" <<'NGINX'

# Account configuration and maintenance are rebuilt locally. Email/SMS code
# delivery remains on the legacy catch-all; these routes only consume codes.
location = /SFreeUsers/regConfig {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-account-reg-config always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeUsers/userFoget {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-account-password-reset always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeUsers/userEdit {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-account-edit always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeUsers/setClientId {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-account-client-id always;
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

for route in regConfig userFoget userEdit setClientId; do
    observed=''
    for _ in $(seq 1 10); do
        observed="$(curl -sk --max-time 20 -D - -o /dev/null -G \
            "$PUBLIC_URL/SFreeUsers/$route" \
            --data-urlencode 'params={}' --data-urlencode 'token=route-audit-invalid' \
            | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')"
        [[ "$observed" == "${HEADERS[$route]}" ]] && break
        sleep 1
    done
    if [[ "$observed" != "${HEADERS[$route]}" ]]; then
        echo "Backend header mismatch for $route: ${observed:-<missing>}" >&2
        [[ "$BACKUP" == already-promoted ]] || rollback
        exit 21
    fi
    echo "$route=$observed"
done

for legacy_route in SendCode RegSendCode; do
    count="$(grep -Fc "location = /SFreeUsers/$legacy_route {" "$CONF" || true)"
    [[ "$count" == 0 ]] || {
        echo "$legacy_route must remain on the legacy catch-all" >&2
        exit 22
    }
done

echo "rollback=$BACKUP"
sha256sum "$CONF"
