#!/usr/bin/env bash
set -euo pipefail

# Disposable contentsAdd compatibility test. Ordinary post/video publishing is
# compared across both runtimes. Closed features are required to carry the
# delegate header, proving that the replacement sent them to port 8081.
PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-content-add}
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
for cmd in awk curl mysql paste redis-cli sha256sum sort; do
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
json_value() {
    local response="$1" key="$2"
    printf '%s' "$response" | "$PYTHON_BIN" -c \
        'import json,sys; print(json.load(sys.stdin).get(sys.argv[1], ""))' "$key"
}
assert_success() {
    local label="$1" response="$2"
    [[ "$(json_value "$response" code)" == 1 && "$(json_value "$response" data)" == 1 ]] || {
        echo "$label did not return the legacy code/data success envelope" >&2
        echo "$response" >&2
        exit 10
    }
    echo "$label=code:1,data:1"
}
assert_failure() {
    local label="$1" response="$2"
    [[ "$(json_value "$response" code)" == 0 ]] || {
        echo "$label did not return a legacy failure envelope" >&2
        echo "$response" >&2
        exit 11
    }
}

# Both JARs use JDK serialization for Redis keys and string values.
java_serialized_key() {
    printf '%s' "$1" | "$PYTHON_BIN" -c '
import struct,sys
source=getattr(sys.stdin,"buffer",sys.stdin); target=getattr(sys.stdout,"buffer",sys.stdout)
raw=source.read()
if len(raw)>65535: raise SystemExit("Redis key is too long")
target.write(b"\xac\xed\x00\x05\x74"+struct.pack(">H",len(raw))+raw)
'
}
redis_value() {
    java_serialized_key "$1" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x GET | "$PYTHON_BIN" -c '
import struct,sys
source=getattr(sys.stdin,"buffer",sys.stdin); data=source.read()
if len(data)<7 or data[:5]!=b"\xac\xed\x00\x05\x74": print(""); raise SystemExit(0)
size=struct.unpack(">H",data[5:7])[0]
print(data[7:7+size].decode("utf-8"))
'
}
redis_ttl() {
    java_serialized_key "$1" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x TTL | tr -d '\r\n'
}
delete_redis_key() {
    java_serialized_key "$1" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x DEL >/dev/null
}

epoch="$(date +%s)"
suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
USER_ONE="cca_u1_$suffix"
USER_TWO="cca_u2_$suffix"
ADMIN_NAME="cca_a_$suffix"
TEST_PASSWORD='correct horse battery staple'
TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
CATEGORY_SLUG="cca-category-$suffix"
TAG_SLUG="cca-tag-$suffix"
TITLE_PREFIX="cca_${suffix}_"
USER_ONE_UID=''; USER_TWO_UID=''; ADMIN_UID=''
USER_ONE_TOKEN=''; USER_TWO_TOKEN=''; ADMIN_TOKEN=''
CATEGORY_MID=''; TAG_MID=''
CREATED_CIDS=''
HEADER_FILE="/tmp/starfree-contents-add-headers-$$"

cleanup() {
    local status=0 uid token cid discovered_cids
    set +e
    for pair in "$USER_ONE_UID:$USER_ONE_TOKEN" "$USER_TWO_UID:$USER_TWO_TOKEN" "$ADMIN_UID:$ADMIN_TOKEN"; do
        uid="${pair%%:*}"; token="${pair#*:}"
        if [[ "$uid" =~ ^[1-9][0-9]*$ && -n "$token" ]]; then
            sql "UPDATE starfree_users SET authCode='$token' WHERE uid=$uid" >/dev/null 2>&1
            curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" --data-urlencode "token=$token" >/dev/null 2>&1 || status=1
        fi
    done
    discovered_cids="$(sql "SELECT cid FROM starfree_contents WHERE LEFT(title,${#TITLE_PREFIX})='$TITLE_PREFIX'" 2>/dev/null)"
    if [[ -n "$CREATED_CIDS$discovered_cids" ]]; then
        for cid in $CREATED_CIDS $discovered_cids; do
            [[ "$cid" =~ ^[1-9][0-9]*$ ]] || continue
            sql "DELETE FROM starfree_comments WHERE cid=$cid; DELETE FROM starfree_fields WHERE cid=$cid; DELETE FROM starfree_relationships WHERE cid=$cid; DELETE FROM starfree_contents WHERE cid=$cid" >/dev/null 2>&1 || status=1
            delete_redis_key "${REDIS_PREFIX}_contentsInfo_${cid}_0" >/dev/null 2>&1 || status=1
            delete_redis_key "${REDIS_PREFIX}_contentsInfo_${cid}_1" >/dev/null 2>&1 || status=1
        done
    fi
    for uid in "$USER_ONE_UID" "$USER_TWO_UID" "$ADMIN_UID"; do
        if [[ "$uid" =~ ^[1-9][0-9]*$ ]]; then
            sql "DELETE FROM starfree_userlog WHERE uid=$uid OR toid=$uid; DELETE FROM starfree_inbox WHERE uid=$uid OR touid=$uid; DELETE FROM starfree_paylog WHERE uid=$uid; DELETE FROM starfree_space WHERE uid=$uid; DELETE FROM starfree_fan WHERE uid=$uid OR touid=$uid" >/dev/null 2>&1 || status=1
            for suffix_key in _postNum _isRepeated _silence; do
                delete_redis_key "${REDIS_PREFIX}_${uid}${suffix_key}" >/dev/null 2>&1 || status=1
            done
        fi
    done
    [[ "$CATEGORY_MID" =~ ^[1-9][0-9]*$ ]] && sql "DELETE FROM starfree_metas WHERE mid=$CATEGORY_MID" >/dev/null 2>&1 || true
    [[ "$TAG_MID" =~ ^[1-9][0-9]*$ ]] && sql "DELETE FROM starfree_metas WHERE mid=$TAG_MID" >/dev/null 2>&1 || true
    sql "DELETE FROM starfree_users WHERE name IN ('$USER_ONE','$USER_TWO','$ADMIN_NAME'); DELETE FROM starfree_contents WHERE LEFT(title,${#TITLE_PREFIX})='$TITLE_PREFIX'; DELETE FROM starfree_metas WHERE slug IN ('$CATEGORY_SLUG','$TAG_SLUG')" >/dev/null 2>&1 || status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || status=1
    [[ $status -eq 0 ]] || echo "WARNING: inspect contentsAdd cleanup for $suffix" >&2
    return $status
}
trap cleanup EXIT

PREEXISTING_SQL="$(sql "SELECT (SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='cca_') + (SELECT COUNT(*) FROM starfree_contents WHERE LEFT(title,4)='cca_') + (SELECT COUNT(*) FROM starfree_metas WHERE LEFT(slug,4)='cca-')")"
[[ "$PREEXISTING_SQL" == 0 ]] || { echo "Pre-existing cca_ SQL residue: $PREEXISTING_SQL" >&2; exit 12; }
echo "preexisting_sql_residue=0"

IFS=$'\t' read -r CONTENT_AUDIT POST_MAX BAN_ROBOTS SILENCE_TIME POST_EXP <<<"$(sql \
    "SELECT contentAuditlevel,postMax,banRobots,silenceTime,postExp FROM starfree_apiconfig ORDER BY id LIMIT 1")"
[[ "$CONTENT_AUDIT" == 2 ]] || { echo "contentAuditlevel must remain 2 for the pending-status cutover test" >&2; exit 12; }
[[ "$POST_MAX" =~ ^[0-9]+$ && "$POST_MAX" -ge 2 ]] || { echo "postMax must allow two disposable posts" >&2; exit 12; }
echo "config=contentAudit:$CONTENT_AUDIT,postMax:$POST_MAX,banRobots:$BAN_ROBOTS,postExp:$POST_EXP"

sql "INSERT INTO starfree_users (name,password,mail,created,activated,logged,\`group\`,authCode,bantime,vip,experience) VALUES
    ('$USER_ONE','$TEST_HASH','$USER_ONE@invalid.local',$epoch,$epoch,0,'contributor',NULL,0,0,100000),
    ('$USER_TWO','$TEST_HASH','$USER_TWO@invalid.local',$epoch,$epoch,0,'contributor',NULL,0,0,100000),
    ('$ADMIN_NAME','$TEST_HASH','$ADMIN_NAME@invalid.local',$epoch,$epoch,0,'administrator',NULL,0,0,100000)"
USER_ONE_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$USER_ONE' LIMIT 1")"
USER_TWO_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$USER_TWO' LIMIT 1")"
ADMIN_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$ADMIN_NAME' LIMIT 1")"
for uid in "$USER_ONE_UID" "$USER_TWO_UID" "$ADMIN_UID"; do
    [[ "$uid" =~ ^[1-9][0-9]*$ ]] || { echo "Disposable user id lookup failed" >&2; exit 13; }
done

login_user() {
    local name="$1" uid="$2" params response
    params="$(printf '{\"name\":\"%s\",\"password\":\"%s\"}' "$name" "$TEST_PASSWORD")"
    response="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeUsers/userLogin" --data-urlencode "params=$params")"
    [[ "$(json_value "$response" code)" == 1 ]] || { echo "Login failed for $name" >&2; exit 14; }
    LOGIN_TOKEN="$(sql "SELECT authCode FROM starfree_users WHERE uid=$uid")"
    [[ "$LOGIN_TOKEN" =~ ^${name}[0-9a-f]{32}$ ]] || { echo "Unexpected token for $name" >&2; exit 14; }
    sql "UPDATE starfree_users SET authCode=NULL WHERE uid=$uid"
}
login_user "$USER_ONE" "$USER_ONE_UID"; USER_ONE_TOKEN="$LOGIN_TOKEN"
login_user "$USER_TWO" "$USER_TWO_UID"; USER_TWO_TOKEN="$LOGIN_TOKEN"
login_user "$ADMIN_NAME" "$ADMIN_UID"; ADMIN_TOKEN="$LOGIN_TOKEN"
echo "redis_only_sessions=3"

sql "INSERT INTO starfree_metas (name,slug,type,description,count,\`order\`,parent,imgurl,isrecommend) VALUES
    ('CCA category','$CATEGORY_SLUG','category','compat category',0,0,0,NULL,0),
    ('CCA tag','$TAG_SLUG','tag','compat tag',0,0,0,NULL,0)"
CATEGORY_MID="$(sql "SELECT mid FROM starfree_metas WHERE slug='$CATEGORY_SLUG' LIMIT 1")"
TAG_MID="$(sql "SELECT mid FROM starfree_metas WHERE slug='$TAG_SLUG' LIMIT 1")"

wait_for_burst_key() {
    if [[ "$BAN_ROBOTS" == 1 ]]; then
        sleep 4
    fi
}
publish() {
    local base="$1" token="$2" title="$3" type="$4" wire_text="$5" params
    params="$(printf '{\"title\":\"%s\",\"category\":\"%s,\",\"tag\":\"%s,\",\"sid\":-1,\"type\":\"%s\"}' \
        "$title" "$CATEGORY_MID" "$TAG_MID" "$type")"
    curl -fsS -X POST "$base/SFreeContents/contentsAdd" \
        -H 'Content-Type: application/x-www-form-urlencoded' \
        --data-urlencode "token=$token" --data-urlencode "params=$params" \
        --data-urlencode "text=$wire_text" --data-urlencode isMd=1 \
        --data-urlencode isPaid=0 --data-urlencode isDraft=0 --data-urlencode isSpace=0
}
remember_cid() {
    local title="$1" cid
    cid="$(sql "SELECT cid FROM starfree_contents WHERE title='$title' ORDER BY cid DESC LIMIT 1")"
    [[ "$cid" =~ ^[1-9][0-9]*$ ]] || { echo "Published row is missing for $title" >&2; exit 15; }
    CREATED_CIDS="$CREATED_CIDS $cid"
    REMEMBERED_CID="$cid"
}

WIRE_TEXT="cca_line1_$suffix||rn||cca_line2_$suffix"
EXPECTED_TEXT="<!--markdown-->cca_line1_$suffix
cca_line2_$suffix"
EXPECTED_HASH="$(printf '%s' "$EXPECTED_TEXT" | sha256sum | awk '{print $1}')"

R_POST_TITLE="${TITLE_PREFIX}replacement_post"
response="$(publish "$REPLACEMENT_URL" "$USER_ONE_TOKEN" "$R_POST_TITLE" post "$WIRE_TEXT")"
assert_success replacement_post "$response"
remember_cid "$R_POST_TITLE"; R_POST_CID="$REMEMBERED_CID"
[[ "$(redis_value "${REDIS_PREFIX}_${USER_ONE_UID}_postNum")" == 1 ]] || { echo "Replacement did not create _postNum=1" >&2; exit 16; }
POST_TTL="$(redis_ttl "${REDIS_PREFIX}_${USER_ONE_UID}_postNum")"
[[ "$POST_TTL" =~ ^[1-9][0-9]*$ && "$POST_TTL" -le 86400 ]] || { echo "Invalid post counter TTL: $POST_TTL" >&2; exit 16; }

wait_for_burst_key
L_VIDEO_TITLE="${TITLE_PREFIX}legacy_video"
response="$(publish "$LEGACY_URL" "$USER_ONE_TOKEN" "$L_VIDEO_TITLE" video "$WIRE_TEXT")"
assert_success legacy_video "$response"
remember_cid "$L_VIDEO_TITLE"; L_VIDEO_CID="$REMEMBERED_CID"
[[ "$(redis_value "${REDIS_PREFIX}_${USER_ONE_UID}_postNum")" == 2 ]] || { echo "Legacy did not reuse replacement _postNum" >&2; exit 17; }
echo "shared_counter_replacement_to_legacy=1,2"

L_POST_TITLE="${TITLE_PREFIX}legacy_post"
response="$(publish "$LEGACY_URL" "$USER_TWO_TOKEN" "$L_POST_TITLE" post "$WIRE_TEXT")"
assert_success legacy_post "$response"
remember_cid "$L_POST_TITLE"; L_POST_CID="$REMEMBERED_CID"
[[ "$(redis_value "${REDIS_PREFIX}_${USER_TWO_UID}_postNum")" == 1 ]] || { echo "Legacy did not create _postNum=1" >&2; exit 18; }

wait_for_burst_key
R_VIDEO_TITLE="${TITLE_PREFIX}replacement_video"
response="$(publish "$REPLACEMENT_URL" "$USER_TWO_TOKEN" "$R_VIDEO_TITLE" video "$WIRE_TEXT")"
assert_success replacement_video "$response"
remember_cid "$R_VIDEO_TITLE"; R_VIDEO_CID="$REMEMBERED_CID"
[[ "$(redis_value "${REDIS_PREFIX}_${USER_TWO_UID}_postNum")" == 2 ]] || { echo "Replacement did not reuse legacy _postNum" >&2; exit 19; }
echo "shared_counter_legacy_to_replacement=1,2"

content_shape() {
    sql "SELECT CONCAT_WS('|',type,status,SHA2(text,256),\`order\`,IFNULL(template,'<NULL>'),IFNULL(password,'<NULL>'),commentsNum,allowComment,allowPing,allowFeed,parent,views,likes,isrecommend,istop,isswiper) FROM starfree_contents WHERE cid=$1"
}
assert_content() {
    local cid="$1" expected_type="$2" shape relationships
    shape="$(content_shape "$cid")"
    [[ "$(sql "SELECT slug FROM starfree_contents WHERE cid=$cid")" == "$cid" ]] || { echo "Numeric slug mismatch for cid=$cid" >&2; exit 20; }
    [[ "$shape" == "$expected_type|waiting|$EXPECTED_HASH|0|<NULL>|<NULL>|0|1|1|1|0|0|0|0|0|0" ]] || {
        echo "Content defaults mismatch for cid=$cid: $shape" >&2; exit 20;
    }
    relationships="$(sql "SELECT GROUP_CONCAT(mid ORDER BY mid) FROM starfree_relationships WHERE cid=$cid")"
    [[ "$relationships" == "$(printf '%s\n%s\n' "$CATEGORY_MID" "$TAG_MID" | sort -n | paste -sd, -)" ]] || {
        echo "Relationship mismatch for cid=$cid: $relationships" >&2; exit 20;
    }
}
assert_content "$R_POST_CID" post
assert_content "$L_POST_CID" post
assert_content "$L_VIDEO_CID" video
assert_content "$R_VIDEO_CID" video
[[ "$(content_shape "$R_POST_CID")" == "$(content_shape "$L_POST_CID")" ]] || { echo "Post rows differ by runtime" >&2; exit 21; }
[[ "$(content_shape "$R_VIDEO_CID")" == "$(content_shape "$L_VIDEO_CID")" ]] || { echo "Video rows differ by runtime" >&2; exit 21; }
echo "row_and_relationship_compatibility=PASS"

ADMIN_TITLE="${TITLE_PREFIX}admin_post"
response="$(publish "$REPLACEMENT_URL" "$ADMIN_TOKEN" "$ADMIN_TITLE" post "$WIRE_TEXT")"
assert_success replacement_admin_post "$response"
remember_cid "$ADMIN_TITLE"; ADMIN_CID="$REMEMBERED_CID"
[[ "$(sql "SELECT status FROM starfree_contents WHERE cid=$ADMIN_CID")" == publish ]] || { echo "Staff post was not published" >&2; exit 22; }
[[ -z "$(redis_value "${REDIS_PREFIX}_${ADMIN_UID}_postNum")" ]] || { echo "Staff post touched _postNum" >&2; exit 22; }
echo "pending_and_staff_status=PASS"

assert_delegated() {
    local label="$1" params="$2" flag_name="${3:-}" flag_value="${4:-}" response
    local args=(-fsS -D "$HEADER_FILE" -X POST "$REPLACEMENT_URL/SFreeContents/contentsAdd"
        --data-urlencode token=cca_invalid_token --data-urlencode "params=$params"
        --data-urlencode text=cca_delegate_probe)
    [[ -n "$flag_name" ]] && args+=(--data-urlencode "$flag_name=$flag_value")
    response="$(curl "${args[@]}")"
    assert_failure "$label" "$response"
    grep -qi '^X-Starfree-Delegate: legacy-contents-add' "$HEADER_FILE" || {
        echo "$label was not delegated to the legacy backend" >&2; exit 23;
    }
    echo "$label=legacy-delegate"
}
BASE_DELEGATE_PARAMS="$(printf '{\"title\":\"%sdelegate\",\"sid\":-1,\"type\":\"post\"}' "$TITLE_PREFIX")"
assert_delegated paid "$BASE_DELEGATE_PARAMS" isPaid 1
assert_delegated draft "$BASE_DELEGATE_PARAMS" isDraft 1
assert_delegated linked_space "$BASE_DELEGATE_PARAMS" isSpace 1
assert_delegated attached_shop "$(printf '{\"title\":\"%sshop\",\"sid\":1,\"type\":\"post\"}' "$TITLE_PREFIX")"
assert_delegated unknown_type "$(printf '{\"title\":\"%splugin\",\"sid\":-1,\"type\":\"plugin\"}' "$TITLE_PREFIX")"

if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    wait_for_burst_key
    PUBLIC_TITLE="${TITLE_PREFIX}public_post"
    PUBLIC_PARAMS="$(printf '{\"title\":\"%s\",\"category\":\"%s,\",\"tag\":\"%s,\",\"sid\":-1,\"type\":\"post\"}' \
        "$PUBLIC_TITLE" "$CATEGORY_MID" "$TAG_MID")"
    public_response="$(curl -fsS --resolve api.lcxqy.cn:443:127.0.0.1 -D "$HEADER_FILE" -X POST \
        "$PUBLIC_URL/SFreeContents/contentsAdd" --data-urlencode "token=$ADMIN_TOKEN" \
        --data-urlencode "params=$PUBLIC_PARAMS" --data-urlencode "text=$WIRE_TEXT" --data-urlencode isMd=1)"
    assert_success public_post "$public_response"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || { echo "Public contentsAdd backend header mismatch" >&2; exit 24; }
    if grep -qi '^X-Starfree-Delegate:' "$HEADER_FILE"; then echo "Ordinary public post was unexpectedly delegated" >&2; exit 24; fi
    remember_cid "$PUBLIC_TITLE"; PUBLIC_CID="$REMEMBERED_CID"
    [[ "$(sql "SELECT CONCAT(status,'|',slug) FROM starfree_contents WHERE cid=$PUBLIC_CID")" == "publish|$PUBLIC_CID" ]] || exit 24

    public_delegate="$(curl -fsS --resolve api.lcxqy.cn:443:127.0.0.1 -D "$HEADER_FILE" -X POST \
        "$PUBLIC_URL/SFreeContents/contentsAdd" --data-urlencode token=cca_invalid_token \
        --data-urlencode "params=$BASE_DELEGATE_PARAMS" --data-urlencode text=cca_delegate_probe \
        --data-urlencode isDraft=1)"
    assert_failure public_draft_delegate "$public_delegate"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || exit 24
    grep -qi '^X-Starfree-Delegate: legacy-contents-add' "$HEADER_FILE" || exit 24
    echo "public_contents_add_backend=$EXPECTED_PUBLIC_BACKEND"
fi

# A successful audit is reported only after the same cleanup used on failures
# has run and every known SQL/Redis artifact has been proved absent.
cleanup
trap - EXIT
POST_SQL_RESIDUE="$(sql "SELECT (SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='cca_') + (SELECT COUNT(*) FROM starfree_contents WHERE LEFT(title,4)='cca_') + (SELECT COUNT(*) FROM starfree_metas WHERE LEFT(slug,4)='cca-')")"
[[ "$POST_SQL_RESIDUE" == 0 ]] || { echo "Post-cleanup cca_ SQL residue: $POST_SQL_RESIDUE" >&2; exit 25; }
for pair in "$USER_ONE_UID:$USER_ONE_TOKEN" "$USER_TWO_UID:$USER_TWO_TOKEN" "$ADMIN_UID:$ADMIN_TOKEN"; do
    uid="${pair%%:*}"; token="${pair#*:}"
    [[ "$(redis_ttl "${REDIS_PREFIX}_userInfo${token}")" == -2 ]] || { echo "Session key remains for uid=$uid" >&2; exit 25; }
    for suffix_key in _postNum _isRepeated _silence; do
        [[ "$(redis_ttl "${REDIS_PREFIX}_${uid}${suffix_key}")" == -2 ]] || { echo "Redis key remains for uid=$uid suffix=$suffix_key" >&2; exit 25; }
    done
done
echo "post_cleanup_sql_residue=0,redis_residue=0"
echo "contents_add_direct_audit=PASS"
