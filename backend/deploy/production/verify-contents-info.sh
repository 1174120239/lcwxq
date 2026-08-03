#!/usr/bin/env bash
set -euo pipefail

# Disposable contentsInfo compatibility test. It compares the legacy and replacement
# payloads before cutover, proves Redis-only staff visibility, and verifies that both
# runtimes share the same Java-serialized 15-minute view key.
PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-content-info}
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
code() {
    printf '%s' "$1" | "$PYTHON_BIN" -c 'import json,sys; print(json.load(sys.stdin).get("code", ""))'
}
json_value() {
    local response="$1" key="$2"
    printf '%s' "$response" | "$PYTHON_BIN" -c \
        'import json,sys; value=json.load(sys.stdin).get(sys.argv[1], ""); print(value)' "$key"
}
canonical() {
    printf '%s' "$1" | "$PYTHON_BIN" -c \
        'import json,sys; print(json.dumps(json.load(sys.stdin), sort_keys=True, ensure_ascii=False, separators=(",",":")))'
}
java_serialized_key() {
    printf '%s' "$1" | "$PYTHON_BIN" -c '
import struct,sys
input_stream=getattr(sys.stdin,"buffer",sys.stdin)
output_stream=getattr(sys.stdout,"buffer",sys.stdout)
raw=input_stream.read()
if len(raw) > 65535: raise SystemExit("Redis key is too long")
output_stream.write(b"\xac\xed\x00\x05\x74" + struct.pack(">H", len(raw)) + raw)
'
}
delete_redis_key() {
    java_serialized_key "$1" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x DEL >/dev/null
}
redis_ttl() {
    java_serialized_key "$1" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x TTL | tr -d '\r\n'
}
assert_raw_title() {
    local label="$1" expected="$2" response="$3" actual
    actual="$(json_value "$response" title)"
    [[ "$actual" == "$expected" && "$(code "$response")" == "" ]] || {
        echo "$label is not a raw content object" >&2; echo "$response" >&2; exit 10;
    }
    echo "$label=raw-content"
}
assert_code() {
    local label="$1" expected="$2" response="$3" actual
    actual="$(code "$response")"
    [[ "$actual" == "$expected" ]] || {
        echo "$label code=$actual expected=$expected" >&2; echo "$response" >&2; exit 11;
    }
    echo "$label=code:$actual"
}
request_info() {
    local base="$1" mode="$2" ip="$3" agent="$4" token="${5:-}"
    local args=(-fsS -G "$base/SFreeContents/contentsInfo" -H "X-Real-IP: $ip"
        -H "X-Forwarded-For: $ip" -H "User-Agent: $agent"
        --data-urlencode "key=$CID" --data-urlencode "isMd=$mode")
    [[ -n "$token" ]] && args+=(--data-urlencode "token=$token")
    curl "${args[@]}"
}
reset_read() {
    local mode="$1" ip="$2" agent="$3"
    sql "UPDATE starfree_contents SET views=17 WHERE cid=$CID"
    delete_redis_key "${REDIS_PREFIX}_isRead_${ip}_${agent}_${CID}"
    delete_redis_key "${REDIS_PREFIX}_contentsInfo_${CID}_${mode}"
}

epoch="$(date +%s)"; suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
ADMIN_NAME="cci_a_$suffix"; TITLE="cci_title_$suffix"; SLUG="cci-post-$suffix"
CATEGORY_SLUG="cci-category-$suffix"; TAG_SLUG="cci-tag-$suffix"
TEST_PASSWORD='correct horse battery staple'; TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
ADMIN_UID=''; TOKEN=''; CID=''; CATEGORY_MID=''; TAG_MID=''
HEADER_FILE="/tmp/starfree-contents-info-headers-$$"
READ_IP='198.51.100.27'; READ_AGENT="cci-agent-$suffix"

cleanup() {
    local status=0
    set +e
    if [[ "$ADMIN_UID" =~ ^[1-9][0-9]*$ && -n "$TOKEN" ]]; then
        sql "UPDATE starfree_users SET authCode='$TOKEN' WHERE uid=$ADMIN_UID" >/dev/null 2>&1
        curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" --data-urlencode "token=$TOKEN" >/dev/null 2>&1 || status=1
    fi
    if [[ "$CID" =~ ^[1-9][0-9]*$ ]]; then
        sql "DELETE FROM starfree_comments WHERE cid=$CID; DELETE FROM starfree_fields WHERE cid=$CID; DELETE FROM starfree_relationships WHERE cid=$CID; DELETE FROM starfree_contents WHERE cid=$CID" >/dev/null 2>&1 || status=1
        for mode in 0 1; do delete_redis_key "${REDIS_PREFIX}_contentsInfo_${CID}_${mode}" >/dev/null 2>&1 || status=1; done
        for pair in "$READ_IP:$READ_AGENT" '198.51.100.28:cci-legacy' '198.51.100.29:cci-replacement' '127.0.0.1:cci-public'; do
            delete_redis_key "${REDIS_PREFIX}_isRead_${pair%%:*}_${pair#*:}_${CID}" >/dev/null 2>&1 || status=1
        done
    fi
    [[ "$CATEGORY_MID" =~ ^[1-9][0-9]*$ ]] && sql "DELETE FROM starfree_metas WHERE mid=$CATEGORY_MID" >/dev/null 2>&1 || true
    [[ "$TAG_MID" =~ ^[1-9][0-9]*$ ]] && sql "DELETE FROM starfree_metas WHERE mid=$TAG_MID" >/dev/null 2>&1 || true
    [[ "$ADMIN_UID" =~ ^[1-9][0-9]*$ ]] && sql "DELETE FROM starfree_users WHERE uid=$ADMIN_UID" >/dev/null 2>&1 || true
    sql "DELETE FROM starfree_contents WHERE slug='$SLUG'; DELETE FROM starfree_metas WHERE slug IN ('$CATEGORY_SLUG','$TAG_SLUG'); DELETE FROM starfree_users WHERE name='$ADMIN_NAME'" >/dev/null 2>&1 || status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || status=1
    [[ $status -eq 0 ]] || echo "WARNING: inspect contentsInfo cleanup for $suffix" >&2
    return $status
}
trap cleanup EXIT

sql "INSERT INTO starfree_users (name,password,mail,created,activated,logged,\`group\`,authCode,bantime,vip,experience) VALUES ('$ADMIN_NAME','$TEST_HASH','$ADMIN_NAME@invalid.local',$epoch,$epoch,0,'administrator',NULL,0,0,100000)"
ADMIN_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$ADMIN_NAME' LIMIT 1")"
params="$(printf '{\"name\":\"%s\",\"password\":\"%s\"}' "$ADMIN_NAME" "$TEST_PASSWORD")"
login="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeUsers/userLogin" --data-urlencode "params=$params")"
assert_code replacement_login 1 "$login"
TOKEN="$(sql "SELECT authCode FROM starfree_users WHERE uid=$ADMIN_UID")"
[[ "$TOKEN" =~ ^${ADMIN_NAME}[0-9a-f]{32}$ ]] || { echo "Unexpected login token" >&2; exit 12; }
sql "UPDATE starfree_users SET authCode=NULL WHERE uid=$ADMIN_UID"
echo "session_source=redis-only"

sql "INSERT INTO starfree_metas (name,slug,type,description,count,\`order\`,parent,imgurl,isrecommend) VALUES ('CCI category','$CATEGORY_SLUG','category','compat category',1,2,0,NULL,0),('CCI tag','$TAG_SLUG','tag','compat tag',1,3,0,NULL,0)"
CATEGORY_MID="$(sql "SELECT mid FROM starfree_metas WHERE slug='$CATEGORY_SLUG' LIMIT 1")"
TAG_MID="$(sql "SELECT mid FROM starfree_metas WHERE slug='$TAG_SLUG' LIMIT 1")"
sql "INSERT INTO starfree_contents (title,slug,created,modified,text,\`order\`,authorId,template,type,status,password,commentsNum,allowComment,allowPing,allowFeed,parent,views,likes,isrecommend,istop,isswiper,replyTime) VALUES ('$TITLE','$SLUG',$epoch,$epoch,'<!--markdown--># CCI full body\\n<img src=\"https://example.invalid/a.png\">\\n![manual](https://example.invalid/b.png)',4,$ADMIN_UID,'default','post','publish','hidden',2,'1','1','1',0,17,5,1,0,0,$epoch)"
CID="$(sql "SELECT cid FROM starfree_contents WHERE slug='$SLUG' LIMIT 1")"
sql "INSERT INTO starfree_fields (cid,name,type,str_value,int_value,float_value) VALUES ($CID,'cci_field','str','field-value',0,0); INSERT INTO starfree_relationships (cid,mid) VALUES ($CID,$CATEGORY_MID),($CID,$TAG_MID)"
echo "disposable_content=$CID"

for mode in 0 1; do
    reset_read "$mode" '198.51.100.28' 'cci-legacy'
    legacy="$(request_info "$LEGACY_URL" "$mode" '198.51.100.28' 'cci-legacy')"
    assert_raw_title "legacy_isMd_$mode" "$TITLE" "$legacy"
    reset_read "$mode" '198.51.100.29' 'cci-replacement'
    replacement="$(request_info "$REPLACEMENT_URL" "$mode" '198.51.100.29' 'cci-replacement')"
    assert_raw_title "replacement_isMd_$mode" "$TITLE" "$replacement"
    if [[ "$(canonical "$legacy")" != "$(canonical "$replacement")" ]]; then
        echo "contentsInfo payload mismatch for isMd=$mode" >&2
        echo "legacy=$(canonical "$legacy")" >&2
        echo "replacement=$(canonical "$replacement")" >&2
        exit 13
    fi
done
echo "direct_payload_compatibility=PASS"

sql "UPDATE starfree_contents SET status='waiting' WHERE cid=$CID"
delete_redis_key "${REDIS_PREFIX}_contentsInfo_${CID}_0"
legacy_denied="$(request_info "$LEGACY_URL" 0 '198.51.100.28' 'cci-legacy')"
replacement_denied="$(request_info "$REPLACEMENT_URL" 0 '198.51.100.29' 'cci-replacement')"
assert_code legacy_pending_anonymous 0 "$legacy_denied"
assert_code replacement_pending_anonymous 0 "$replacement_denied"
legacy_staff="$(request_info "$LEGACY_URL" 0 '198.51.100.28' 'cci-legacy' "$TOKEN")"
replacement_staff="$(request_info "$REPLACEMENT_URL" 0 '198.51.100.29' 'cci-replacement' "$TOKEN")"
assert_raw_title legacy_pending_staff "$TITLE" "$legacy_staff"
assert_raw_title replacement_pending_staff "$TITLE" "$replacement_staff"
echo "pending_visibility=PASS"

sql "UPDATE starfree_contents SET status='publish' WHERE cid=$CID"
reset_read 0 "$READ_IP" "$READ_AGENT"
legacy_first="$(request_info "$LEGACY_URL" 0 "$READ_IP" "$READ_AGENT")"
LEGACY_RESPONSE_VIEWS="$(json_value "$legacy_first" views)"
LEGACY_DB_VIEWS="$(sql "SELECT views FROM starfree_contents WHERE cid=$CID")"
LEGACY_TTL="$(redis_ttl "${REDIS_PREFIX}_isRead_${READ_IP}_${READ_AGENT}_${CID}")"
echo "legacy_read=response:$LEGACY_RESPONSE_VIEWS,db:$LEGACY_DB_VIEWS,ttl:$LEGACY_TTL"
[[ "$LEGACY_RESPONSE_VIEWS" == 17 && "$LEGACY_DB_VIEWS" == 18 ]] || { echo "Legacy first-read count mismatch" >&2; exit 14; }
replacement_duplicate="$(request_info "$REPLACEMENT_URL" 0 "$READ_IP" "$READ_AGENT")"
REPLACEMENT_RESPONSE_VIEWS="$(json_value "$replacement_duplicate" views)"
REPLACEMENT_DB_VIEWS="$(sql "SELECT views FROM starfree_contents WHERE cid=$CID")"
echo "replacement_duplicate=response:$REPLACEMENT_RESPONSE_VIEWS,db:$REPLACEMENT_DB_VIEWS"
[[ "$REPLACEMENT_RESPONSE_VIEWS" == 18 && "$REPLACEMENT_DB_VIEWS" == 18 ]] || { echo "Shared duplicate-read count mismatch" >&2; exit 14; }
TTL="$(redis_ttl "${REDIS_PREFIX}_isRead_${READ_IP}_${READ_AGENT}_${CID}")"
[[ "$TTL" =~ ^[0-9]+$ && "$TTL" -gt 0 && "$TTL" -le 900 ]] || { echo "Read TTL is invalid: $TTL" >&2; exit 14; }
echo "shared_read_key_ttl=$TTL"

if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    # This curl runs on the API host, so Nginx sets X-Real-IP to 127.0.0.1.
    reset_read 0 '127.0.0.1' 'cci-public'
    public="$(curl -fsS --resolve api.lcxqy.cn:443:127.0.0.1 -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeContents/contentsInfo" \
        -H 'X-Real-IP: 198.51.100.30' -H 'User-Agent: cci-public' \
        --data-urlencode "key=$CID" --data-urlencode isMd=0)"
    assert_raw_title public_contents_info "$TITLE" "$public"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || { echo "Public contentsInfo backend header mismatch" >&2; exit 15; }
    [[ "$(sql "SELECT views FROM starfree_contents WHERE cid=$CID")" == 18 ]] || { echo "Public first-read count mismatch" >&2; exit 15; }
    curl -fsS --resolve api.lcxqy.cn:443:127.0.0.1 -G "$PUBLIC_URL/SFreeContents/contentsInfo" -H 'X-Real-IP: 198.51.100.30' -H 'User-Agent: cci-public' --data-urlencode "key=$CID" --data-urlencode isMd=0 >/dev/null
    [[ "$(sql "SELECT views FROM starfree_contents WHERE cid=$CID")" == 18 ]] || { echo "Public duplicate read incremented views" >&2; exit 15; }
    echo "public_contents_info_backend=$EXPECTED_PUBLIC_BACKEND"
fi

echo "contents_info_direct_audit=PASS"
