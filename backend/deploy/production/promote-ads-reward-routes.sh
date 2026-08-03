#!/usr/bin/env bash
set -euo pipefail

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-ads-reward-$STAMP"

for required in cp curl grep nginx sed sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

routes=(ads-gift ads-gift-notify ads-server-notify)
uris=(/SFreeUserlog/adsGift /SFreeUserlog/adsGiftNotify /SFreeUserlog/adsServerNotify)

already_promoted=true
for route in "${routes[@]}"; do
    header="replacement-economy-replacement-$route"
    header_line="add_header X-Starfree-Backend $header always;"
    [[ "$(grep -Fc "$header_line" "$CONF")" == 1 ]] || already_promoted=false
done

rollback() {
    echo "Advertising reward route promotion failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
}

if [[ "$already_promoted" != true ]]; then
    for route in "${routes[@]}"; do
        old="replacement-economy-legacy-locked-$route"
        new="replacement-economy-replacement-$route"
        old_line="add_header X-Starfree-Backend $old always;"
        new_line="add_header X-Starfree-Backend $new always;"
        [[ "$(grep -Fc "$old_line" "$CONF")" == 1 && "$(grep -Fc "$new_line" "$CONF")" == 0 ]] || {
            echo "Unexpected route header count for $route" >&2
            exit 3
        }
    done
    cp -p "$CONF" "$BACKUP"
    # Replace longest names first because ads-gift prefixes ads-gift-notify.
    sed -i \
        -e 's/replacement-economy-legacy-locked-ads-server-notify/replacement-economy-replacement-ads-server-notify/g' \
        -e 's/replacement-economy-legacy-locked-ads-gift-notify/replacement-economy-replacement-ads-gift-notify/g' \
        -e 's/replacement-economy-legacy-locked-ads-gift/replacement-economy-replacement-ads-gift/g' \
        "$CONF"
    nginx -t || { rollback; exit 20; }
    nginx -s reload
else
    BACKUP='already-promoted'
fi

for index in "${!routes[@]}"; do
    route="${routes[$index]}"
    uri="${uris[$index]}"
    expected="replacement-economy-replacement-$route"
    observed=''
    for _ in $(seq 1 10); do
        observed="$(curl -sk --max-time 20 -D - -o /dev/null "$PUBLIC_URL$uri" \
            | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')"
        [[ "$observed" == "$expected" ]] && break
        sleep 1
    done
    if [[ "$observed" != "$expected" ]]; then
        echo "Backend header mismatch for $uri: ${observed:-<missing>}" >&2
        [[ "$BACKUP" == already-promoted ]] || rollback
        exit 21
    fi
    echo "$uri=$observed"
done

echo "rollback=$BACKUP"
sha256sum "$CONF"
