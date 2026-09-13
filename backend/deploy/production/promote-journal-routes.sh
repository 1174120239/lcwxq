#!/usr/bin/env bash
set -euo pipefail
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-journal-$STAMP"
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
ROUTES=(journalList journalInfo articleList articleInfo articleSubmit articleVote journalSave journalStatus articleManage myArticles articleReview)
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }
for route in "${ROUTES[@]}"; do [[ $(grep -Fc "location = /SFreeJournal/$route {" "$CONF" || true) == 0 ]] || { echo "Route already exists: $route" >&2; exit 3; }; done
cp -p "$CONF" "$BACKUP"
rollback(){ cp -p "$BACKUP" "$CONF"; nginx -t && nginx -s reload; }
for route in "${ROUTES[@]}"; do
cat >>"$CONF" <<NGINX
location = /SFreeJournal/$route {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend replacement-journal always;
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
nginx -s reload
observed=$(curl -sk --max-time 20 -D - -o /tmp/journal-route-body "$PUBLIC_URL/SFreeJournal/journalList?page=1&limit=1" | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
if [[ "$observed" != replacement-journal ]]; then echo "Backend header mismatch: ${observed:-<missing>}" >&2; rollback; exit 21; fi
grep -Eq '"code"[[:space:]]*:[[:space:]]*1' /tmp/journal-route-body || { echo 'Journal API envelope failed.' >&2; rollback; exit 22; }
echo "rollback=$BACKUP"
sha256sum "$CONF"
