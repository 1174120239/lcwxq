#!/usr/bin/env bash
set -euo pipefail

# Disposable spaceDelete compatibility/security test. The legacy endpoint only
# removes the selected Space row: child rows, like logs, and experience remain.
# Redis-only sessions exercise owner, non-owner, and staff paths on both APIs.

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-space-delete}
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
    # From this point authentication must come from the shared Redis session.
    sql "UPDATE starfree_users SET authCode=NULL WHERE uid=$uid"
    printf '%s' "$token"
}
notice_count() {
    local marker="$1"
    sql "SELECT COUNT(*) FROM starfree_inbox WHERE type='system' AND uid=$ADMIN_UID AND touid=$OWNER_UID AND text LIKE '%$marker%'"
}

epoch="$(date +%s)"; suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
OWNER_NAME="csd_o_$suffix"; OTHER_NAME="csd_x_$suffix"; ADMIN_NAME="csd_a_$suffix"
TEST_PASSWORD='correct horse battery staple'; TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
OWNER_UID=''; OTHER_UID=''; ADMIN_UID=''; OWNER_TOKEN=''; OTHER_TOKEN=''; ADMIN_TOKEN=''
REPLACEMENT_OWNER_ID=''; REPLACEMENT_CHILD_ID=''; REPLACEMENT_ADMIN_ID=''
LEGACY_OWNER_ID=''; LEGACY_CHILD_ID=''; PUBLIC_ID=''
REPLACEMENT_OWNER_TEXT="csd_replacement_owner_$suffix"
REPLACEMENT_ADMIN_TEXT="csd_replacement_admin_$suffix"
LEGACY_OWNER_TEXT="csd_legacy_owner_$suffix"
PUBLIC_TEXT="csd_public_$suffix"
HEADER_FILE="/tmp/starfree-space-delete-headers-$$"

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
        sql "DELETE FROM starfree_space WHERE uid IN ($ids) OR text LIKE 'csd\\_%'" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_users WHERE uid IN ($ids)" >/dev/null 2>&1 || status=1
    fi
    sql "DELETE FROM starfree_space WHERE text LIKE 'csd\\_%'" >/dev/null 2>&1 || status=1
    sql "DELETE FROM starfree_users WHERE LEFT(name,4)='csd_'" >/dev/null 2>&1 || status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || status=1
    [[ $status -eq 0 ]] || echo "WARNING: inspect spaceDelete cleanup for $suffix" >&2
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
($OWNER_UID,$epoch,$epoch,'$REPLACEMENT_OWNER_TEXT',NULL,0,1,0,1,0),
($OWNER_UID,$epoch,$epoch,'$REPLACEMENT_ADMIN_TEXT',NULL,0,0,0,1,0),
($OWNER_UID,$epoch,$epoch,'$LEGACY_OWNER_TEXT',NULL,0,1,0,1,0)"
REPLACEMENT_OWNER_ID="$(sql "SELECT id FROM starfree_space WHERE text='$REPLACEMENT_OWNER_TEXT' LIMIT 1")"
REPLACEMENT_ADMIN_ID="$(sql "SELECT id FROM starfree_space WHERE text='$REPLACEMENT_ADMIN_TEXT' LIMIT 1")"
LEGACY_OWNER_ID="$(sql "SELECT id FROM starfree_space WHERE text='$LEGACY_OWNER_TEXT' LIMIT 1")"
sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
($OTHER_UID,$epoch,$epoch,'csd_replacement_child_$suffix',NULL,3,0,$REPLACEMENT_OWNER_ID,1,0),
($OTHER_UID,$epoch,$epoch,'csd_legacy_child_$suffix',NULL,3,0,$LEGACY_OWNER_ID,1,0)"
REPLACEMENT_CHILD_ID="$(sql "SELECT id FROM starfree_space WHERE text='csd_replacement_child_$suffix' LIMIT 1")"
LEGACY_CHILD_ID="$(sql "SELECT id FROM starfree_space WHERE text='csd_legacy_child_$suffix' LIMIT 1")"
sql "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) VALUES
($OTHER_UID,$REPLACEMENT_OWNER_ID,'spaceLike',0,$epoch,0),
($OTHER_UID,$LEGACY_OWNER_ID,'spaceLike',0,$epoch,0)"
echo "disposable_space_ids=$REPLACEMENT_OWNER_ID,$REPLACEMENT_ADMIN_ID,$LEGACY_OWNER_ID,$REPLACEMENT_CHILD_ID,$LEGACY_CHILD_ID"

replacement_denied="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceDelete" \
    --data-urlencode "token=$OTHER_TOKEN" --data-urlencode "id=$REPLACEMENT_OWNER_ID")"
assert_code replacement_non_owner_rejected 0 "$replacement_denied"
legacy_denied="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceDelete" \
    --data-urlencode "token=$OTHER_TOKEN" --data-urlencode "id=$LEGACY_OWNER_ID")"
assert_code legacy_non_owner_rejected 0 "$legacy_denied"

replacement_owner_delete="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceDelete" \
    --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "id=$REPLACEMENT_OWNER_ID")"
assert_code replacement_owner_delete 1 "$replacement_owner_delete"
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$REPLACEMENT_OWNER_ID")" == 0 ]] || exit 14
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$REPLACEMENT_CHILD_ID AND toid=$REPLACEMENT_OWNER_ID")" == 1 ]] || { echo "Replacement unexpectedly cascaded child row" >&2; exit 14; }
[[ "$(sql "SELECT COUNT(*) FROM starfree_userlog WHERE cid=$REPLACEMENT_OWNER_ID AND type='spaceLike'")" == 1 ]] || { echo "Replacement unexpectedly cascaded like log" >&2; exit 14; }
[[ "$(notice_count "$REPLACEMENT_OWNER_TEXT")" == 0 ]] || { echo "Owner self-delete wrote a notice" >&2; exit 14; }

legacy_owner_delete="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceDelete" \
    --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "id=$LEGACY_OWNER_ID")"
assert_code legacy_owner_delete_bug 0 "$legacy_owner_delete"
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$LEGACY_OWNER_ID")" == 1 ]] || exit 15
echo "legacy_staff_only_aop_bug=CONFIRMED"

# The old controller's owner branch is unreachable through its purview=1 AOP.
# Use staff for the legacy row to compare its actual deletion side effects.
legacy_staff_delete="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceDelete" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$LEGACY_OWNER_ID")"
assert_code legacy_staff_delete 1 "$legacy_staff_delete"
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$LEGACY_OWNER_ID")" == 0 ]] || exit 15
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$LEGACY_CHILD_ID AND toid=$LEGACY_OWNER_ID")" == 1 ]] || { echo "Legacy child-row contract changed" >&2; exit 15; }
[[ "$(sql "SELECT COUNT(*) FROM starfree_userlog WHERE cid=$LEGACY_OWNER_ID AND type='spaceLike'")" == 1 ]] || { echo "Legacy like-log contract changed" >&2; exit 15; }
[[ "$(notice_count "$LEGACY_OWNER_TEXT")" == 1 ]] || { echo "Legacy admin-delete notice missing" >&2; exit 15; }
echo "legacy_non_cascade_contract=CONFIRMED"

replacement_admin_delete="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceDelete" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$REPLACEMENT_ADMIN_ID")"
assert_code replacement_staff_delete 1 "$replacement_admin_delete"
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$REPLACEMENT_ADMIN_ID")" == 0 ]] || exit 16
[[ "$(notice_count "$REPLACEMENT_ADMIN_TEXT")" == 1 ]] || { echo "Replacement admin-delete notice missing" >&2; exit 16; }

[[ "$(sql "SELECT experience FROM starfree_users WHERE uid=$OWNER_UID")" == 100000 ]] || { echo "spaceDelete unexpectedly changed experience" >&2; exit 18; }
echo "delete_experience_change=0"

if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
        ($OWNER_UID,$epoch,$epoch,'$PUBLIC_TEXT',NULL,0,0,0,1,0)"
    PUBLIC_ID="$(sql "SELECT id FROM starfree_space WHERE text='$PUBLIC_TEXT' LIMIT 1")"
    public_delete="$(curl -fsS -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeSpace/spaceDelete" \
        --data-urlencode "token=$OWNER_TOKEN" --data-urlencode "id=$PUBLIC_ID")"
    assert_code public_owner_delete_fix 1 "$public_delete"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || { echo "Public spaceDelete backend header mismatch" >&2; exit 19; }
    [[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$PUBLIC_ID")" == 0 ]] || exit 19
    [[ "$(notice_count "$PUBLIC_TEXT")" == 0 ]] || { echo "Public owner delete wrote a notice" >&2; exit 19; }
    echo "public_delete_backend=$EXPECTED_PUBLIC_BACKEND"
fi

echo "space_delete_direct_audit=PASS"
