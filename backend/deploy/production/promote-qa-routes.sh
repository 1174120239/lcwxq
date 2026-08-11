#!/usr/bin/env bash
set -euo pipefail

# Promotes only the campus Q&A endpoints. Every route is exact so the legacy
# catch-all and unrelated replacement endpoints keep their existing behavior.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-qa-$STAMP"
HEADER=replacement-campus-qa
ROUTES=(
    questionList questionInfo questionAdd answerList answerAdd answerEdit answerDelete
    answerLike commentList commentAdd commentDelete questionManage questionSave
    questionStatus
)

for required in awk cat cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

route_count=0
for route in "${ROUTES[@]}"; do
    count=$(grep -Fc "location = /SFreeQa/$route {" "$CONF" || true)
    [[ "$count" -le 1 ]] || { echo "Duplicate route detected: $route" >&2; exit 3; }
    [[ "$count" == 1 ]] && route_count=$((route_count + 1))
done
header_count=$(grep -Fc "add_header X-Starfree-Backend $HEADER always;" "$CONF" || true)

if [[ "$route_count" == "${#ROUTES[@]}" && "$header_count" == "${#ROUTES[@]}" ]]; then
    BACKUP=already-promoted
    ROUTES_TO_ADD=()
elif [[ "$route_count" == 0 && "$header_count" == 0 ]]; then
    ROUTES_TO_ADD=("${ROUTES[@]}")
elif [[ "$route_count" == $((${#ROUTES[@]} - 1)) && "$header_count" == "$route_count"
        && $(grep -Fc "location = /SFreeQa/questionAdd {" "$CONF" || true) == 0 ]]; then
    # Older releases promoted the original routes. Add public question
    # submission without duplicating or replacing those locations.
    ROUTES_TO_ADD=(questionAdd)
else
    echo "Partial campus Q&A route promotion detected" >&2
    exit 3
fi

if [[ "${#ROUTES_TO_ADD[@]}" -gt 0 ]]; then
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Campus Q&A route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }

    if [[ "$route_count" == 0 ]]; then
        cat >>"$CONF" <<'NGINX'

# Campus Q&A is provided by the replacement backend. Keep these locations
# exact so unknown plugin endpoints continue to use the legacy catch-all.
NGINX
    fi
    for route in "${ROUTES_TO_ADD[@]}"; do
        cat >>"$CONF" <<NGINX
location = /SFreeQa/$route {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend $HEADER always;
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
fi

observed=''
for _ in $(seq 1 10); do
    observed=$(curl -sk --max-time 20 -D - -o /dev/null \
        "$PUBLIC_URL/SFreeQa/questionList?page=1&limit=1" \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
    [[ "$observed" == "$HEADER" ]] && break
    sleep 1
done
if [[ "$observed" != "$HEADER" ]]; then
    echo "Backend header mismatch for questionList: ${observed:-<missing>}" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 21
fi

for route in "${ROUTES[@]}"; do
    observed=$(curl -sk --max-time 20 -D - -o /dev/null \
        "$PUBLIC_URL/SFreeQa/$route" \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
    if [[ "$observed" != "$HEADER" ]]; then
        echo "Backend header mismatch for $route: ${observed:-<missing>}" >&2
        [[ "$BACKUP" == already-promoted ]] || rollback
        exit 21
    fi
    echo "$route=$observed"
done

body=$(curl -sk --max-time 20 "$PUBLIC_URL/SFreeQa/questionList?page=1&limit=1")
grep -Eq '"code"[[:space:]]*:[[:space:]]*1' <<<"$body" || {
    echo "Public questionList did not return a successful API envelope" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 22
}

echo "rollback=$BACKUP"
sha256sum "$CONF"
