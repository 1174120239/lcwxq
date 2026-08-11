#!/usr/bin/env bash
set -euo pipefail

# Promotes only registration/email-change and password-recovery mail delivery.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-email-verification-$STAMP"

declare -A HEADERS=(
    [RegSendCode]=replacement-email-registration
    [SendCode]=replacement-email-recovery
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
    location_count=$(grep -Fc "location = /SFreeUsers/$route {" "$CONF" || true)
    header_count=$(grep -Fc "add_header X-Starfree-Backend ${HEADERS[$route]} always;" "$CONF" || true)
    [[ "$location_count" == 1 && "$header_count" == 1 ]] || complete=false
    [[ "$location_count" == 0 && "$header_count" == 0 ]] || empty=false
done

if [[ "$complete" == true ]]; then
    BACKUP=already-promoted
elif [[ "$empty" != true ]]; then
    echo "Partial or duplicate email verification routes detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Email verification route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }

    cat >>"$CONF" <<'NGINX'

# Email verification delivery is provided by the replacement backend.
location = /SFreeUsers/RegSendCode {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-email-registration always;
    proxy_connect_timeout 10;
    proxy_read_timeout 30;
    proxy_send_timeout 30;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeUsers/SendCode {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-email-recovery always;
    proxy_connect_timeout 10;
    proxy_read_timeout 30;
    proxy_send_timeout 30;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}
NGINX
    nginx -t || { rollback; exit 20; }
    nginx -s reload
fi

for route in RegSendCode SendCode; do
    observed=''
    for _ in $(seq 1 10); do
        observed=$(curl -sk --max-time 20 -D - -o /dev/null -G \
            "$PUBLIC_URL/SFreeUsers/$route" \
            --data-urlencode 'params={}' \
            | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
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

echo "rollback=$BACKUP"
sha256sum "$CONF"
