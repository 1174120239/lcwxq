#!/usr/bin/env bash
set -euo pipefail

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-user-register-$STAMP"
LOCATION='location = /SFreeUsers/userRegister {'
HEADER='add_header X-Starfree-Backend replacement-user-register always;'

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

location_count="$(grep -Fc "$LOCATION" "$CONF" || true)"
header_count="$(grep -Fc "$HEADER" "$CONF" || true)"
if [[ "$location_count" == 1 && "$header_count" == 1 ]]; then
    BACKUP='already-promoted'
elif [[ "$location_count" != 0 || "$header_count" != 0 ]]; then
    echo "Partial or duplicate userRegister route detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Registration route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    cat >>"$CONF" <<'NGINX'

# Registration is rebuilt locally. Verification-code delivery remains on the
# legacy catch-all; this exact route owns only account creation and invite pay.
location = /SFreeUsers/userRegister {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-user-register always;
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
for _ in $(seq 1 10); do
    observed="$(curl -sk --max-time 20 -D - -o /dev/null -X POST \
        "$PUBLIC_URL/SFreeUsers/userRegister" --data-urlencode 'params={}' \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')"
    [[ "$observed" == replacement-user-register ]] && break
    sleep 1
done
if [[ "$observed" != replacement-user-register ]]; then
    echo "Backend header mismatch for userRegister: ${observed:-<missing>}" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 21
fi

echo "userRegister=$observed"
echo "rollback=$BACKUP"
sha256sum "$CONF"
