#!/usr/bin/env bash
set -euo pipefail

# Disposable compatibility test for the Space-like write path. It deliberately
# exercises both backends against the same MySQL/Redis state:
#   replacement first -> legacy must reject the duplicate
#   legacy first      -> replacement must reject the duplicate
# When VERIFY_PUBLIC=1, a third user also likes through public Nginx and the
# response must carry EXPECTED_PUBLIC_BACKEND. All rows and sessions are cleaned
# even when an assertion fails.

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-space-like}
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

json_code() {
    "$PYTHON_BIN" -c 'import json,sys; print(json.load(sys.stdin).get("code", ""))'
}

assert_code() {
    local label="$1" expected="$2" response="$3" actual
    actual="$(printf '%s' "$response" | json_code)"
    if [[ "$actual" != "$expected" ]]; then
        echo "$label returned code=$actual, expected $expected" >&2
        echo "$response" >&2
        exit 10
    fi
    echo "$label=code:$actual"
}

for required in awk curl mysql; do
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
    echo "Python is required to parse JSON responses safely" >&2
    exit 2
fi

[[ -r "$PROPERTIES_FILE" ]] || {
    echo "Legacy configuration not readable: $PROPERTIES_FILE" >&2
    exit 2
}
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

epoch="$(date +%s)"
suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
# Production's legacy name column is short and non-strict MySQL may silently
# truncate oversized values. These role-prefixed names are exactly 15 bytes.
OWNER_NAME="csl_o_${suffix}"
NEW_FIRST_NAME="csl_n_${suffix}"
OLD_FIRST_NAME="csl_l_${suffix}"
PUBLIC_NAME="csl_p_${suffix}"
TEST_PASSWORD='correct horse battery staple'
TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
OWNER_UID=''
NEW_FIRST_UID=''
OLD_FIRST_UID=''
PUBLIC_UID=''
SPACE_ID=''
NEW_FIRST_TOKEN=''
OLD_FIRST_TOKEN=''
PUBLIC_TOKEN=''
HEADER_FILE="/tmp/starfree-space-like-headers-$$"

sign_out() {
    local uid="$1" token="$2"
    [[ -n "$uid" && -n "$token" ]] || return 0
    # Redis keys use Java serialization. Restore authCode only long enough for
    # the replacement signOut path to remove both the Redis hash and account key.
    sql "UPDATE starfree_users SET authCode = '$token' WHERE uid = $uid" >/dev/null 2>&1
    curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" \
        --data-urlencode "token=$token" >/dev/null 2>&1
}

cleanup() {
    local cleanup_status=0 ids=''
    set +e
    sign_out "$NEW_FIRST_UID" "$NEW_FIRST_TOKEN" || cleanup_status=1
    sign_out "$OLD_FIRST_UID" "$OLD_FIRST_TOKEN" || cleanup_status=1
    sign_out "$PUBLIC_UID" "$PUBLIC_TOKEN" || cleanup_status=1

    for uid in "$OWNER_UID" "$NEW_FIRST_UID" "$OLD_FIRST_UID" "$PUBLIC_UID"; do
        if [[ "$uid" =~ ^[1-9][0-9]*$ ]]; then
            ids="${ids:+$ids,}$uid"
        fi
    done
    if [[ -n "$ids" ]]; then
        sql "DELETE FROM starfree_userlog WHERE uid IN ($ids) OR toid IN ($ids)" \
            >/dev/null 2>&1 || cleanup_status=1
        sql "DELETE FROM starfree_space WHERE uid IN ($ids)" \
            >/dev/null 2>&1 || cleanup_status=1
        sql "DELETE FROM starfree_users WHERE uid IN ($ids)" \
            >/dev/null 2>&1 || cleanup_status=1
    fi
    # Exact-name cleanup is also run after uid cleanup, so a partial lookup
    # failure cannot leave one of this execution's disposable users behind.
    sql "DELETE FROM starfree_users WHERE name IN ('$OWNER_NAME','$NEW_FIRST_NAME','$OLD_FIRST_NAME','$PUBLIC_NAME')" \
        >/dev/null 2>&1 || cleanup_status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || cleanup_status=1
    if [[ $cleanup_status -ne 0 ]]; then
        echo "WARNING: inspect disposable Space-like cleanup for suffix $suffix" >&2
    fi
    return $cleanup_status
}
trap cleanup EXIT

now="$(date +%s)"
sql "INSERT INTO starfree_users
    (name,password,mail,created,activated,logged,\`group\`,authCode,bantime,vip)
    VALUES
    ('$OWNER_NAME','$TEST_HASH','$OWNER_NAME@invalid.local',$now,$now,0,'contributor',NULL,0,0),
    ('$NEW_FIRST_NAME','$TEST_HASH','$NEW_FIRST_NAME@invalid.local',$now,$now,0,'contributor',NULL,0,0),
    ('$OLD_FIRST_NAME','$TEST_HASH','$OLD_FIRST_NAME@invalid.local',$now,$now,0,'contributor',NULL,0,0),
    ('$PUBLIC_NAME','$TEST_HASH','$PUBLIC_NAME@invalid.local',$now,$now,0,'contributor',NULL,0,0)"
OWNER_UID="$(sql "SELECT uid FROM starfree_users WHERE name = '$OWNER_NAME' LIMIT 1")"
NEW_FIRST_UID="$(sql "SELECT uid FROM starfree_users WHERE name = '$NEW_FIRST_NAME' LIMIT 1")"
OLD_FIRST_UID="$(sql "SELECT uid FROM starfree_users WHERE name = '$OLD_FIRST_NAME' LIMIT 1")"
PUBLIC_UID="$(sql "SELECT uid FROM starfree_users WHERE name = '$PUBLIC_NAME' LIMIT 1")"
for uid in "$OWNER_UID" "$NEW_FIRST_UID" "$OLD_FIRST_UID" "$PUBLIC_UID"; do
    [[ "$uid" =~ ^[1-9][0-9]*$ ]] || {
        echo "Could not resolve every disposable user id" >&2
        exit 11
    }
done
echo "disposable_uids=$OWNER_UID,$NEW_FIRST_UID,$OLD_FIRST_UID,$PUBLIC_UID"

login_user() {
    local name="$1" uid="$2" response token params
    params="$(printf '{\"name\":\"%s\",\"password\":\"%s\"}' "$name" "$TEST_PASSWORD")"
    response="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeUsers/userLogin" \
        --data-urlencode "params=$params")"
    assert_code "login_$name" 1 "$response" >&2
    token="$(sql "SELECT authCode FROM starfree_users WHERE uid = $uid")"
    [[ "$token" =~ ^${name}[0-9a-f]{32}$ ]] || {
        echo "Unexpected token format for $name" >&2
        exit 12
    }
    printf '%s' "$token"
}

NEW_FIRST_TOKEN="$(login_user "$NEW_FIRST_NAME" "$NEW_FIRST_UID")"
OLD_FIRST_TOKEN="$(login_user "$OLD_FIRST_NAME" "$OLD_FIRST_UID")"
PUBLIC_TOKEN="$(login_user "$PUBLIC_NAME" "$PUBLIC_UID")"

# Clear MySQL tokens. Every like below must authenticate from the shared,
# Java-serialized Redis session used by both old and replacement APIs.
sql "UPDATE starfree_users SET authCode = NULL WHERE uid IN ($NEW_FIRST_UID,$OLD_FIRST_UID,$PUBLIC_UID)"
token_rows="$(sql "SELECT COUNT(*) FROM starfree_users WHERE uid IN ($NEW_FIRST_UID,$OLD_FIRST_UID,$PUBLIC_UID) AND authCode IS NOT NULL")"
[[ "$token_rows" == 0 ]] || { echo "MySQL token cleanup failed" >&2; exit 13; }
echo "mysql_token_rows=0"

sql "INSERT INTO starfree_space
    (uid,created,modified,text,pic,type,likes,toid,status,onlyMe)
    VALUES
    ($OWNER_UID,$now,$now,'codex disposable Space-like smoke',NULL,0,0,0,1,0)"
SPACE_ID="$(sql "SELECT id FROM starfree_space WHERE uid = $OWNER_UID ORDER BY id DESC LIMIT 1")"
[[ "$SPACE_ID" =~ ^[1-9][0-9]*$ ]] || { echo "Could not resolve Space id" >&2; exit 14; }
echo "disposable_space_id=$SPACE_ID"

replacement_first="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceLikes" \
    --data-urlencode "id=$SPACE_ID" --data-urlencode "token=$NEW_FIRST_TOKEN")"
assert_code replacement_first_like 1 "$replacement_first"
legacy_after_replacement="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceLikes" \
    --data-urlencode "id=$SPACE_ID" --data-urlencode "token=$NEW_FIRST_TOKEN")"
assert_code legacy_duplicate_after_replacement 0 "$legacy_after_replacement"

legacy_first="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceLikes" \
    --data-urlencode "id=$SPACE_ID" --data-urlencode "token=$OLD_FIRST_TOKEN")"
assert_code legacy_first_like 1 "$legacy_first"
replacement_after_legacy="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceLikes" \
    --data-urlencode "id=$SPACE_ID" --data-urlencode "token=$OLD_FIRST_TOKEN")"
assert_code replacement_duplicate_after_legacy 0 "$replacement_after_legacy"

expected_likes=2
if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    public_first="$(curl -fsS -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeSpace/spaceLikes" \
        --data-urlencode "id=$SPACE_ID" --data-urlencode "token=$PUBLIC_TOKEN")"
    assert_code public_first_like 1 "$public_first"
    if ! grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE"; then
        echo "Public Space-like route did not report $EXPECTED_PUBLIC_BACKEND" >&2
        sed -n '/^[Xx]-[Ss]tarfree-[Bb]ackend:/p' "$HEADER_FILE" >&2
        exit 15
    fi
    echo "public_space_like_backend=$EXPECTED_PUBLIC_BACKEND"
    public_duplicate="$(curl -fsS -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeSpace/spaceLikes" \
        --data-urlencode "id=$SPACE_ID" --data-urlencode "token=$PUBLIC_TOKEN")"
    assert_code public_duplicate_like 0 "$public_duplicate"
    expected_likes=3
fi

like_count="$(sql "SELECT COALESCE(likes,0) FROM starfree_space WHERE id = $SPACE_ID")"
log_count="$(sql "SELECT COUNT(*) FROM starfree_userlog WHERE cid = $SPACE_ID AND type = 'spaceLike'")"
[[ "$like_count" == "$expected_likes" ]] || {
    echo "Space like counter mismatch: expected=$expected_likes actual=$like_count" >&2
    exit 16
}
[[ "$log_count" == "$expected_likes" ]] || {
    echo "Space like log mismatch: expected=$expected_likes actual=$log_count" >&2
    exit 17
}
echo "space_likes=$like_count"
echo "space_like_logs=$log_count"

cleanup
trap - EXIT
remaining_users="$(sql "SELECT COUNT(*) FROM starfree_users WHERE name IN ('$OWNER_NAME','$NEW_FIRST_NAME','$OLD_FIRST_NAME','$PUBLIC_NAME')")"
remaining_space="$(sql "SELECT COUNT(*) FROM starfree_space WHERE text = 'codex disposable Space-like smoke' AND uid = $OWNER_UID")"
[[ "$remaining_users" == 0 && "$remaining_space" == 0 ]] || {
    echo "Disposable cleanup failed: users=$remaining_users space=$remaining_space" >&2
    exit 18
}
echo "disposable_cleanup=PASS"
echo "space_like_smoke=PASS"
