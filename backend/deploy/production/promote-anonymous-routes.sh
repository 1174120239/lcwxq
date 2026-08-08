#!/usr/bin/env bash
set -euo pipefail

# Switches the anonymous dynamics endpoints (ng_music plugin functionality,
# implemented natively in the replacement backend) to port 18082. Run during a
# release session after the new JAR is deployed and migration 005 is applied,
# following DEPLOYMENT_GUIDE.md.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-anonymous-$STAMP"

ROUTES=(config post owner)
HEADER=replacement-anonymous

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

complete=true
empty=true
header_total="$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)"
for route in "${ROUTES[@]}"; do
    location_count="$(grep -Fc "location = /SFreeAnonymous/$route {" "$CONF" || true)"
    [[ "$location_count" == 1 && "$header_total" -ge "${#ROUTES[@]}" ]] || complete=false
    [[ "$location_count" == 0 ]] || empty=false
done
admin_location_count="$(grep -Fc "location = /SFreeAnonymous/admin/config {" "$CONF" || true)"
[[ "$admin_location_count" == 1 && "$header_total" -ge 4 ]] || complete=false
[[ "$admin_location_count" == 0 ]] || empty=false
[[ "$header_total" == 0 ]] || empty=false

if [[ "$complete" == true ]]; then
    BACKUP='already-promoted'
elif [[ "$empty" != true ]]; then
    echo "Partial or duplicate anonymous routes detected" >&2
    exit 3
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Anonymous route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    cat >>"$CONF" <<'NGINX'

# Anonymous dynamics are rebuilt locally (ng_music plugin functionality). The
# public config endpoint must not reveal the anonymous account identity; the
# owner lookup only returns the real publisher to the poster or staff.
location = /SFreeAnonymous/config {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-anonymous always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeAnonymous/post {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-anonymous always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeAnonymous/owner {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-anonymous always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeAnonymous/admin/config {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-anonymous always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}
NGINX
    nginx -t
    nginx -s reload
    echo "Anonymous routes promoted; backup: $BACKUP"
fi
