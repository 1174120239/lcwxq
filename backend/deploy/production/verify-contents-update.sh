#!/usr/bin/env bash
set -euo pipefail

# Disposable contentsUpdate compatibility audit. It compares ordinary post
# updates across both runtimes, verifies replacement-only correctness fixes,
# and proves that closed content features are still delegated to port 8081.
PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-content-update}
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
    printf '%s' "$response" | "$PYTHON_BIN" -c '
import json,sys
value=json.load(sys.stdin)
if value.get("code") != 1 or value.get("data") != 1 or value.get("msg") != u"\u4fee\u6539\u6210\u529f":
    raise SystemExit(1)
' || { echo "$label did not return the exact legacy update envelope" >&2; echo "$response" >&2; exit 10; }
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

# Both services use JDK serialization for Redis keys and string values.
java_serialized_key() {
    printf '%s' "$1" | "$PYTHON_BIN" -c '
import struct,sys
source=getattr(sys.stdin,"buffer",sys.stdin); target=getattr(sys.stdout,"buffer",sys.stdout)
raw=source.read()
if len(raw)>65535: raise SystemExit("Redis key is too long")
target.write(b"\xac\xed\x00\x05\x74"+struct.pack(">H",len(raw))+raw)
'
}
redis_ttl() {
    java_serialized_key "$1" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x TTL | tr -d '\r\n'
}
delete_redis_key() {
    java_serialized_key "$1" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x DEL >/dev/null
}
matching_java_keys() {
    local prefix="$1" marker="${2:-}"
    redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw KEYS '*' | "$PYTHON_BIN" -c '
import struct,sys
prefix=sys.argv[1]; marker=sys.argv[2]
source=getattr(sys.stdin,"buffer",sys.stdin)
for raw in source.read().splitlines():
    if len(raw)<7 or raw[:5]!=b"\xac\xed\x00\x05\x74": continue
    size=struct.unpack(">H",raw[5:7])[0]
    if len(raw)<7+size: continue
    try: key=raw[7:7+size].decode("utf-8")
    except UnicodeDecodeError: continue
    if key.startswith(prefix) and (not marker or marker in key): print(key)
' "$prefix" "$marker"
}
count_matching_java_keys() {
    local count
    count="$(matching_java_keys "$1" "${2:-}" | awk 'END { print NR + 0 }')"
    printf '%s' "$count"
}
delete_matching_java_keys() {
    local key
    while IFS= read -r key; do
        [[ -n "$key" ]] && delete_redis_key "$key"
    done < <(matching_java_keys "$1" "${2:-}")
}

epoch="$(date +%s)"
suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
CONTRIBUTOR_NAME="ccu_u_$suffix"
ADMIN_NAME="ccu_a_$suffix"
TEST_PASSWORD='correct horse battery staple'
TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
TITLE_PREFIX="ccu_${suffix}_"
OLD_CATEGORY_SLUG="ccu-old-category-$suffix"
OLD_TAG_SLUG="ccu-old-tag-$suffix"
NEW_CATEGORY_SLUG="ccu-new-category-$suffix"
NEW_TAG_SLUG="ccu-new-tag-$suffix"
CONTRIBUTOR_UID=''; ADMIN_UID=''; CONTRIBUTOR_TOKEN=''; ADMIN_TOKEN=''
OLD_CATEGORY_MID=''; OLD_TAG_MID=''; NEW_CATEGORY_MID=''; NEW_TAG_MID=''
LEGACY_POST_CID=''; REPLACEMENT_POST_CID=''; ADMIN_POST_CID=''; VIDEO_CID=''; CUSTOM_CID=''
HEADER_FILE="/tmp/starfree-contents-update-headers-$$"

cleanup() {
    local status=0 uid token cid
    set +e
    for pair in "$CONTRIBUTOR_UID:$CONTRIBUTOR_TOKEN" "$ADMIN_UID:$ADMIN_TOKEN"; do
        uid="${pair%%:*}"; token="${pair#*:}"
        if [[ "$uid" =~ ^[1-9][0-9]*$ && -n "$token" ]]; then
            # signOut validates against authCode, so temporarily restore the token
            # after the audit deliberately converted the account to Redis-only.
            sql "UPDATE starfree_users SET authCode='$token' WHERE uid=$uid" >/dev/null 2>&1
            curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" --data-urlencode "token=$token" >/dev/null 2>&1 || status=1
        fi
    done
    for cid in "$LEGACY_POST_CID" "$REPLACEMENT_POST_CID" "$ADMIN_POST_CID" "$VIDEO_CID" "$CUSTOM_CID"; do
        if [[ "$cid" =~ ^[1-9][0-9]*$ ]]; then
            sql "DELETE FROM starfree_comments WHERE cid=$cid; DELETE FROM starfree_fields WHERE cid=$cid; DELETE FROM starfree_relationships WHERE cid=$cid; DELETE FROM starfree_contents WHERE cid=$cid" >/dev/null 2>&1 || status=1
            delete_redis_key "${REDIS_PREFIX}_contentsInfo_${cid}_0" >/dev/null 2>&1 || status=1
            delete_redis_key "${REDIS_PREFIX}_contentsInfo_${cid}_1" >/dev/null 2>&1 || status=1
        fi
    done
    delete_matching_java_keys "${REDIS_PREFIX}_contentsList_1" "$TITLE_PREFIX" >/dev/null 2>&1 || status=1
    sql "DELETE FROM starfree_relationships WHERE cid IN (SELECT cid FROM starfree_contents WHERE LEFT(title,4)='ccu_'); DELETE FROM starfree_contents WHERE LEFT(title,4)='ccu_'" >/dev/null 2>&1 || status=1
    sql "DELETE FROM starfree_users WHERE name IN ('$CONTRIBUTOR_NAME','$ADMIN_NAME'); DELETE FROM starfree_metas WHERE slug IN ('$OLD_CATEGORY_SLUG','$OLD_TAG_SLUG','$NEW_CATEGORY_SLUG','$NEW_TAG_SLUG')" >/dev/null 2>&1 || status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || status=1
    [[ $status -eq 0 ]] || echo "WARNING: inspect contentsUpdate cleanup for $suffix" >&2
    return $status
}
trap cleanup EXIT

PREEXISTING_SQL="$(sql "SELECT (SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='ccu_') + (SELECT COUNT(*) FROM starfree_contents WHERE LEFT(title,4)='ccu_') + (SELECT COUNT(*) FROM starfree_metas WHERE LEFT(slug,4)='ccu-')")"
[[ "$PREEXISTING_SQL" == 0 ]] || { echo "Pre-existing ccu_ SQL residue: $PREEXISTING_SQL" >&2; exit 12; }
echo "preexisting_sql_residue=0"

CONTENT_AUDIT="$(sql "SELECT contentAuditlevel FROM starfree_apiconfig ORDER BY id LIMIT 1")"
[[ "$CONTENT_AUDIT" == 2 ]] || { echo "contentAuditlevel must remain 2 for the pending-status audit" >&2; exit 12; }

sql "INSERT INTO starfree_users (name,password,mail,created,activated,logged,\`group\`,authCode,bantime,vip,experience) VALUES
    ('$CONTRIBUTOR_NAME','$TEST_HASH','$CONTRIBUTOR_NAME@invalid.local',$epoch,$epoch,0,'contributor',NULL,0,0,100000),
    ('$ADMIN_NAME','$TEST_HASH','$ADMIN_NAME@invalid.local',$epoch,$epoch,0,'administrator',NULL,0,0,100000)"
CONTRIBUTOR_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$CONTRIBUTOR_NAME' LIMIT 1")"
ADMIN_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$ADMIN_NAME' LIMIT 1")"
for uid in "$CONTRIBUTOR_UID" "$ADMIN_UID"; do
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
login_user "$CONTRIBUTOR_NAME" "$CONTRIBUTOR_UID"; CONTRIBUTOR_TOKEN="$LOGIN_TOKEN"
login_user "$ADMIN_NAME" "$ADMIN_UID"; ADMIN_TOKEN="$LOGIN_TOKEN"
echo "redis_only_sessions=2"

sql "INSERT INTO starfree_metas (name,slug,type,description,count,\`order\`,parent,imgurl,isrecommend) VALUES
    ('CCU old category','$OLD_CATEGORY_SLUG','category','update audit',2,0,0,NULL,0),
    ('CCU old tag','$OLD_TAG_SLUG','tag','update audit',2,0,0,NULL,0),
    ('CCU new category','$NEW_CATEGORY_SLUG','category','update audit',0,0,0,NULL,0),
    ('CCU new tag','$NEW_TAG_SLUG','tag','update audit',0,0,0,NULL,0)"
OLD_CATEGORY_MID="$(sql "SELECT mid FROM starfree_metas WHERE slug='$OLD_CATEGORY_SLUG' LIMIT 1")"
OLD_TAG_MID="$(sql "SELECT mid FROM starfree_metas WHERE slug='$OLD_TAG_SLUG' LIMIT 1")"
NEW_CATEGORY_MID="$(sql "SELECT mid FROM starfree_metas WHERE slug='$NEW_CATEGORY_SLUG' LIMIT 1")"
NEW_TAG_MID="$(sql "SELECT mid FROM starfree_metas WHERE slug='$NEW_TAG_SLUG' LIMIT 1")"

LEGACY_POST_TITLE="${TITLE_PREFIX}legacy_post"
REPLACEMENT_POST_TITLE="${TITLE_PREFIX}replacement_post"
ADMIN_POST_TITLE="${TITLE_PREFIX}admin_post"
VIDEO_TITLE="${TITLE_PREFIX}video"
CUSTOM_TITLE="${TITLE_PREFIX}custom"
sql "INSERT INTO starfree_contents (title,slug,created,modified,text,\`order\`,authorId,template,type,status,password,commentsNum,allowComment,allowPing,allowFeed,parent,views,likes,isrecommend,istop,isswiper,replyTime) VALUES
    ('$LEGACY_POST_TITLE','${TITLE_PREFIX}legacy',1,1,'<!--markdown-->initial legacy',0,$CONTRIBUTOR_UID,NULL,'post','publish',NULL,0,'1','1','1',0,0,0,0,0,0,1),
    ('$REPLACEMENT_POST_TITLE','${TITLE_PREFIX}replacement',1,1,'<!--markdown-->initial replacement',0,$CONTRIBUTOR_UID,NULL,'post','publish',NULL,0,'1','1','1',0,0,0,0,0,0,1),
    ('$ADMIN_POST_TITLE','${TITLE_PREFIX}admin',1,1,'<!--markdown-->initial admin',0,$ADMIN_UID,NULL,'post','waiting',NULL,0,'1','1','1',0,0,0,0,0,0,1),
    ('$VIDEO_TITLE','${TITLE_PREFIX}video',1,1,'<!--markdown-->initial video',0,$ADMIN_UID,NULL,'video','publish',NULL,0,'1','1','1',0,0,0,0,0,0,1),
    ('$CUSTOM_TITLE','${TITLE_PREFIX}custom',1,1,'custom body',0,$ADMIN_UID,NULL,'page','publish',NULL,0,'1','1','1',0,0,0,0,0,0,1)"
LEGACY_POST_CID="$(sql "SELECT cid FROM starfree_contents WHERE title='$LEGACY_POST_TITLE' LIMIT 1")"
REPLACEMENT_POST_CID="$(sql "SELECT cid FROM starfree_contents WHERE title='$REPLACEMENT_POST_TITLE' LIMIT 1")"
ADMIN_POST_CID="$(sql "SELECT cid FROM starfree_contents WHERE title='$ADMIN_POST_TITLE' LIMIT 1")"
VIDEO_CID="$(sql "SELECT cid FROM starfree_contents WHERE title='$VIDEO_TITLE' LIMIT 1")"
CUSTOM_CID="$(sql "SELECT cid FROM starfree_contents WHERE title='$CUSTOM_TITLE' LIMIT 1")"
sql "INSERT INTO starfree_relationships (cid,mid) VALUES
    ($LEGACY_POST_CID,$OLD_CATEGORY_MID),($LEGACY_POST_CID,$OLD_TAG_MID),
    ($REPLACEMENT_POST_CID,$OLD_CATEGORY_MID),($REPLACEMENT_POST_CID,$OLD_TAG_MID)"

update_content() {
    local base="$1" token="$2" cid="$3" title="$4" wire_text="$5" category="$6" tag="$7" type="${8:-}"
    local params
    if [[ -n "$type" ]]; then
        params="$(printf '{\"cid\":%s,\"title\":\"%s\",\"category\":\"%s\",\"tag\":\"%s\",\"sid\":-1,\"type\":\"%s\"}' "$cid" "$title" "$category" "$tag" "$type")"
    else
        params="$(printf '{\"cid\":%s,\"title\":\"%s\",\"category\":\"%s\",\"tag\":\"%s\",\"sid\":-1}' "$cid" "$title" "$category" "$tag")"
    fi
    curl -fsS -X POST "$base/SFreeContents/contentsUpdate" \
        --data-urlencode "token=$token" --data-urlencode "params=$params" \
        --data-urlencode "text=$wire_text"
}

WIRE_TEXT="ccu_line_one_$suffix||rn||ccu_line_two_$suffix"
REPLACEMENT_EXPECTED_TEXT="<!--markdown-->ccu_line_one_$suffix
ccu_line_two_$suffix"
LEGACY_WIRE_HASH="$(printf '%s' "$WIRE_TEXT" | sha256sum | awk '{print $1}')"
REPLACEMENT_EXPECTED_HASH="$(printf '%s' "$REPLACEMENT_EXPECTED_TEXT" | sha256sum | awk '{print $1}')"
LEGACY_UPDATED_TITLE="${TITLE_PREFIX}legacy_updated"
REPLACEMENT_UPDATED_TITLE="${TITLE_PREFIX}replacement_updated"

legacy_response="$(update_content "$LEGACY_URL" "$CONTRIBUTOR_TOKEN" "$LEGACY_POST_CID" \
    "$LEGACY_UPDATED_TITLE" "$WIRE_TEXT" "$NEW_CATEGORY_MID," "$NEW_TAG_MID,")"
assert_success legacy_post_update "$legacy_response"

# Populate real legacy caches after the legacy comparison update. The next
# replacement update must evict both detail modes and the unique page-one list.
for mode in 0 1; do
    curl -fsS -G "$LEGACY_URL/SFreeContents/contentsInfo" \
        --data-urlencode "key=$REPLACEMENT_POST_CID" --data-urlencode "isMd=$mode" \
        --data-urlencode "token=$CONTRIBUTOR_TOKEN" >/dev/null
    ttl="$(redis_ttl "${REDIS_PREFIX}_contentsInfo_${REPLACEMENT_POST_CID}_${mode}")"
    [[ "$ttl" =~ ^[1-9][0-9]*$ ]] || { echo "Legacy detail cache mode=$mode was not created" >&2; exit 15; }
done
curl -fsS -G "$LEGACY_URL/SFreeContents/contentsList" \
    --data-urlencode 'searchParams={}' --data-urlencode limit=15 --data-urlencode page=1 \
    --data-urlencode order=created --data-urlencode "searchKey=$TITLE_PREFIX" >/dev/null
LIST_CACHE_BEFORE="$(count_matching_java_keys "${REDIS_PREFIX}_contentsList_1" "$TITLE_PREFIX")"
[[ "$LIST_CACHE_BEFORE" -ge 1 ]] || { echo "Unique legacy page-one cache was not created" >&2; exit 15; }

replacement_response="$(update_content "$REPLACEMENT_URL" "$CONTRIBUTOR_TOKEN" "$REPLACEMENT_POST_CID" \
    "$REPLACEMENT_UPDATED_TITLE" "$WIRE_TEXT" "$NEW_CATEGORY_MID," "$NEW_TAG_MID,")"
assert_success replacement_post_update "$replacement_response"

content_shape() {
    sql "SELECT CONCAT_WS('|',type,status,SHA2(text,256),allowComment,allowPing,allowFeed) FROM starfree_contents WHERE cid=$1"
}
# The closed backend leaves the frontend placeholder untouched. This is kept
# as an observed baseline; the replacement is required to restore a real LF.
LEGACY_EXPECTED_SHAPE="post|waiting|$LEGACY_WIRE_HASH|1|1|1"
REPLACEMENT_EXPECTED_SHAPE="post|waiting|$REPLACEMENT_EXPECTED_HASH|1|1|1"
LEGACY_SHAPE="$(content_shape "$LEGACY_POST_CID")"
REPLACEMENT_SHAPE="$(content_shape "$REPLACEMENT_POST_CID")"
echo "legacy_post_shape=$LEGACY_SHAPE"
echo "replacement_post_shape=$REPLACEMENT_SHAPE"
[[ "$LEGACY_SHAPE" == "$LEGACY_EXPECTED_SHAPE" ]] || { echo "Legacy post update baseline mismatch" >&2; exit 16; }
[[ "$REPLACEMENT_SHAPE" == "$REPLACEMENT_EXPECTED_SHAPE" ]] || { echo "Replacement Markdown-preserving update mismatch" >&2; exit 16; }
[[ "$(sql "SELECT GROUP_CONCAT(mid ORDER BY mid) FROM starfree_relationships WHERE cid=$LEGACY_POST_CID")" == "$(printf '%s\n%s' "$NEW_CATEGORY_MID" "$NEW_TAG_MID" | sort -n | paste -sd, -)" ]] || exit 16
[[ "$(sql "SELECT GROUP_CONCAT(mid ORDER BY mid) FROM starfree_relationships WHERE cid=$REPLACEMENT_POST_CID")" == "$(printf '%s\n%s' "$NEW_CATEGORY_MID" "$NEW_TAG_MID" | sort -n | paste -sd, -)" ]] || exit 16
META_COUNTS="$(sql "SELECT GROUP_CONCAT(count ORDER BY mid) FROM starfree_metas WHERE mid IN ($OLD_CATEGORY_MID,$OLD_TAG_MID,$NEW_CATEGORY_MID,$NEW_TAG_MID)")"
[[ "$META_COUNTS" == '0,0,2,2' ]] || { echo "Relationship meta counts are stale: $META_COUNTS" >&2; exit 16; }
for mode in 0 1; do
    [[ "$(redis_ttl "${REDIS_PREFIX}_contentsInfo_${REPLACEMENT_POST_CID}_${mode}")" == -2 ]] || { echo "Detail cache mode=$mode survived update" >&2; exit 17; }
done
[[ "$(count_matching_java_keys "${REDIS_PREFIX}_contentsList_1" "$TITLE_PREFIX")" == 0 ]] || { echo "Page-one list cache survived update" >&2; exit 17; }
echo "post_parity_relationships_and_cache=PASS"

# Numeric-but-missing meta IDs are dangerous with MyISAM because no foreign
# key rejects them. The candidate must reject before changing the article row.
MISSING_MID=2147483647
rejected_params="$(printf '{\"cid\":%s,\"title\":\"%s\",\"category\":\"%s,\",\"tag\":\"%s,\",\"sid\":-1}' \
    "$REPLACEMENT_POST_CID" "${TITLE_PREFIX}must_not_persist" "$MISSING_MID" "$NEW_TAG_MID")"
rejected="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeContents/contentsUpdate" \
    --data-urlencode "token=$CONTRIBUTOR_TOKEN" --data-urlencode "params=$rejected_params" \
    --data-urlencode text=invalid_relationship)"
assert_failure missing_relationship "$rejected"
[[ "$(sql "SELECT title FROM starfree_contents WHERE cid=$REPLACEMENT_POST_CID")" == "$REPLACEMENT_UPDATED_TITLE" ]] || { echo "Rejected update changed article" >&2; exit 18; }
[[ "$(sql "SELECT COUNT(*) FROM starfree_relationships WHERE cid=$REPLACEMENT_POST_CID AND mid=$MISSING_MID")" == 0 ]] || { echo "Rejected update created orphan relationship" >&2; exit 18; }

admin_response="$(update_content "$REPLACEMENT_URL" "$ADMIN_TOKEN" "$ADMIN_POST_CID" \
    "${TITLE_PREFIX}admin_updated" 'admin_line_one||rn||admin_line_two' '' '')"
assert_success replacement_admin_update "$admin_response"
[[ "$(sql "SELECT status FROM starfree_contents WHERE cid=$ADMIN_POST_CID")" == publish ]] || { echo "Administrator update was not published" >&2; exit 19; }

video_response="$(update_content "$REPLACEMENT_URL" "$ADMIN_TOKEN" "$VIDEO_CID" \
    "${TITLE_PREFIX}video_updated" 'video_line_one||rn||video_line_two' '' '' video)"
assert_success replacement_video_update "$video_response"
VIDEO_SHAPE="$(sql "SELECT CONCAT(type,'|',LEFT(text,15),'|',IF(LOCATE(CHAR(10),text)>0,1,0),'|',status) FROM starfree_contents WHERE cid=$VIDEO_CID")"
[[ "$VIDEO_SHAPE" == 'video|<!--markdown-->|1|publish' ]] || { echo "Video type, Markdown, newline, or status mismatch: $VIDEO_SHAPE" >&2; exit 19; }
echo "pending_admin_and_video_behavior=PASS"

assert_delegated() {
    local label="$1" params="$2" flag_name="${3:-}" flag_value="${4:-}" response
    local args=(-fsS -D "$HEADER_FILE" -X POST "$REPLACEMENT_URL/SFreeContents/contentsUpdate"
        --data-urlencode token=ccu_invalid_token --data-urlencode "params=$params"
        --data-urlencode text=ccu_delegate_probe)
    [[ -n "$flag_name" ]] && args+=(--data-urlencode "$flag_name=$flag_value")
    response="$(curl "${args[@]}")"
    assert_failure "$label" "$response"
    grep -qi '^X-Starfree-Delegate: legacy-contents-update' "$HEADER_FILE" || {
        echo "$label was not delegated to the legacy backend" >&2; exit 20;
    }
    echo "$label=legacy-delegate"
}
BASE_DELEGATE_PARAMS="$(printf '{\"cid\":%s,\"title\":\"delegate\",\"sid\":-1}' "$REPLACEMENT_POST_CID")"
assert_delegated paid "$BASE_DELEGATE_PARAMS" isPaid 1
assert_delegated draft "$BASE_DELEGATE_PARAMS" isDraft 1
assert_delegated shop "$(printf '{\"cid\":%s,\"title\":\"shop\",\"sid\":1}' "$REPLACEMENT_POST_CID")"
assert_delegated nonordinary "$(printf '{\"cid\":%s,\"title\":\"page\",\"sid\":-1}' "$CUSTOM_CID")"
assert_delegated unknown_type "$(printf '{\"cid\":%s,\"title\":\"plugin\",\"sid\":-1,\"type\":\"plugin\"}' "$REPLACEMENT_POST_CID")"

if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    public_params="$(printf '{\"cid\":%s,\"title\":\"%s\",\"category\":\"\",\"tag\":\"\",\"sid\":-1}' \
        "$ADMIN_POST_CID" "${TITLE_PREFIX}public_updated")"
    public_response="$(curl -fsS --resolve api.lcxqy.cn:443:127.0.0.1 -D "$HEADER_FILE" -X POST \
        "$PUBLIC_URL/SFreeContents/contentsUpdate" --data-urlencode "token=$ADMIN_TOKEN" \
        --data-urlencode "params=$public_params" --data-urlencode 'text=public_line_one||rn||public_line_two')"
    assert_success public_update "$public_response"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || { echo "Public update backend header mismatch" >&2; exit 21; }
    if grep -qi '^X-Starfree-Delegate:' "$HEADER_FILE"; then echo "Ordinary public update was unexpectedly delegated" >&2; exit 21; fi
    [[ "$(sql "SELECT CONCAT(title,'|',status) FROM starfree_contents WHERE cid=$ADMIN_POST_CID")" == "${TITLE_PREFIX}public_updated|publish" ]] || exit 21

    public_delegate="$(curl -fsS --resolve api.lcxqy.cn:443:127.0.0.1 -D "$HEADER_FILE" -X POST \
        "$PUBLIC_URL/SFreeContents/contentsUpdate" --data-urlencode token=ccu_invalid_token \
        --data-urlencode "params=$BASE_DELEGATE_PARAMS" --data-urlencode text=delegate \
        --data-urlencode isDraft=1)"
    assert_failure public_draft_delegate "$public_delegate"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || exit 21
    grep -qi '^X-Starfree-Delegate: legacy-contents-update' "$HEADER_FILE" || exit 21
    echo "public_contents_update_backend=$EXPECTED_PUBLIC_BACKEND"
fi

# Report PASS only after cleanup and an explicit residue audit.
cleanup
trap - EXIT
POST_SQL_RESIDUE="$(sql "SELECT (SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='ccu_') + (SELECT COUNT(*) FROM starfree_contents WHERE LEFT(title,4)='ccu_') + (SELECT COUNT(*) FROM starfree_metas WHERE LEFT(slug,4)='ccu-')")"
[[ "$POST_SQL_RESIDUE" == 0 ]] || { echo "Post-cleanup ccu_ SQL residue: $POST_SQL_RESIDUE" >&2; exit 22; }
for token in "$CONTRIBUTOR_TOKEN" "$ADMIN_TOKEN"; do
    [[ "$(redis_ttl "${REDIS_PREFIX}_userInfo${token}")" == -2 ]] || { echo "Session key remains after cleanup" >&2; exit 22; }
done
[[ "$(count_matching_java_keys "${REDIS_PREFIX}_contentsList_1" "$TITLE_PREFIX")" == 0 ]] || { echo "Audit list cache remains after cleanup" >&2; exit 22; }
echo "post_cleanup_sql_residue=0,redis_residue=0"
echo "contents_update_direct_audit=PASS"
