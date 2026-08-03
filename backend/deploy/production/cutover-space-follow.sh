#!/usr/bin/env bash
set -euo pipefail

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
SMOKE_SCRIPT=${SMOKE_SCRIPT:-/opt/starfree-replacement/verify-space-follow.sh}
BACKUP="$CONF.rollback-space-follow-$(date +%Y%m%d-%H%M%S)"
[[ -f "$CONF" ]] || { echo "Nginx include missing" >&2; exit 2; }
for route in followSpace myFollowSpace; do grep -q "^location = /SFreeSpace/$route {" "$CONF" && { echo "$route already exists" >&2; exit 3; }; done
cp -p "$CONF" "$BACKUP"
cat >>"$CONF" <<'NGINX'

# Followed-Space feed. The replacement filters followed users' private/reply
# rows and supports both the legacy name and the alias shipped by the frontend.
location = /SFreeSpace/followSpace {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-space-follow always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}

location = /SFreeSpace/myFollowSpace {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-space-follow always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST $remote_addr;
}
NGINX
rollback() { echo "Follow cutover failed; restoring $BACKUP" >&2; cp -p "$BACKUP" "$CONF"; nginx -t; nginx -s reload; }
nginx -t || { rollback; exit 20; }
nginx -s reload
for route in followSpace myFollowSpace; do
    header=''
    for _ in $(seq 1 10); do
        header="$(curl -sk -G -D - -o /dev/null "https://api.lcxqy.cn/SFreeSpace/$route" \
            --data-urlencode token=codex_invalid_token --data-urlencode page=1 --data-urlencode limit=1 \
            | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $0}')"
        [[ "$header" == 'X-Starfree-Backend: replacement-space-follow' ]] && break
        sleep 1
    done
    [[ "$header" == 'X-Starfree-Backend: replacement-space-follow' ]] || {
        echo "$route header mismatch after reload: ${header:-<missing>}" >&2
        rollback
        exit 21
    }
done
VERIFY_PUBLIC=1 EXPECTED_PUBLIC_BACKEND=replacement-space-follow bash "$SMOKE_SCRIPT" || { rollback; exit 22; }
sha256sum "$CONF"
echo "space_follow_backend=replacement-space-follow"
echo "rollback=$BACKUP"
