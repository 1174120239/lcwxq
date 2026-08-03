#!/usr/bin/env bash
set -euo pipefail

# Disposable spaceReview security/compatibility test. It verifies the legacy
# runtime permission guard, demonstrates its pending-rejection bug on an
# isolated row, checks replacement notices, and cleans everything on EXIT.

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-space-review}
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
    sql "UPDATE starfree_users SET authCode=NULL WHERE uid=$uid"
    printf '%s' "$token"
}

epoch="$(date +%s)"; suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
OWNER_NAME="csr_o_$suffix"; OTHER_NAME="csr_x_$suffix"; ADMIN_NAME="csr_a_$suffix"
TEST_PASSWORD='correct horse battery staple'; TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
OWNER_UID=''; OTHER_UID=''; ADMIN_UID=''; OWNER_TOKEN=''; OTHER_TOKEN=''; ADMIN_TOKEN=''
APPROVE_ID=''; REJECT_ID=''; LEGACY_AUTH_ID=''; LEGACY_REJECT_ID=''; PUBLIC_ID=''
HEADER_FILE="/tmp/starfree-space-review-headers-$$"

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
        sql "DELETE FROM starfree_space WHERE uid IN ($ids) OR text LIKE 'csr\\_%'" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_users WHERE uid IN ($ids)" >/dev/null 2>&1 || status=1
    fi
    sql "DELETE FROM starfree_space WHERE text LIKE 'csr\\_%'" >/dev/null 2>&1 || status=1
    sql "DELETE FROM starfree_users WHERE LEFT(name,4)='csr_'" >/dev/null 2>&1 || status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || status=1
    [[ $status -eq 0 ]] || echo "WARNING: inspect spaceReview cleanup for $suffix" >&2
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

sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
($OWNER_UID,$epoch,$epoch,'csr_approve_$suffix',NULL,0,0,0,0,0),
($OWNER_UID,$epoch,$epoch,'csr_reject_$suffix',NULL,0,0,0,0,0),
($OWNER_UID,$epoch,$epoch,'csr_legacy_auth_$suffix',NULL,0,0,0,0,0),
($OWNER_UID,$epoch,$epoch,'csr_legacy_reject_$suffix',NULL,0,0,0,0,0)"
APPROVE_ID="$(sql "SELECT id FROM starfree_space WHERE text='csr_approve_$suffix' LIMIT 1")"
REJECT_ID="$(sql "SELECT id FROM starfree_space WHERE text='csr_reject_$suffix' LIMIT 1")"
LEGACY_AUTH_ID="$(sql "SELECT id FROM starfree_space WHERE text='csr_legacy_auth_$suffix' LIMIT 1")"
LEGACY_REJECT_ID="$(sql "SELECT id FROM starfree_space WHERE text='csr_legacy_reject_$suffix' LIMIT 1")"
echo "disposable_space_ids=$APPROVE_ID,$REJECT_ID,$LEGACY_AUTH_ID,$LEGACY_REJECT_ID"

replacement_denied="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceReview" \
    --data-urlencode "token=$OTHER_TOKEN" --data-urlencode "id=$APPROVE_ID" --data-urlencode type=1)"
assert_code replacement_non_staff_rejected 0 "$replacement_denied"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$APPROVE_ID")" == 0 ]] || exit 14

legacy_auth="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceReview" \
    --data-urlencode "token=$OTHER_TOKEN" --data-urlencode "id=$LEGACY_AUTH_ID" --data-urlencode type=1)"
assert_code legacy_non_staff_rejected 0 "$legacy_auth"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$LEGACY_AUTH_ID")" == 0 ]] || exit 15
echo "legacy_runtime_staff_guard=CONFIRMED"

legacy_reject="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceReview" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$LEGACY_REJECT_ID" --data-urlencode type=0)"
assert_code legacy_pending_reject 0 "$legacy_reject"
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$LEGACY_REJECT_ID")" == 1 ]] || exit 16
echo "legacy_pending_reject_bug=CONFIRMED"

approve="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceReview" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$APPROVE_ID" --data-urlencode type=1)"
assert_code replacement_staff_approve 1 "$approve"
[[ "$(sql "SELECT status FROM starfree_space WHERE id=$APPROVE_ID")" == 1 ]] || exit 17
APPROVE_NOTICE_COUNT="$(sql "SELECT COUNT(*) FROM starfree_inbox WHERE type='system' AND uid=$ADMIN_UID AND touid=$OWNER_UID")"
[[ "$APPROVE_NOTICE_COUNT" -ge 1 ]] || { echo "Approval notice missing" >&2; exit 17; }

duplicate="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceReview" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$APPROVE_ID" --data-urlencode type=1)"
assert_code replacement_duplicate_approve_rejected 0 "$duplicate"
[[ "$(sql "SELECT COUNT(*) FROM starfree_inbox WHERE type='system' AND uid=$ADMIN_UID AND touid=$OWNER_UID")" == "$APPROVE_NOTICE_COUNT" ]] || { echo "Duplicate approval wrote a notice" >&2; exit 18; }

reject="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceReview" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$REJECT_ID" --data-urlencode type=0)"
assert_code replacement_pending_reject 1 "$reject"
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$REJECT_ID")" == 0 ]] || { echo "Replacement did not delete rejected row" >&2; exit 19; }
[[ "$(sql "SELECT COUNT(*) FROM starfree_inbox WHERE type='system' AND uid=$ADMIN_UID AND touid=$OWNER_UID")" -eq $((APPROVE_NOTICE_COUNT + 1)) ]] || { echo "Rejection notice missing" >&2; exit 19; }

replacement_legacy_reject="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceReview" \
    --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$LEGACY_REJECT_ID" --data-urlencode type=0)"
assert_code replacement_fixes_pending_reject 1 "$replacement_legacy_reject"
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE id=$LEGACY_REJECT_ID")" == 0 ]] || exit 20

if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
        ($OWNER_UID,$epoch,$epoch,'csr_public_$suffix',NULL,0,0,0,0,0)"
    PUBLIC_ID="$(sql "SELECT id FROM starfree_space WHERE text='csr_public_$suffix' LIMIT 1")"
    public_response="$(curl -fsS -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeSpace/spaceReview" \
        --data-urlencode "token=$ADMIN_TOKEN" --data-urlencode "id=$PUBLIC_ID" --data-urlencode type=1)"
    assert_code public_staff_approve 1 "$public_response"
    grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || { echo "Public spaceReview backend header mismatch" >&2; exit 21; }
    [[ "$(sql "SELECT status FROM starfree_space WHERE id=$PUBLIC_ID")" == 1 ]] || exit 21
    echo "public_review_backend=$EXPECTED_PUBLIC_BACKEND"
fi

echo "space_review_direct_audit=PASS"
