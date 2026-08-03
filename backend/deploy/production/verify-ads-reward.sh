#!/usr/bin/env bash
set -euo pipefail

# Disposable advertising-reward smoke test. It never changes reward config and
# removes all rows by resolved user IDs. Run direct-port and public tests serially.
PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
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

for required in awk curl mysql sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1; then
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
        --user="$DB_USERNAME" --batch --raw --skip-column-names \
        "$DB_NAME" --execute="$1"
}

json_field() {
    local field="$1"
    "$PYTHON_BIN" -c 'import json,sys; v=json.load(sys.stdin).get(sys.argv[1]); print(str(v).lower() if isinstance(v,bool) else v)' "$field"
}

assert_field() {
    local label="$1" field="$2" expected="$3" response="$4" actual
    actual="$(printf '%s' "$response" | json_field "$field")"
    [[ "$actual" == "$expected" ]] || {
        echo "$label returned $field=$actual, expected $expected" >&2
        echo "$response" >&2
        exit 10
    }
}

suffix="$(date +%s | tail -c 8)$(printf '%02d' "$(( $$ % 100 ))")"
OWNER_NAME="car_${suffix}_a"
OTHER_NAME="car_${suffix}_b"
OWNER_TOKEN="${OWNER_NAME}_token"
OTHER_TOKEN="${OTHER_NAME}_token"
OWNER_UID=''
OTHER_UID=''

cleanup() {
    local status=0 ids=''
    set +e
    for uid in "$OWNER_UID" "$OTHER_UID"; do
        [[ "$uid" =~ ^[1-9][0-9]*$ ]] && ids="${ids:+$ids,}$uid"
    done
    if [[ -n "$ids" ]]; then
        sql "DELETE FROM starfree_userlog WHERE uid IN ($ids) OR toid IN ($ids);
             DELETE FROM starfree_paylog WHERE uid IN ($ids);
             DELETE FROM starfree_economy_operations
             WHERE actor_uid IN ($ids) OR target_uid IN ($ids);
             DELETE FROM starfree_users WHERE uid IN ($ids);" >/dev/null 2>&1 || status=1
    fi
    sql "DELETE FROM starfree_users WHERE name IN ('$OWNER_NAME','$OTHER_NAME');" \
        >/dev/null 2>&1 || status=1
    [[ $status -eq 0 ]] || echo "WARNING: inspect advertising reward cleanup for $suffix" >&2
    return $status
}
trap cleanup EXIT

now="$(date +%s)"
sql "INSERT INTO starfree_users
    (name,password,mail,created,activated,logged,\`group\`,authCode,assets,experience,points,pay,vip)
    VALUES
    ('$OWNER_NAME','','$OWNER_NAME@invalid.local',$now,$now,0,'contributor','$OWNER_TOKEN',100,0,0,'reward-smoke',0),
    ('$OTHER_NAME','','$OTHER_NAME@invalid.local',$now,$now,0,'contributor','$OTHER_TOKEN',50,0,0,'reward-smoke',0)"
OWNER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$OWNER_NAME' LIMIT 1")"
OTHER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$OTHER_NAME' LIMIT 1")"
[[ "$OWNER_UID" =~ ^[1-9][0-9]*$ && "$OTHER_UID" =~ ^[1-9][0-9]*$ ]] || {
    echo "Could not resolve disposable advertising reward users" >&2
    exit 11
}

config="$(sql "SELECT CONCAT_WS(CHAR(9),adsVideoType,adsGiftNum,adsGiftAward,
    COALESCE(adsSecuritykey,'')) FROM starfree_apiconfig ORDER BY id LIMIT 1")"
IFS=$'\t' read -r video_type gift_limit gift_award security_key <<<"$config"
[[ "$video_type" =~ ^[01]$ && "$gift_limit" =~ ^[0-9]+$ && "$gift_award" =~ ^[0-9]+$ ]] || {
    echo "Advertising reward configuration is invalid" >&2
    exit 12
}

if [[ "$video_type" == 0 ]]; then
    sql "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid)
         VALUES ($OWNER_UID,0,'adsGift',0,$now,0)"
    log_id="$(sql "SELECT id FROM starfree_userlog WHERE uid=$OWNER_UID
        AND type='adsGift' ORDER BY id DESC LIMIT 1")"
    stolen="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUserlog/adsGiftNotify" \
        --data-urlencode "token=$OTHER_TOKEN" --data-urlencode "logid=$log_id")"
    assert_field client_cross_user code 0 "$stolen"
    owner_before="$(sql "SELECT assets FROM starfree_users WHERE uid=$OWNER_UID")"
    rewarded="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUserlog/adsGiftNotify" \
        --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "logid=$log_id")"
    assert_field client_reward code 1 "$rewarded"
    replay="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUserlog/adsGiftNotify" \
        --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "logid=$log_id")"
    assert_field client_reward_replay code 1 "$replay"
    [[ "$(sql "SELECT assets FROM starfree_users WHERE uid=$OWNER_UID")" == "$(( owner_before + gift_award ))" ]] || {
        echo "Client reward balance is not idempotent" >&2
        exit 13
    }
    echo "ads_reward_mode=client"
else
    trans_id="car_${suffix}_tx"
    owner_before="$(sql "SELECT assets FROM starfree_users WHERE uid=$OWNER_UID")"
    signature="$(printf '%s' "${security_key}:${trans_id}" | sha256sum | awk '{print $1}')"
    callback="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUserlog/adsServerNotify" \
        --data-urlencode "trans_id=$trans_id" --data-urlencode "user_id=$OWNER_UID" \
        --data-urlencode "sign=$signature" --data-urlencode "adpid=reward-smoke" \
        --data-urlencode "provider=reward-smoke" --data-urlencode 'extra=')"
    if [[ -z "$security_key" ]]; then
        assert_field empty_key_callback isValid false "$callback"
        [[ "$(sql "SELECT assets FROM starfree_users WHERE uid=$OWNER_UID")" == "$owner_before" ]] || {
            echo "Empty-key callback changed the disposable balance" >&2
            exit 14
        }
        echo "ads_reward_mode=server-disabled-empty-key"
    elif [[ "$gift_limit" == 0 ]]; then
        assert_field zero_limit_callback isValid false "$callback"
        echo "ads_reward_mode=server-disabled-zero-limit"
    else
        assert_field server_reward isValid true "$callback"
        replay="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUserlog/adsServerNotify" \
            --data-urlencode "trans_id=$trans_id" --data-urlencode "user_id=$OWNER_UID" \
            --data-urlencode "sign=$signature" --data-urlencode "adpid=reward-smoke" \
            --data-urlencode "provider=reward-smoke" --data-urlencode 'extra=')"
        assert_field server_reward_replay isValid true "$replay"
        [[ "$(sql "SELECT assets FROM starfree_users WHERE uid=$OWNER_UID")" == "$(( owner_before + gift_award ))" ]] || {
            echo "Server reward balance is not idempotent" >&2
            exit 15
        }
        other_before="$(sql "SELECT assets FROM starfree_users WHERE uid=$OTHER_UID")"
        cross_user="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUserlog/adsServerNotify" \
            --data-urlencode "trans_id=$trans_id" --data-urlencode "user_id=$OTHER_UID" \
            --data-urlencode "sign=$signature" --data-urlencode "adpid=reward-smoke" \
            --data-urlencode "provider=reward-smoke" --data-urlencode 'extra=')"
        assert_field server_cross_user_replay isValid true "$cross_user"
        [[ "$(sql "SELECT assets FROM starfree_users WHERE uid=$OTHER_UID")" == "$other_before" ]] || {
            echo "Replayed server transaction credited another user" >&2
            exit 15
        }
        echo "ads_reward_mode=server"
    fi
fi

needs_review="$(sql "SELECT COUNT(*) FROM starfree_economy_operations
    WHERE (actor_uid IN ($OWNER_UID,$OTHER_UID) OR target_uid IN ($OWNER_UID,$OTHER_UID))
    AND state='needs_review'")"
[[ "$needs_review" == 0 ]] || {
    echo "Advertising reward operation requires manual reconciliation" >&2
    exit 16
}
echo "ads_reward_smoke=passed"
