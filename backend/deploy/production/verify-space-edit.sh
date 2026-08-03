#!/usr/bin/env bash
set -euo pipefail

# Disposable editSpace compatibility/security test. Three Redis-only users
# exercise owner, non-owner, and staff paths without changing global config.
# SQL rows, sessions, and legacy abuse-control keys are removed on every exit.

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-space-edit}
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
for cmd in awk curl mysql redis-cli; do command -v "$cmd" >/dev/null 2>&1 || { echo "Missing $cmd" >&2; exit 2; }; done

DB_USERNAME=${DB_USERNAME:-$(read_property spring.datasource.username)}
DB_PASSWORD=${DB_PASSWORD:-$(read_property spring.datasource.password)}
REDIS_HOST=${REDIS_HOST:-$(read_property spring.redis.host)}
REDIS_PORT=${REDIS_PORT:-$(read_property spring.redis.port)}
REDIS_PASSWORD=${REDIS_PASSWORD-$(read_property spring.redis.password)}
REDIS_PREFIX=${REDIS_PREFIX:-$(read_property web.prefix)}
REDIS_HOST=${REDIS_HOST:-127.0.0.1}; REDIS_PORT=${REDIS_PORT:-6379}; REDIS_PREFIX=${REDIS_PREFIX:-starfree}
[[ -n "$DB_USERNAME" && -n "$DB_PASSWORD" ]] || { echo "Database credentials are missing" >&2; exit 2; }
export MYSQL_PWD="$DB_PASSWORD"
if [[ -n "$REDIS_PASSWORD" ]]; then export REDISCLI_AUTH="$REDIS_PASSWORD"; else unset REDISCLI_AUTH || true; fi

sql() { mysql --protocol=TCP --host=127.0.0.1 --user="$DB_USERNAME" --batch --skip-column-names "$DB_NAME" --execute="$1"; }
code() { printf '%s' "$1" | "$PYTHON_BIN" -c 'import json,sys; print(json.load(sys.stdin).get("code", ""))'; }
assert_code() {
    local label="$1" expected="$2" response="$3" actual
    actual="$(code "$response")"
    [[ "$actual" == "$expected" ]] || { echo "$label code=$actual expected=$expected" >&2; echo "$response" >&2; exit 10; }
    echo "$label=code:$actual"
}
java_serialized_key() {
    printf '%s' "$1" | "$PYTHON_BIN" -c '
import struct,sys
source=getattr(sys.stdin,"buffer",sys.stdin); target=getattr(sys.stdout,"buffer",sys.stdout)
value=source.read(); target.write(b"\xac\xed\x00\x05\x74"+struct.pack(">H",len(value))+value)
'
}
delete_redis_key() {
    java_serialized_key "$1" | redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw -x DEL >/dev/null
}
login_token() {
    local name="$1" uid="$2" params response token
    params="$(printf '{\"name\":\"%s\",\"password\":\"%s\"}' "$name" "$TEST_PASSWORD")"
    response="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeUsers/userLogin" --data-urlencode "params=$params")"
    [[ "$(code "$response")" == 1 ]] || { echo "Login failed for $name" >&2; echo "$response" >&2; exit 12; }
    token="$(sql "SELECT authCode FROM starfree_users WHERE uid=$uid")"
    [[ "$token" =~ ^${name}[0-9a-f]{32}$ ]] || { echo "Unexpected token for $name" >&2; exit 12; }
    sql "UPDATE starfree_users SET authCode=NULL WHERE uid=$uid"
    printf '%s' "$token"
}

epoch="$(date +%s)"; suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
OWNER_NAME="cse_o_$suffix"; OTHER_NAME="cse_x_$suffix"; ADMIN_NAME="cse_a_$suffix"
TEST_PASSWORD='correct horse battery staple'; TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
OWNER_UID=''; OTHER_UID=''; ADMIN_UID=''; OWNER_TOKEN=''; OTHER_TOKEN=''; ADMIN_TOKEN=''
MAIN_ID=''; LEGACY_ID=''; LOCKED_ID=''; REPLY_ID=''; PLUGIN_ID=''; PUBLIC_ID=''
ORIGINAL_TEXT="cse_original_$suffix"; OWNER_TEXT="cse_owner_edit_$suffix"
ADMIN_TEXT="cse_admin_edit_$suffix"; LEGACY_TEXT="cse_legacy_edit_$suffix"
PUBLIC_TEXT="cse_public_edit_$suffix"; HEADER_FILE="/tmp/starfree-space-edit-headers-$$"

cleanup() {
    local status=0 ids='' uid token
    set +e
    for pair in "$OWNER_UID:$OWNER_TOKEN" "$OTHER_UID:$OTHER_TOKEN" "$ADMIN_UID:$ADMIN_TOKEN"; do
        uid="${pair%%:*}"; token="${pair#*:}"
        if [[ "$uid" =~ ^[1-9][0-9]*$ && -n "$token" ]]; then
            sql "UPDATE starfree_users SET authCode='$token' WHERE uid=$uid" >/dev/null 2>&1
            curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" --data-urlencode "token=$token" >/dev/null 2>&1 || status=1
        fi
        [[ "$uid" =~ ^[1-9][0-9]*$ ]] && ids="${ids:+$ids,}$uid"
    done
    if [[ -n "$ids" ]]; then
        sql "DELETE FROM starfree_userlog WHERE uid IN ($ids) OR toid IN ($ids)" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_inbox WHERE uid IN ($ids) OR touid IN ($ids)" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_fan WHERE uid IN ($ids) OR touid IN ($ids)" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_space WHERE uid IN ($ids) OR text LIKE 'cse\\_%'" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_users WHERE uid IN ($ids)" >/dev/null 2>&1 || status=1
        for uid in ${ids//,/ }; do
            for key_suffix in _spaceNum _isAddSpace _isIntercept _silence; do
                delete_redis_key "${REDIS_PREFIX}_${uid}${key_suffix}" >/dev/null 2>&1 || status=1
            done
        done
    fi
    sql "DELETE FROM starfree_space WHERE text LIKE 'cse\\_%'" >/dev/null 2>&1 || status=1
    sql "DELETE FROM starfree_users WHERE LEFT(name,4)='cse_'" >/dev/null 2>&1 || status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || status=1
    [[ $status -eq 0 ]] || echo "WARNING: inspect editSpace cleanup for $suffix" >&2
    return $status
}
trap cleanup EXIT

BAN_ROBOTS="$(sql "SELECT banRobots FROM starfree_apiconfig ORDER BY id LIMIT 1")"
echo "config=banRobots:$BAN_ROBOTS"
sql "INSERT INTO starfree_users (name,password,mail,created,activated,logged,\`group\`,authCode,bantime,vip,experience) VALUES
('$OWNER_NAME','$TEST_HASH','$OWNER_NAME@invalid.local',$epoch,$epoch,0,'contributor',NULL,0,0,100000),
('$OTHER_NAME','$TEST_HASH','$OTHER_NAME@invalid.local',$epoch,$epoch,0,'contributor',NULL,0,0,100000),
('$ADMIN_NAME','$TEST_HASH','$ADMIN_NAME@invalid.local',$epoch,$epoch,0,'administrator',NULL,0,0,100000)"
OWNER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$OWNER_NAME' LIMIT 1")"
OTHER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$OTHER_NAME' LIMIT 1")"
ADMIN_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$ADMIN_NAME' LIMIT 1")"
[[ "$OWNER_UID" =~ ^[1-9][0-9]*$ && "$OTHER_UID" =~ ^[1-9][0-9]*$ && "$ADMIN_UID" =~ ^[1-9][0-9]*$ ]] || exit 13
echo "disposable_uids=$OWNER_UID,$OTHER_UID,$ADMIN_UID"
OWNER_TOKEN="$(login_token "$OWNER_NAME" "$OWNER_UID")"
OTHER_TOKEN="$(login_token "$OTHER_NAME" "$OTHER_UID")"
ADMIN_TOKEN="$(login_token "$ADMIN_NAME" "$ADMIN_UID")"
[[ "$(sql "SELECT COUNT(*) FROM starfree_users WHERE authCode IN ('$OWNER_TOKEN','$OTHER_TOKEN','$ADMIN_TOKEN')")" == 0 ]] || exit 14
echo "mysql_token_rows=0"

sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
($OWNER_UID,$((epoch-10)),$((epoch-10)),'$ORIGINAL_TEXT','old.png',0,0,0,1,0),
($OWNER_UID,$((epoch-9)),$((epoch-9)),'cse_legacy_original_$suffix','old-legacy.png',0,0,0,1,0),
($OWNER_UID,$((epoch-8)),$((epoch-8)),'cse_locked_$suffix',NULL,0,0,0,2,0),
($OWNER_UID,$((epoch-7)),$((epoch-7)),'cse_reply_$suffix',NULL,3,0,0,1,0),
($OWNER_UID,$((epoch-6)),$((epoch-6)),'cse_plugin_$suffix',NULL,6,0,0,1,0)"
MAIN_ID="$(sql "SELECT id FROM starfree_space WHERE text='$ORIGINAL_TEXT' LIMIT 1")"
LEGACY_ID="$(sql "SELECT id FROM starfree_space WHERE text='cse_legacy_original_$suffix' LIMIT 1")"
LOCKED_ID="$(sql "SELECT id FROM starfree_space WHERE text='cse_locked_$suffix' LIMIT 1")"
REPLY_ID="$(sql "SELECT id FROM starfree_space WHERE text='cse_reply_$suffix' LIMIT 1")"
PLUGIN_ID="$(sql "SELECT id FROM starfree_space WHERE text='cse_plugin_$suffix' LIMIT 1")"
sql "UPDATE starfree_space SET toid=$LOCKED_ID WHERE id=$REPLY_ID"
echo "disposable_space_ids=$MAIN_ID,$LEGACY_ID,$LOCKED_ID,$REPLY_ID,$PLUGIN_ID"

owner_edit="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/editSpace" \
    --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "id=$MAIN_ID" \
    --data-urlencode type=0 --data-urlencode toid=0 --data-urlencode onlyMe=1 \
    --data-urlencode "text=$OWNER_TEXT" --data-urlencode pic=new-owner.png)"
assert_code replacement_owner_edit 1 "$owner_edit"
OWNER_STATE="$(sql "SELECT CONCAT(uid,':',type,':',status,':',onlyMe,':',text,':',pic) FROM starfree_space WHERE id=$MAIN_ID")"
[[ "$OWNER_STATE" == "$OWNER_UID:0:1:1:$OWNER_TEXT:new-owner.png" ]] || { echo "Owner edit state mismatch: $OWNER_STATE" >&2; exit 15; }

denied="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/editSpace" \
    --data-urlencode "token=$OTHER_TOKEN" --data-urlencode "id=$MAIN_ID" \
    --data-urlencode type=0 --data-urlencode toid=0 --data-urlencode onlyMe=0 \
    --data-urlencode "text=cse_denied_$suffix")"
assert_code replacement_non_owner_rejected 0 "$denied"
[[ "$(sql "SELECT text FROM starfree_space WHERE id=$MAIN_ID")" == "$OWNER_TEXT" ]] || exit 16

admin_edit="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/editSpace" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$MAIN_ID" \
    --data-urlencode type=0 --data-urlencode toid=0 --data-urlencode onlyMe=0 \
    --data-urlencode "text=$ADMIN_TEXT" --data-urlencode pic=new-admin.png)"
assert_code replacement_admin_edit 1 "$admin_edit"
ADMIN_STATE="$(sql "SELECT CONCAT(uid,':',type,':',onlyMe,':',text,':',pic) FROM starfree_space WHERE id=$MAIN_ID")"
[[ "$ADMIN_STATE" == "$OWNER_UID:0:0:$ADMIN_TEXT:new-admin.png" ]] || { echo "Admin edit transferred owner or type: $ADMIN_STATE" >&2; exit 17; }
echo "replacement_owner_preserved=$OWNER_UID"

plugin="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/editSpace" \
    --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "id=$PLUGIN_ID" \
    --data-urlencode type=6 --data-urlencode toid=1 --data-urlencode onlyMe=0 \
    --data-urlencode "text=cse_plugin_changed_$suffix")"
assert_code replacement_plugin_rejected 0 "$plugin"
[[ "$(sql "SELECT text FROM starfree_space WHERE id=$PLUGIN_ID")" == "cse_plugin_$suffix" ]] || exit 18

mismatch="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/editSpace" \
    --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "id=$MAIN_ID" \
    --data-urlencode type=3 --data-urlencode "toid=$LOCKED_ID" --data-urlencode onlyMe=0 \
    --data-urlencode "text=cse_type_mismatch_$suffix")"
assert_code replacement_type_mismatch_rejected 0 "$mismatch"

[[ "$BAN_ROBOTS" == 1 ]] && sleep 6
legacy_edit="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/editSpace" \
    --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "id=$LEGACY_ID" \
    --data-urlencode type=0 --data-urlencode toid=0 --data-urlencode onlyMe=1 \
    --data-urlencode "text=$LEGACY_TEXT" --data-urlencode pic=new-legacy.png)"
assert_code legacy_owner_edit 1 "$legacy_edit"
LEGACY_STATE="$(sql "SELECT CONCAT(uid,':',type,':',onlyMe,':',text,':',pic) FROM starfree_space WHERE id=$LEGACY_ID")"
[[ "$LEGACY_STATE" == "$OWNER_UID:0:1:$LEGACY_TEXT:new-legacy.png" ]] || { echo "Legacy owner edit state mismatch: $LEGACY_STATE" >&2; exit 19; }

[[ "$BAN_ROBOTS" == 1 ]] && sleep 6
locked_reply="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/editSpace" \
    --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "id=$REPLY_ID" \
    --data-urlencode type=3 --data-urlencode "toid=$LOCKED_ID" --data-urlencode onlyMe=0 \
    --data-urlencode "text=cse_locked_reply_edit_$suffix")"
assert_code replacement_locked_reply_rejected 0 "$locked_reply"

if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    [[ "$BAN_ROBOTS" == 1 ]] && sleep 6
    sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
        ($OWNER_UID,$epoch,$epoch,'cse_public_original_$suffix',NULL,0,0,0,1,0)"
    PUBLIC_ID="$(sql "SELECT id FROM starfree_space WHERE text='cse_public_original_$suffix' LIMIT 1")"
    public_response="$(curl -fsS -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeSpace/editSpace" \
        --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "id=$PUBLIC_ID" \
        --data-urlencode type=0 --data-urlencode toid=0 --data-urlencode onlyMe=1 \
        --data-urlencode "text=$PUBLIC_TEXT" --data-urlencode pic=public.png)"
    assert_code public_owner_edit 1 "$public_response"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || { echo "Public editSpace backend header mismatch" >&2; exit 20; }
    [[ "$(sql "SELECT CONCAT(uid,':',onlyMe,':',text) FROM starfree_space WHERE id=$PUBLIC_ID")" == "$OWNER_UID:1:$PUBLIC_TEXT" ]] || exit 20
    echo "public_edit_backend=$EXPECTED_PUBLIC_BACKEND"
fi

echo "space_edit_direct_audit=PASS"
