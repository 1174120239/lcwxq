#!/usr/bin/env bash
set -euo pipefail

# Switches inbox, unread count and read-marker routes to the replacement backend.
# The replacement renders spaceComment notifications with the source dynamic
# state; the legacy catch-all predates that type. Run during a release session
# after the new JAR is deployed, following DEPLOYMENT_GUIDE.md.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-inbox-$STAMP"

ROUTES=(inbox unreadNum setRead)
HEADER=replacement-user-inbox

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

complete=true
empty=true
for route in "${ROUTES[@]}"; do
    location_count="$(grep -Fc "location = /SFreeUsers/$route {" "$CONF" || true)"
    header_count="$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)"
    [[ "$location_count" == 1 && "$header_count" == 1 ]] || complete=false
    [[ "$location_count" == 0 && "$header_count" == 0 ]] || empty=false
done

if [[ "$complete" == true ]]; then
    BACKUP='already-promoted'
elif [[ "$empty" != true ]]; then
    echo "Partial or duplicate inbox routes detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Inbox route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    cat >>"$CONF" <<'NGINX'

# Inbox, unread count and read markers are rebuilt locally. The replacement
# renders dynamic comment (spaceComment) notifications with their source Space
# state and opens the original dynamic; the legacy catch-all predates this type.
location = /SFreeUsers/inbox {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-user-inbox always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeUsers/unreadNum {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-user-inbox always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeUsers/setRead {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-user-inbox always;
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
        observed="$(curl -sk --max-time 20 -D - -o /dev/null -G \
            "$PUBLIC_URL/SFreeUsers/$route" \
            --data-urlencode 'token=route-audit-invalid' \
            | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')"
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

for legacy_route in myChat msgList; do
    count="$(grep -Fc "location = /SFreeChat/$legacy_route {" "$CONF" || true)"
    [[ "$count" == 0 ]] || {
        echo "$legacy_route must remain on the legacy catch-all" >&2
        exit 22
    }
done

echo "rollback=$BACKUP"
sha256sum "$CONF"
