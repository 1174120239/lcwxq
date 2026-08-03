#!/usr/bin/env bash
set -euo pipefail

# Disposable spaceLock compatibility/security test. Redis-only sessions are
# exercised against both runtimes. Every SQL row and login is removed on EXIT.

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-space-lock}
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
for cmd in awk curl mysql; do command -v "$cmd" >/dev/null 2>&1 || { echo "Missing $cmd" >&2; exit 2; }; done
DB_USERNAME=${DB_USERNAME:-$(read_property spring.datasource.username)}
DB_PASSWORD=${DB_PASSWORD:-$(read_property spring.datasource.password)}
[[ -n "$DB_USERNAME" && -n "$DB_PASSWORD" ]] || { echo "Database credentials are missing" >&2; exit 2; }
export MYSQL_PWD="$DB_PASSWORD"

sql() { mysql --protocol=TCP --host=127.0.0.1 --user="$DB_USERNAME" --batch --skip-column-names "$DB_NAME" --execute="$1"; }
code() { printf '%s' "$1" | "$PYTHON_BIN" -c 'import json,sys; print(json.load(sys.stdin).get("code", ""))'; }
assert_code() {
    local label="$1" expected="$2" response="$3" actual
    actual="$(code "$response")"
    [[ "$actual" == "$expected" ]] || { echo "$label code=$actual expected=$expected" >&2; echo "$response" >&2; exit 10; }
    echo "$label=code:$actual"
}
login_token() {
    local name="$1" uid="$2" params response token
    params="$(printf '{\"name\":\"%s\",\"password\":\"%s\"}' "$name" "$TEST_PASSWORD")"
    response="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeUsers/userLogin" --data-urlencode "params=$params")"
    [[ "$(code "$response")" == 1 ]] || { echo "Login failed for $name" >&2; exit 11; }
    token="$(sql "SELECT authCode FROM starfree_users WHERE uid=$uid")"
    [[ "$token" =~ ^${name}[0-9a-f]{32}$ ]] || { echo "Unexpected token for $name" >&2; exit 11; }
    # Remove the SQL fallback so all following authenticated requests prove
    # that the shared legacy Java-serialized Redis session is sufficient.
    sql "UPDATE starfree_users SET authCode=NULL WHERE uid=$uid"
    printf '%s' "$token"
}
notice_count() {
    local space_id="$1"
    sql "SELECT COUNT(*) FROM starfree_inbox WHERE type='system' AND uid=$ADMIN_UID AND touid=$OWNER_UID AND text LIKE '%ID:$space_id%'"
}

epoch="$(date +%s)"; suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
OWNER_NAME="csk_o_$suffix"; OTHER_NAME="csk_x_$suffix"; ADMIN_NAME="csk_a_$suffix"
TEST_PASSWORD='correct horse battery staple'; TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
OWNER_UID=''; OTHER_UID=''; ADMIN_UID=''; OWNER_TOKEN=''; OTHER_TOKEN=''; ADMIN_TOKEN=''
REPLACEMENT_ID=''; LEGACY_ID=''; PENDING_ID=''; PUBLIC_ID=''
HEADER_FILE="/tmp/starfree-space-lock-headers-$$"

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
        sql "DELETE FROM starfree_space WHERE uid IN ($ids) OR text LIKE 'csk\\_%'" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_users WHERE uid IN ($ids)" >/dev/null 2>&1 || status=1
    fi
    sql "DELETE FROM starfree_space WHERE text LIKE 'csk\\_%'" >/dev/null 2>&1 || status=1
    sql "DELETE FROM starfree_users WHERE LEFT(name,4)='csk_'" >/dev/null 2>&1 || status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || status=1
    [[ $status -eq 0 ]] || echo "WARNING: inspect spaceLock cleanup for $suffix" >&2
    return $status
}
trap cleanup EXIT

sql "INSERT INTO starfree_users (name,password,mail,created,activated,logged,\`group\`,authCode,bantime,vip,experience) VALUES
('$OWNER_NAME','$TEST_HASH','$OWNER_NAME@invalid.local',$epoch,$epoch,0,'contributor',NULL,0,0,100000),
('$OTHER_NAME','$TEST_HASH','$OTHER_NAME@invalid.local',$epoch,$epoch,0,'contributor',NULL,0,0,100000),
('$ADMIN_NAME','$TEST_HASH','$ADMIN_NAME@invalid.local',$epoch,$epoch,0,'administrator',NULL,0,0,100000)"
OWNER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$OWNER_NAME' LIMIT 1")"
OTHER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$OTHER_NAME' LIMIT 1")"
ADMIN_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$ADMIN_NAME' LIMIT 1")"
[[ "$OWNER_UID" =~ ^[1-9][0-9]*$ && "$OTHER_UID" =~ ^[1-9][0-9]*$ && "$ADMIN_UID" =~ ^[1-9][0-9]*$ ]] || exit 12
echo "disposable_uids=$OWNER_UID,$OTHER_UID,$ADMIN_UID"
OWNER_TOKEN="$(login_token "$OWNER_NAME" "$OWNER_UID")"
OTHER_TOKEN="$(login_token "$OTHER_NAME" "$OTHER_UID")"
ADMIN_TOKEN="$(login_token "$ADMIN_NAME" "$ADMIN_UID")"
[[ "$(sql "SELECT COUNT(*) FROM starfree_users WHERE authCode IN ('$OWNER_TOKEN','$OTHER_TOKEN','$ADMIN_TOKEN')")" == 0 ]] || exit 13
echo "mysql_token_rows=0"
echo "session_source=redis-only"

sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
($OWNER_UID,$epoch,$epoch,'csk_replacement_$suffix',NULL,0,0,0,1,0),
($OWNER_UID,$epoch,$epoch,'csk_legacy_$suffix',NULL,0,0,0,1,0),
($OWNER_UID,$epoch,$epoch,'csk_pending_$suffix',NULL,0,0,0,0,0)"
REPLACEMENT_ID="$(sql "SELECT id FROM starfree_space WHERE text='csk_replacement_$suffix' LIMIT 1")"
LEGACY_ID="$(sql "SELECT id FROM starfree_space WHERE text='csk_legacy_$suffix' LIMIT 1")"
PENDING_ID="$(sql "SELECT id FROM starfree_space WHERE text='csk_pending_$suffix' LIMIT 1")"
echo "disposable_space_ids=$REPLACEMENT_ID,$LEGACY_ID,$PENDING_ID"

replacement_denied="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceLock" \
    --data-urlencode "token=$OTHER_TOKEN" --data-urlencode "id=$REPLACEMENT_ID" --data-urlencode type=2)"
assert_code replacement_non_staff_rejected 0 "$replacement_denied"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$REPLACEMENT_ID")" == 1 ]] || exit 14

legacy_denied="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceLock" \
    --data-urlencode "token=$OTHER_TOKEN" --data-urlencode "id=$LEGACY_ID" --data-urlencode type=2)"
assert_code legacy_non_staff_rejected 0 "$legacy_denied"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$LEGACY_ID")" == 1 ]] || exit 15
echo "legacy_runtime_staff_guard=CONFIRMED"

replacement_pending="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceLock" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$PENDING_ID" --data-urlencode type=2)"
assert_code replacement_pending_rejected 0 "$replacement_pending"
legacy_pending="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceLock" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$PENDING_ID" --data-urlencode type=2)"
assert_code legacy_pending_rejected 0 "$legacy_pending"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$PENDING_ID")" == 0 ]] || exit 16

legacy_lock="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceLock" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$LEGACY_ID" --data-urlencode type=2)"
assert_code legacy_staff_lock 1 "$legacy_lock"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$LEGACY_ID")" == 2 ]] || exit 17
[[ "$(notice_count "$LEGACY_ID")" == 1 ]] || { echo "Legacy lock notice missing" >&2; exit 17; }
legacy_unlock="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceLock" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$LEGACY_ID" --data-urlencode type=1)"
assert_code legacy_staff_unlock 1 "$legacy_unlock"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$LEGACY_ID")" == 1 ]] || exit 18
[[ "$(notice_count "$LEGACY_ID")" == 2 ]] || { echo "Legacy unlock notice missing" >&2; exit 18; }
echo "legacy_lock_unlock_contract=CONFIRMED"

replacement_lock="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceLock" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$REPLACEMENT_ID" --data-urlencode type=2)"
assert_code replacement_staff_lock 1 "$replacement_lock"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$REPLACEMENT_ID")" == 2 ]] || exit 19
[[ "$(notice_count "$REPLACEMENT_ID")" == 1 ]] || { echo "Replacement lock notice missing" >&2; exit 19; }

duplicate="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceLock" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$REPLACEMENT_ID" --data-urlencode type=2)"
assert_code replacement_duplicate_lock_rejected 0 "$duplicate"
[[ "$(notice_count "$REPLACEMENT_ID")" == 1 ]] || { echo "Duplicate lock wrote a notice" >&2; exit 20; }

replacement_unlock="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceLock" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$REPLACEMENT_ID" --data-urlencode type=1)"
assert_code replacement_staff_unlock 1 "$replacement_unlock"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$REPLACEMENT_ID")" == 1 ]] || exit 21
[[ "$(notice_count "$REPLACEMENT_ID")" == 2 ]] || { echo "Replacement unlock notice missing" >&2; exit 21; }

if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
        ($OWNER_UID,$epoch,$epoch,'csk_public_$suffix',NULL,0,0,0,1,0)"
    PUBLIC_ID="$(sql "SELECT id FROM starfree_space WHERE text='csk_public_$suffix' LIMIT 1")"
    public_lock="$(curl -fsS -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeSpace/spaceLock" \
        --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$PUBLIC_ID" --data-urlencode type=2)"
    assert_code public_staff_lock 1 "$public_lock"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || { echo "Public spaceLock backend header mismatch" >&2; exit 22; }
    [[ "$(sql "SELECT status FROM starfree_space WHERE id=$PUBLIC_ID")" == 2 ]] || exit 22
    [[ "$(notice_count "$PUBLIC_ID")" == 1 ]] || { echo "Public lock notice missing" >&2; exit 22; }
    echo "public_lock_backend=$EXPECTED_PUBLIC_BACKEND"
fi

echo "space_lock_direct_audit=PASS"
