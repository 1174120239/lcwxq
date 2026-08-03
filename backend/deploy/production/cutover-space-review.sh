#!/usr/bin/env bash
set -euo pipefail

# Cut over only spaceReview. Lock and delete remain on the legacy catch-all.
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
SMOKE_SCRIPT=${SMOKE_SCRIPT:-/opt/starfree-replacement/verify-space-review.sh}
BACKUP="$CONF.rollback-space-review-$(date +%Y%m%d-%H%M%S)"

[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }
[[ -x "$SMOKE_SCRIPT" || -r "$SMOKE_SCRIPT" ]] || { echo "Smoke script missing: $SMOKE_SCRIPT" >&2; exit 2; }
if grep -q '^location = /SFreeSpace/spaceReview {' "$CONF"; then
    echo "spaceReview route already exists; refusing a duplicate cutover" >&2
    exit 3
fi

rollback() {
    echo "spaceReview cutover failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
}

cp -p "$CONF" "$BACKUP"
cat >>"$CONF" <<'NGINX'

# Moderation approval/rejection uses the replacement's explicit staff guard,
# duplicate protection, pending-row rejection fix, and best-effort notice.
location = /SFreeSpace/spaceReview {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-space-review always;
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
header=''
for _ in $(seq 1 10); do
    header="$(curl -sk -G -D - -o /dev/null 'https://api.lcxqy.cn/SFreeSpace/spaceReview' \
        --data-urlencode token=codex_invalid_token \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $0}')"
    [[ "$header" == 'X-Starfree-Backend: replacement-space-review' ]] && break
    sleep 1
done
if [[ "$header" != 'X-Starfree-Backend: replacement-space-review' ]]; then
    echo "spaceReview header mismatch after reload: ${header:-<missing>}" >&2
    rollback
    exit 21
fi

if ! VERIFY_PUBLIC=1 EXPECTED_PUBLIC_BACKEND=replacement-space-review \
        bash "$SMOKE_SCRIPT"; then
    rollback
    exit 22
fi

sha256sum "$CONF"
echo "space_review_backend=replacement-space-review"
echo "rollback=$BACKUP"
