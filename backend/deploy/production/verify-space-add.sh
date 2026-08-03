#!/usr/bin/env bash
set -euo pipefail

# Disposable addSpace compatibility test. It proves that a Redis-only login can
# publish through the replacement and then through the legacy backend while both
# implementations share the same Java-serialized _spaceNum key. No global API
# configuration is changed, and every test row/key is removed by the EXIT trap.

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-space-add}
DB_NAME=${DB_NAME:-lcxqy}

read_property() {
    local key="$1"
    awk -v wanted="$key" '
        { line=$0; sub(/^[[:space:]]+/, "", line)
          if (index(line,wanted)==1) { value=substr(line,length(wanted)+1)
            if (value ~ /^[[:space:]]*=/) { sub(/^[[:space:]]*=[[:space:]]*/, "", value); print value; exit }
          }
        }' "$PROPERTIES_FILE"
}

if command -v python3 >/dev/null 2>&1; then PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1; then PYTHON_BIN=python
else echo "Python is required" >&2; exit 2; fi
for cmd in awk curl mysql redis-cli; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "Missing $cmd" >&2; exit 2; }
done

DB_USERNAME=${DB_USERNAME:-$(read_property spring.datasource.username)}
DB_PASSWORD=${DB_PASSWORD:-$(read_property spring.datasource.password)}
REDIS_HOST=${REDIS_HOST:-$(read_property spring.redis.host)}
REDIS_PORT=${REDIS_PORT:-$(read_property spring.redis.port)}
REDIS_PASSWORD=${REDIS_PASSWORD-$(read_property spring.redis.password)}
REDIS_PREFIX=${REDIS_PREFIX:-$(read_property web.prefix)}
REDIS_HOST=${REDIS_HOST:-127.0.0.1}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_PREFIX=${REDIS_PREFIX:-starfree}
[[ -n "$DB_USERNAME" && -n "$DB_PASSWORD" ]] || { echo "Database credentials are missing" >&2; exit 2; }
export MYSQL_PWD="$DB_PASSWORD"
if [[ -n "$REDIS_PASSWORD" ]]; then export REDISCLI_AUTH="$REDIS_PASSWORD"; else unset REDISCLI_AUTH || true; fi

sql() {
    mysql --protocol=TCP --host=127.0.0.1 --user="$DB_USERNAME" \
        --batch --skip-column-names "$DB_NAME" --execute="$1"
}
code() { printf '%s' "$1" | "$PYTHON_BIN" -c 'import json,sys; print(json.load(sys.stdin).get("code", ""))'; }
assert_code() {
    local label="$1" expected="$2" response="$3" actual
    actual="$(code "$response")"
    [[ "$actual" == "$expected" ]] || { echo "$label code=$actual expected=$expected" >&2; echo "$response" >&2; exit 10; }
    echo "$label=code:$actual"
}

# RedisTemplate<Object,Object> in both JARs uses JDK serialization. ASCII keys
# serialize as AC ED 00 05 74 + a two-byte length + the key bytes.
java_serialized_key() {
    printf '%s' "$1" | "$PYTHON_BIN" -c '
import struct,sys
input_stream=getattr(sys.stdin,"buffer",sys.stdin)
output_stream=getattr(sys.stdout,"buffer",sys.stdout)
value=input_stream.read()
if len(value)>65535: raise SystemExit("Redis key too long")
output_stream.write(b"\xac\xed\x00\x05\x74"+struct.pack(">H",len(value))+value)
'
}
redis_value() {
    local key="$1"
    java_serialized_key "$key" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x GET | "$PYTHON_BIN" -c '
import struct,sys
input_stream=getattr(sys.stdin,"buffer",sys.stdin)
data=input_stream.read()
if len(data)<7 or data[:5]!=b"\xac\xed\x00\x05\x74": print(""); raise SystemExit(0)
size=struct.unpack(">H",data[5:7])[0]
print(data[7:7+size].decode("utf-8"))
'
}
redis_ttl() {
    local key="$1"
    java_serialized_key "$key" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x TTL | tr -d '\r\n'
}
delete_redis_key() {
    local key="$1"
    java_serialized_key "$key" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x DEL >/dev/null
}

epoch="$(date +%s)"
suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
USER_NAME="csa_u_$suffix"
TEST_PASSWORD='correct horse battery staple'
TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
PUBLIC_TEXT="csa_public_$suffix"
PRIVATE_TEXT="csa_private_$suffix"
LEGACY_TEXT="csa_legacy_$suffix"
PLUGIN_TEXT="csa_plugin_$suffix"
UID_VALUE=''
TOKEN=''
PUBLIC_ID=''
PRIVATE_ID=''
LEGACY_ID=''
HEADER_FILE="/tmp/starfree-space-add-headers-$$"

cleanup() {
    local status=0
    set +e
    if [[ "$UID_VALUE" =~ ^[1-9][0-9]*$ && -n "$TOKEN" ]]; then
        sql "UPDATE starfree_users SET authCode='$TOKEN' WHERE uid=$UID_VALUE" >/dev/null 2>&1
        curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" --data-urlencode "token=$TOKEN" >/dev/null 2>&1 || status=1
    fi
    if [[ "$UID_VALUE" =~ ^[1-9][0-9]*$ ]]; then
        sql "DELETE FROM starfree_userlog WHERE uid=$UID_VALUE OR toid=$UID_VALUE" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_inbox WHERE uid=$UID_VALUE OR touid=$UID_VALUE" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_fan WHERE uid=$UID_VALUE OR touid=$UID_VALUE" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_space WHERE uid=$UID_VALUE" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_users WHERE uid=$UID_VALUE" >/dev/null 2>&1 || status=1
        for suffix_key in _spaceNum _isAddSpace _isIntercept _silence; do
            delete_redis_key "${REDIS_PREFIX}_${UID_VALUE}${suffix_key}" >/dev/null 2>&1 || status=1
        done
    fi
    sql "DELETE FROM starfree_space WHERE text IN ('$PUBLIC_TEXT','$PRIVATE_TEXT','$LEGACY_TEXT','$PLUGIN_TEXT')" >/dev/null 2>&1 || status=1
    sql "DELETE FROM starfree_users WHERE name='$USER_NAME'" >/dev/null 2>&1 || status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || status=1
    [[ $status -eq 0 ]] || echo "WARNING: inspect addSpace cleanup for $suffix" >&2
    return $status
}
trap cleanup EXIT

IFS=$'\t' read -r BAN_ROBOTS SILENCE_TIME INTERCEPT_TIME POST_MAX SPACE_MIN_EXP \
    SPACE_AUDIT POST_EXP IDENTIFY_SM IDENTIFY_LV <<<"$(sql \
    "SELECT banRobots,silenceTime,interceptTime,postMax,spaceMinExp,spaceAudit,postExp,identifysmPost,identifylvPost FROM starfree_apiconfig ORDER BY id LIMIT 1")"
[[ "$IDENTIFY_SM" == 0 && "$IDENTIFY_LV" == 0 ]] || { echo "Disposable user cannot satisfy enabled identity gates" >&2; exit 11; }
REQUIRED_POSTS=3
[[ "$VERIFY_PUBLIC" == 1 ]] && REQUIRED_POSTS=4
[[ "$POST_MAX" =~ ^[0-9]+$ && "$POST_MAX" -ge "$REQUIRED_POSTS" ]] || { echo "postMax must allow $REQUIRED_POSTS disposable posts" >&2; exit 11; }
INITIAL_EXP=$(( SPACE_MIN_EXP + 1000 ))
echo "config=banRobots:$BAN_ROBOTS,postMax:$POST_MAX,spaceAudit:$SPACE_AUDIT,postExp:$POST_EXP"

sql "INSERT INTO starfree_users (name,password,mail,created,activated,logged,\`group\`,authCode,bantime,vip,experience) VALUES
    ('$USER_NAME','$TEST_HASH','$USER_NAME@invalid.local',$epoch,$epoch,0,'contributor',NULL,0,0,$INITIAL_EXP)"
UID_VALUE="$(sql "SELECT uid FROM starfree_users WHERE name='$USER_NAME' LIMIT 1")"
[[ "$UID_VALUE" =~ ^[1-9][0-9]*$ ]] || { echo "Disposable user id lookup failed" >&2; exit 12; }
echo "disposable_uid=$UID_VALUE"

params="$(printf '{\"name\":\"%s\",\"password\":\"%s\"}' "$USER_NAME" "$TEST_PASSWORD")"
login="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeUsers/userLogin" --data-urlencode "params=$params")"
assert_code replacement_login 1 "$login"
TOKEN="$(sql "SELECT authCode FROM starfree_users WHERE uid=$UID_VALUE")"
[[ "$TOKEN" =~ ^${USER_NAME}[0-9a-f]{32}$ ]] || { echo "Unexpected token format" >&2; exit 13; }
sql "UPDATE starfree_users SET authCode=NULL WHERE uid=$UID_VALUE"
[[ "$(sql "SELECT COUNT(*) FROM starfree_users WHERE authCode='$TOKEN'")" == 0 ]] || exit 14
echo "mysql_token_rows=0"

replacement_public="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/addSpace" \
    --data-urlencode "token=$TOKEN" --data-urlencode type=0 \
    --data-urlencode "text=$PUBLIC_TEXT" --data-urlencode onlyMe=0)"
assert_code replacement_public 1 "$replacement_public"
PUBLIC_ID="$(sql "SELECT id FROM starfree_space WHERE uid=$UID_VALUE AND text='$PUBLIC_TEXT' LIMIT 1")"
[[ "$PUBLIC_ID" =~ ^[1-9][0-9]*$ ]] || { echo "Public row missing" >&2; exit 15; }
EXPECTED_STATUS=$(( SPACE_AUDIT == 1 ? 0 : 1 ))
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$PUBLIC_ID")" == "$EXPECTED_STATUS" ]] || { echo "Public status mismatch" >&2; exit 15; }
[[ "$(redis_value "${REDIS_PREFIX}_${UID_VALUE}_spaceNum")" == 1 ]] || { echo "Replacement did not create shared _spaceNum=1" >&2; exit 16; }
POST_TTL="$(redis_ttl "${REDIS_PREFIX}_${UID_VALUE}_spaceNum")"
[[ "$POST_TTL" =~ ^[1-9][0-9]*$ && "$POST_TTL" -le 86400 ]] || { echo "Invalid _spaceNum TTL: $POST_TTL" >&2; exit 16; }
echo "replacement_space_num=1,ttl:$POST_TTL"

[[ "$BAN_ROBOTS" == 1 ]] && sleep 6
replacement_private="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/addSpace" \
    --data-urlencode "token=$TOKEN" --data-urlencode type=0 \
    --data-urlencode "text=$PRIVATE_TEXT" --data-urlencode onlyMe=1)"
assert_code replacement_private 1 "$replacement_private"
PRIVATE_ID="$(sql "SELECT id FROM starfree_space WHERE uid=$UID_VALUE AND text='$PRIVATE_TEXT' LIMIT 1")"
[[ "$PRIVATE_ID" =~ ^[1-9][0-9]*$ ]] || { echo "Private row missing" >&2; exit 17; }
[[ "$(sql "SELECT CONCAT(status,':',onlyMe) FROM starfree_space WHERE id=$PRIVATE_ID")" == "$EXPECTED_STATUS:1" ]] || { echo "Private row flags mismatch" >&2; exit 17; }
[[ "$(redis_value "${REDIS_PREFIX}_${UID_VALUE}_spaceNum")" == 2 ]] || { echo "Replacement did not advance shared _spaceNum=2" >&2; exit 18; }

plugin="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/addSpace" \
    --data-urlencode "token=$TOKEN" --data-urlencode type=6 \
    --data-urlencode "text=$PLUGIN_TEXT" --data-urlencode onlyMe=0)"
assert_code replacement_plugin_rejected 0 "$plugin"
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE uid=$UID_VALUE AND text='$PLUGIN_TEXT'")" == 0 ]] || { echo "Plugin row must not exist" >&2; exit 19; }

EXPECTED_REPLACEMENT_LOGS=0
EXPECTED_REPLACEMENT_EXP=$INITIAL_EXP
if [[ "$EXPECTED_STATUS" == 1 && "$POST_EXP" -gt 0 ]]; then
    EXPECTED_REPLACEMENT_LOGS=2
    EXPECTED_REPLACEMENT_EXP=$(( INITIAL_EXP + POST_EXP * 2 ))
fi
[[ "$(sql "SELECT COUNT(*) FROM starfree_userlog WHERE uid=$UID_VALUE AND type='postExp'")" == "$EXPECTED_REPLACEMENT_LOGS" ]] || { echo "Replacement postExp log count mismatch" >&2; exit 20; }
[[ "$(sql "SELECT experience FROM starfree_users WHERE uid=$UID_VALUE")" == "$EXPECTED_REPLACEMENT_EXP" ]] || { echo "Replacement experience mismatch" >&2; exit 20; }
USER_ACTIVITY="$(sql "SELECT CONCAT(posttime,':',IFNULL(ip,'')) FROM starfree_users WHERE uid=$UID_VALUE")"
[[ "$USER_ACTIVITY" =~ ^[1-9][0-9]*:.+ ]] || { echo "User posttime/ip was not updated" >&2; exit 21; }
echo "replacement_rows=$PUBLIC_ID,$PRIVATE_ID,status:$EXPECTED_STATUS,postExpLogs:$EXPECTED_REPLACEMENT_LOGS"

[[ "$BAN_ROBOTS" == 1 ]] && sleep 6
legacy="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/addSpace" \
    --data-urlencode "token=$TOKEN" --data-urlencode type=0 \
    --data-urlencode "text=$LEGACY_TEXT" --data-urlencode onlyMe=0)"
assert_code legacy_public 1 "$legacy"
LEGACY_ID="$(sql "SELECT id FROM starfree_space WHERE uid=$UID_VALUE AND text='$LEGACY_TEXT' LIMIT 1")"
[[ "$LEGACY_ID" =~ ^[1-9][0-9]*$ ]] || { echo "Legacy row missing" >&2; exit 22; }
[[ "$(redis_value "${REDIS_PREFIX}_${UID_VALUE}_spaceNum")" == 3 ]] || { echo "Legacy backend did not reuse replacement _spaceNum" >&2; exit 23; }
echo "legacy_shared_space_num=3,row:$LEGACY_ID"

if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    [[ "$BAN_ROBOTS" == 1 ]] && sleep 6
    PUBLIC_TEXT="csa_public_route_$suffix"
    public_response="$(curl -fsS -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeSpace/addSpace" \
        --data-urlencode "token=$TOKEN" --data-urlencode type=0 \
        --data-urlencode "text=$PUBLIC_TEXT" --data-urlencode onlyMe=0)"
    assert_code public_add 1 "$public_response"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || { echo "Public addSpace backend header mismatch" >&2; exit 24; }
    echo "public_add_backend=$EXPECTED_PUBLIC_BACKEND"
fi

echo "space_add_direct_audit=PASS"
