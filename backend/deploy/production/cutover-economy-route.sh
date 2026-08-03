#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <route-key>" >&2
    exit 2
fi

ROUTE_KEY="$1"
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}

# Each invocation changes one exact location and creates one rollback file.
# Official payment routes are still implemented by port 8081; port 18082 only
# holds the shared advisory lock while its generic proxy forwards those bytes.
case "$ROUTE_KEY" in
    pay-scancode) URI=/pay/scancodePayStar; MODE=legacy-locked ;;
    pay-wx) URI=/pay/WxPayStar; MODE=legacy-locked ;;
    pay-token) URI=/pay/tokenPay; MODE=legacy-locked ;;
    pay-token-star) URI=/pay/tokenPayStar; MODE=legacy-locked ;;
    pay-epay) URI=/pay/EPayStar; MODE=legacy-locked ;;
    pay-qr) URI=/pay/qrCodeStar; MODE=legacy-locked ;;
    pay-notify) URI=/pay/notify; MODE=legacy-locked ;;
    pay-wx-notify) URI=/pay/wxPayNotify; MODE=legacy-locked ;;
    pay-epay-notify) URI=/pay/EPayNotify; MODE=legacy-locked ;;
    ads-gift) URI=/SFreeUserlog/adsGift; MODE=replacement ;;
    ads-gift-notify) URI=/SFreeUserlog/adsGiftNotify; MODE=replacement ;;
    ads-server-notify) URI=/SFreeUserlog/adsServerNotify; MODE=replacement ;;
    userlog-write) URI=/SFreeUserlog/addLog; MODE=replacement ;;
    reward-list) URI=/SFreeContents/rewardList; MODE=replacement ;;
    signin-config) URI=/SFreeEconomy/signinConfig; MODE=replacement ;;
    signin) URI=/SFreeEconomy/signin; MODE=replacement ;;
    signin-streak) URI=/SFreeEconomy/signinStreak; MODE=replacement ;;
    manual-adjust) URI=/SFreeUsers/userRecharge; MODE=replacement ;;
    withdraw-request) URI=/SFreeUsers/userWithdraw; MODE=replacement ;;
    withdraw-list) URI=/SFreeUsers/withdrawList; MODE=replacement ;;
    withdraw-review) URI=/SFreeUsers/withdrawStatus; MODE=replacement ;;
    wallet-orders) URI=/pay/payorderList; MODE=replacement ;;
    finance-list) URI=/pay/financeList; MODE=replacement ;;
    finance-total) URI=/pay/financeTotal; MODE=replacement ;;
    shop-buy) URI=/SFreeShop/buyShop; MODE=replacement ;;
    shop-bought) URI=/SFreeShop/isBuyShop; MODE=replacement ;;
    vip-buy) URI=/SFreeShop/buyVIP; MODE=replacement ;;
    vip-package) URI=/SFreeShop/buyVIPpackage; MODE=replacement ;;
    vip-info) URI=/SFreeShop/vipInfo; MODE=replacement ;;
    ads-buy) URI=/SFreeAds/addAds; MODE=replacement ;;
    ads-renewal) URI=/SFreeAds/renewalAds; MODE=replacement ;;
    *) echo "Unknown economy route key: $ROUTE_KEY" >&2; exit 2 ;;
esac

[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }
if grep -Fq "location = $URI {" "$CONF"; then
    echo "Exact route already exists: $URI" >&2
    exit 3
fi

BACKUP="$CONF.rollback-economy-$ROUTE_KEY-$(date +%Y%m%d-%H%M%S)"
HEADER="replacement-economy-$MODE-$ROUTE_KEY"

rollback() {
    echo "Route cutover failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    nginx -s reload
}

cp -p "$CONF" "$BACKUP"
cat >>"$CONF" <<NGINX

# Economy route '$ROUTE_KEY'. Backup: $BACKUP
location = $URI {
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

nginx -t || { rollback; exit 20; }
nginx -s reload

observed=''
for _ in $(seq 1 10); do
    observed="$(curl -sk --max-time 20 -D - -o /dev/null "$PUBLIC_URL$URI" \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')"
    [[ "$observed" == "$HEADER" ]] && break
    sleep 1
done
if [[ "$observed" != "$HEADER" ]]; then
    echo "Backend header mismatch for $URI: ${observed:-<missing>}" >&2
    rollback
    exit 21
fi

echo "route=$URI"
echo "backend=$HEADER"
echo "rollback=$BACKUP"
sha256sum "$CONF"
