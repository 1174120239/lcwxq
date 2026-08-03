#!/usr/bin/env bash
set -euo pipefail

# Direct replacement smoke test. It uses disposable balances only and removes
# every row by resolved IDs, including the InnoDB operation journal.
PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
HEALTH_URL=${HEALTH_URL:-$REPLACEMENT_URL/health}
DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-lcxqy}

read_property() {
    local key="$1"
    awk -v wanted="$key" '
        {
            line = $0
            sub(/^[[:space:]]+/, "", line)
            if (index(line, wanted) == 1) {
                value = substr(line, length(wanted) + 1)
                if (value ~ /^[[:space:]]*=/) {
                    sub(/^[[:space:]]*=[[:space:]]*/, "", value)
                    print value
                    exit
                }
            }
        }
    ' "$PROPERTIES_FILE"
}

for required in awk curl mysql; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
    PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
    PYTHON_BIN=python
else
    echo "Python is required to parse JSON" >&2
    exit 2
fi

DB_USERNAME=${DB_USERNAME:-$(read_property spring.datasource.username)}
DB_PASSWORD=${DB_PASSWORD:-$(read_property spring.datasource.password)}
[[ -n "$DB_USERNAME" && -n "$DB_PASSWORD" ]] || {
    echo "Database credentials are missing" >&2
    exit 2
}
export MYSQL_PWD="$DB_PASSWORD"

sql() {
    mysql --protocol=TCP --host="$DB_HOST" --port="$DB_PORT" \
        --user="$DB_USERNAME" --batch --skip-column-names \
        "$DB_NAME" --execute="$1"
}

json_code() {
    "$PYTHON_BIN" -c 'import json,sys; print(json.load(sys.stdin).get("code", ""))'
}

assert_code() {
    local label="$1" expected="$2" response="$3" actual
    actual="$(printf '%s' "$response" | json_code)"
    [[ "$actual" == "$expected" ]] || {
        echo "$label returned code=$actual, expected $expected" >&2
        echo "$response" >&2
        exit 10
    }
    echo "$label=code:$actual"
}

suffix="$(date +%s | tail -c 8)$(printf '%02d' "$(( $$ % 100 ))")"
USER_NAME="ceu_${suffix}"
ADMIN_NAME="cea_${suffix}"
USER_TOKEN="${USER_NAME}_token"
ADMIN_TOKEN="${ADMIN_NAME}_token"
USER_UID=''
ADMIN_UID=''
CONTENT_ID=''
SHOP_ID=''
VIP_PACKAGE_ID=''
AD_ID=''

cleanup() {
    local cleanup_status=0 ids=''
    set +e
    for uid in "$USER_UID" "$ADMIN_UID"; do
        [[ "$uid" =~ ^[1-9][0-9]*$ ]] && ids="${ids:+$ids,}$uid"
    done
    if [[ -n "$CONTENT_ID" ]]; then
        sql "DELETE FROM starfree_relationships WHERE cid=$CONTENT_ID;
             DELETE FROM starfree_fields WHERE cid=$CONTENT_ID;
             DELETE FROM starfree_comments WHERE cid=$CONTENT_ID;
             DELETE FROM starfree_contents WHERE cid=$CONTENT_ID;" >/dev/null 2>&1 \
            || cleanup_status=1
    fi
    if [[ "$AD_ID" =~ ^[1-9][0-9]*$ ]]; then
        sql "DELETE FROM starfree_ads WHERE aid=$AD_ID;" >/dev/null 2>&1 \
            || cleanup_status=1
    fi
    if [[ "$SHOP_ID" =~ ^[1-9][0-9]*$ ]]; then
        sql "DELETE FROM starfree_shop WHERE id=$SHOP_ID;" >/dev/null 2>&1 \
            || cleanup_status=1
    fi
    if [[ "$VIP_PACKAGE_ID" =~ ^[1-9][0-9]*$ ]]; then
        sql "DELETE FROM starfree_vips WHERE id=$VIP_PACKAGE_ID;" >/dev/null 2>&1 \
            || cleanup_status=1
    fi
    if [[ -n "$ids" ]]; then
        sql "DELETE FROM starfree_admin_Signinlog WHERE uid IN ('$USER_UID','$ADMIN_UID');
             DELETE FROM starfree_userlog WHERE uid IN ($ids) OR toid IN ($ids);
             DELETE FROM starfree_paylog WHERE uid IN ($ids);
             DELETE FROM starfree_inbox WHERE uid IN ($ids) OR touid IN ($ids);
             DELETE FROM starfree_economy_operations WHERE actor_uid IN ($ids) OR target_uid IN ($ids);
             DELETE FROM starfree_users WHERE uid IN ($ids);" >/dev/null 2>&1 \
            || cleanup_status=1
    fi
    sql "DELETE FROM starfree_users WHERE name IN ('$USER_NAME','$ADMIN_NAME')" \
        >/dev/null 2>&1 || cleanup_status=1
    if [[ $cleanup_status -ne 0 ]]; then
        echo "WARNING: inspect disposable economy cleanup for $suffix" >&2
    fi
    return $cleanup_status
}
trap cleanup EXIT

[[ "$(sql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='starfree_economy_operations'")" == 1 ]] || {
    echo "Economy migration has not been applied" >&2
    exit 11
}
curl -fsS "$HEALTH_URL" >/dev/null

now="$(date +%s)"
ad_unit_price="$(sql "SELECT COALESCE(pushAdsPrice,0) FROM starfree_apiconfig ORDER BY id LIMIT 1")"
[[ "$ad_unit_price" =~ ^[0-9]+$ ]] || { echo "Invalid push advertising price" >&2; exit 12; }
starting_assets="$(( ad_unit_price + 1000 ))"
sql "INSERT INTO starfree_users
    (name,password,mail,created,activated,logged,\`group\`,authCode,assets,experience,points,pay,vip)
    VALUES
    ('$USER_NAME','','$USER_NAME@invalid.local',$now,$now,0,'contributor','$USER_TOKEN',$starting_assets,11,7,'integration-pay',0),
    ('$ADMIN_NAME','','$ADMIN_NAME@invalid.local',$now,$now,0,'administrator','$ADMIN_TOKEN',0,0,0,'integration-pay',0)"
USER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$USER_NAME' LIMIT 1")"
ADMIN_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$ADMIN_NAME' LIMIT 1")"
[[ "$USER_UID" =~ ^[1-9][0-9]*$ && "$ADMIN_UID" =~ ^[1-9][0-9]*$ ]] || {
    echo "Could not resolve disposable users" >&2
    exit 12
}
echo "disposable_uids=$USER_UID,$ADMIN_UID"

echo "stage=create_reward_content"
sql "INSERT INTO starfree_contents
    (title,slug,created,modified,text,authorId,type,status)
    VALUES ('economy smoke $suffix','ce_$suffix',$now,$now,'<!--markdown-->smoke',$ADMIN_UID,'post','publish')"
CONTENT_ID="$(sql "SELECT cid FROM starfree_contents WHERE slug='ce_$suffix' LIMIT 1")"
[[ "$CONTENT_ID" =~ ^[1-9][0-9]*$ ]] || {
    echo "Could not resolve disposable reward content" >&2
    exit 12
}
echo "disposable_content_id=$CONTENT_ID"

echo "stage=reward_idempotency"
reward_params="$(printf '{"cid":%s,"type":"reward","num":10}' "$CONTENT_ID")"
reward_id="ce_reward_$suffix"
reward_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUserlog/addLog" \
    --data-urlencode "token=$USER_TOKEN" --data-urlencode "params=$reward_params" \
    --data-urlencode "requestId=$reward_id")"
assert_code reward 1 "$reward_response"
reward_replay="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUserlog/addLog" \
    --data-urlencode "token=$USER_TOKEN" --data-urlencode "params=$reward_params" \
    --data-urlencode "requestId=$reward_id")"
assert_code reward_replay 1 "$reward_replay"
[[ "$(sql "SELECT CONCAT((SELECT assets FROM starfree_users WHERE uid=$USER_UID),',',(SELECT assets FROM starfree_users WHERE uid=$ADMIN_UID),',',(SELECT COUNT(*) FROM starfree_userlog WHERE uid=$USER_UID AND cid=$CONTENT_ID AND type='reward'))")" == "$(( starting_assets - 10 )),10,1" ]] || {
    echo "Reward balances or idempotency are incorrect" >&2
    exit 13
}

points_before="$(sql "SELECT points FROM starfree_users WHERE uid=$USER_UID")"
echo "stage=signin_balance_isolation"
signin_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeEconomy/signin" \
    --data-urlencode "token=$USER_TOKEN")"
assert_code signin 1 "$signin_response"
[[ "$(sql "SELECT points FROM starfree_users WHERE uid=$USER_UID")" == "$points_before" ]] || {
    echo "Seven-day sign-in changed points" >&2
    exit 14
}

echo "stage=manual_adjustments"
adjust_assets="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/userRecharge" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "key=$USER_UID" \
    --data-urlencode num=5 --data-urlencode type=0 --data-urlencode rechargeType=0 \
    --data-urlencode "requestId=ce_assets_$suffix")"
assert_code adjust_assets 1 "$adjust_assets"
adjust_points="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/userRecharge" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "key=$USER_UID" \
    --data-urlencode num=4 --data-urlencode type=0 --data-urlencode rechargeType=1 \
    --data-urlencode "requestId=ce_points_$suffix")"
assert_code adjust_points 1 "$adjust_points"
[[ "$(sql "SELECT points FROM starfree_users WHERE uid=$USER_UID")" == "$(( points_before + 4 ))" ]] || {
    echo "Manual points adjustment is incorrect" >&2
    exit 15
}

echo "stage=shop_purchase"
sql "INSERT INTO starfree_shop
    (title,price,num,type,uid,vipDiscount,created,status,sellNum,integral)
    VALUES ('economy shop $suffix',10,1,0,$ADMIN_UID,'1',$now,1,0,3)"
SHOP_ID="$(sql "SELECT id FROM starfree_shop WHERE title='economy shop $suffix' LIMIT 1")"
[[ "$SHOP_ID" =~ ^[1-9][0-9]*$ ]] || { echo "Could not resolve disposable shop" >&2; exit 16; }
shop_before="$(sql "SELECT CONCAT((SELECT assets FROM starfree_users WHERE uid=$USER_UID),',',(SELECT points FROM starfree_users WHERE uid=$USER_UID),',',(SELECT assets FROM starfree_users WHERE uid=$ADMIN_UID),',',(SELECT points FROM starfree_users WHERE uid=$ADMIN_UID))")"
IFS=, read -r buyer_assets buyer_points seller_assets seller_points <<<"$shop_before"
shop_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeShop/buyShop" \
    --data-urlencode "token=$USER_TOKEN" --data-urlencode "sid=$SHOP_ID" \
    --data-urlencode isIntegral=1 --data-urlencode "requestId=ce_shop_$suffix")"
assert_code shop_buy 1 "$shop_response"
shop_replay="$(curl -fsS -G "$REPLACEMENT_URL/SFreeShop/buyShop" \
    --data-urlencode "token=$USER_TOKEN" --data-urlencode "sid=$SHOP_ID" \
    --data-urlencode isIntegral=1 --data-urlencode "requestId=ce_shop_$suffix")"
assert_code shop_replay 1 "$shop_replay"
shop_expected="$(( buyer_assets - 7 )),$(( buyer_points - 3 )),$(( seller_assets + 7 )),$(( seller_points + 3 ))"
shop_actual="$(sql "SELECT CONCAT((SELECT assets FROM starfree_users WHERE uid=$USER_UID),',',(SELECT points FROM starfree_users WHERE uid=$USER_UID),',',(SELECT assets FROM starfree_users WHERE uid=$ADMIN_UID),',',(SELECT points FROM starfree_users WHERE uid=$ADMIN_UID))")"
[[ "$shop_actual" == "$shop_expected" ]] || { echo "Shop balance transfer is incorrect" >&2; exit 16; }
[[ "$(sql "SELECT CONCAT(num,',',sellNum) FROM starfree_shop WHERE id=$SHOP_ID")" == '0,1' ]] || {
    echo "Shop stock or sales count is incorrect" >&2
    exit 16
}

echo "stage=vip_package_purchase"
sql "INSERT INTO starfree_vips (orderKey,name,price,day,giftDay,intro)
    VALUES (999,'economy vip $suffix',5,1,0,'disposable smoke package')"
VIP_PACKAGE_ID="$(sql "SELECT id FROM starfree_vips WHERE name='economy vip $suffix' LIMIT 1")"
[[ "$VIP_PACKAGE_ID" =~ ^[1-9][0-9]*$ ]] || { echo "Could not resolve disposable VIP package" >&2; exit 16; }
vip_before="$(sql "SELECT CONCAT(assets,',',points,',',experience) FROM starfree_users WHERE uid=$USER_UID")"
IFS=, read -r vip_assets vip_points vip_experience <<<"$vip_before"
vip_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeShop/buyVIPpackage" \
    --data-urlencode "token=$USER_TOKEN" --data-urlencode "id=$VIP_PACKAGE_ID" \
    --data-urlencode "requestId=ce_vip_$suffix")"
assert_code vip_package 1 "$vip_response"
vip_replay="$(curl -fsS -G "$REPLACEMENT_URL/SFreeShop/buyVIPpackage" \
    --data-urlencode "token=$USER_TOKEN" --data-urlencode "id=$VIP_PACKAGE_ID" \
    --data-urlencode "requestId=ce_vip_$suffix")"
assert_code vip_replay 1 "$vip_replay"
[[ "$(sql "SELECT CONCAT(assets,',',points,',',experience) FROM starfree_users WHERE uid=$USER_UID")" == "$(( vip_assets - 5 )),$vip_points,$vip_experience" ]] || {
    echo "VIP purchase changed an unexpected balance or charged twice" >&2
    exit 16
}
[[ "$(sql "SELECT IF(vip>$now,1,0) FROM starfree_users WHERE uid=$USER_UID")" == 1 ]] || {
    echo "VIP package did not extend the disposable user" >&2
    exit 16
}

echo "stage=advertising_purchase"
ad_params="$(printf '{\"name\":\"economy ad %s\",\"type\":0,\"img\":\"https://example.invalid/ad.png\",\"intro\":\"disposable smoke ad\",\"urltype\":1,\"url\":\"https://example.invalid/\"}' "$suffix")"
ad_assets_before="$(sql "SELECT assets FROM starfree_users WHERE uid=$USER_UID")"
ad_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeAds/addAds" \
    --data-urlencode "token=$USER_TOKEN" --data-urlencode day=1 \
    --data-urlencode "params=$ad_params" --data-urlencode "requestId=ce_ad_$suffix")"
assert_code ads_buy 1 "$ad_response"
ad_replay="$(curl -fsS -G "$REPLACEMENT_URL/SFreeAds/addAds" \
    --data-urlencode "token=$USER_TOKEN" --data-urlencode day=1 \
    --data-urlencode "params=$ad_params" --data-urlencode "requestId=ce_ad_$suffix")"
assert_code ads_replay 1 "$ad_replay"
AD_ID="$(sql "SELECT aid FROM starfree_ads WHERE uid=$USER_UID AND name='economy ad $suffix' LIMIT 1")"
[[ "$AD_ID" =~ ^[1-9][0-9]*$ ]] || { echo "Could not resolve disposable advertisement" >&2; exit 16; }
[[ "$(sql "SELECT assets FROM starfree_users WHERE uid=$USER_UID")" == "$(( ad_assets_before - ad_unit_price ))" ]] || {
    echo "Advertising purchase debit is incorrect" >&2
    exit 16
}
[[ "$(sql "SELECT COUNT(*) FROM starfree_paylog WHERE uid=$USER_UID AND paytype='buyAds'")" == 1 ]] || {
    echo "Advertising replay created an incorrect paylog count" >&2
    exit 16
}
ad_close_before="$(sql "SELECT \`close\` FROM starfree_ads WHERE aid=$AD_ID")"
ad_price_before="$(sql "SELECT price FROM starfree_ads WHERE aid=$AD_ID")"
renewal_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeAds/renewalAds" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$AD_ID" \
    --data-urlencode day=1 --data-urlencode "requestId=ce_ad_renewal_$suffix")"
assert_code ads_renewal 1 "$renewal_response"
renewal_replay="$(curl -fsS -G "$REPLACEMENT_URL/SFreeAds/renewalAds" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$AD_ID" \
    --data-urlencode day=1 --data-urlencode "requestId=ce_ad_renewal_$suffix")"
assert_code ads_renewal_replay 1 "$renewal_replay"
[[ "$(sql "SELECT CONCAT(\`close\`-$ad_close_before,',',price-$ad_price_before) FROM starfree_ads WHERE aid=$AD_ID")" == "86400,$ad_unit_price" ]] || {
    echo "Advertising renewal duration or value is incorrect" >&2
    exit 16
}

assets_before_withdraw="$(sql "SELECT assets FROM starfree_users WHERE uid=$USER_UID")"
echo "stage=withdrawal_approval"
withdraw_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/userWithdraw" \
    --data-urlencode "token=$USER_TOKEN" --data-urlencode num=2 \
    --data-urlencode "requestId=ce_withdraw_$suffix")"
assert_code withdraw_request 1 "$withdraw_response"
withdraw_id="$(sql "SELECT id FROM starfree_userlog WHERE uid=$USER_UID AND type='withdraw' AND cid=-1 ORDER BY id DESC LIMIT 1")"
[[ "$(sql "SELECT assets FROM starfree_users WHERE uid=$USER_UID")" == "$assets_before_withdraw" ]] || {
    echo "Withdrawal application debited before approval" >&2
    exit 16
}
approve_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/withdrawStatus" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "key=$withdraw_id" \
    --data-urlencode type=1)"
assert_code withdraw_approve 1 "$approve_response"
[[ "$(sql "SELECT CONCAT(cid,',',(SELECT assets FROM starfree_users WHERE uid=$USER_UID)) FROM starfree_userlog WHERE id=$withdraw_id")" == "0,$(( assets_before_withdraw - 2 ))" ]] || {
    echo "Withdrawal approval state or balance is incorrect" >&2
    exit 17
}

echo "stage=finance_payload"
orders_response="$(curl -fsS -G "$REPLACEMENT_URL/pay/payorderList" \
    --data-urlencode "token=$USER_TOKEN")"
assert_code payorder_list 1 "$orders_response"
printf '%s' "$orders_response" | "$PYTHON_BIN" -c '
import json,sys
value=json.load(sys.stdin)
assert isinstance(value.get("paydata"), list) and len(value["paydata"]) >= 5
' || { echo "payorderList top-level paydata is missing" >&2; exit 18; }

needs_review="$(sql "SELECT COUNT(*) FROM starfree_economy_operations WHERE (actor_uid IN ($USER_UID,$ADMIN_UID) OR target_uid IN ($USER_UID,$ADMIN_UID)) AND state='needs_review'")"
[[ "$needs_review" == 0 ]] || {
    echo "Disposable economy operation requires manual review" >&2
    exit 19
}

echo "economy_smoke=passed"
