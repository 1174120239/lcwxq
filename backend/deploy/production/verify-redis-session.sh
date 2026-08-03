#!/usr/bin/env bash
set -euo pipefail

# Creates one disposable user and one private Space row. After login, authCode
# is cleared so successful reads prove the Java-serialized Redis session bridge.

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
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

require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "Required command not found: $1" >&2
        exit 2
    }
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

require_command awk
require_command curl
require_command mysql
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

suffix="$(date +%s)_$$"
TEST_NAME="codex_r_${suffix}"
TEST_MAIL="${TEST_NAME}@invalid.local"
TEST_PASSWORD='correct horse battery staple'
TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
TEST_UID=''
SPACE_ID=''
TOKEN=''
SESSION_ACTIVE=false
PUBLIC_HEADER_FILE="/tmp/starfree-redis-session-headers-$$"

cleanup() {
    local cleanup_status=0
    set +e

    # Redis keys use Java serialization. Cleanup therefore calls signOut through
    # the replacement instead of trying to construct binary keys in redis-cli.
    if [[ "$SESSION_ACTIVE" == true && -n "$TOKEN" && -n "$TEST_UID" ]]; then
        sql "UPDATE starfree_users SET authCode = '$TOKEN' WHERE uid = $TEST_UID" \
            >/dev/null 2>&1
        curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" \
            --data-urlencode "token=$TOKEN" >/dev/null 2>&1 || cleanup_status=1
    fi
    if [[ -n "$SPACE_ID" ]]; then
        sql "DELETE FROM starfree_space WHERE id = $SPACE_ID" >/dev/null 2>&1 \
            || cleanup_status=1
    fi
    if [[ -n "$TEST_UID" ]]; then
        sql "DELETE FROM starfree_users WHERE uid = $TEST_UID AND name = '$TEST_NAME'" \
            >/dev/null 2>&1 || cleanup_status=1
    else
        sql "DELETE FROM starfree_users WHERE name = '$TEST_NAME'" >/dev/null 2>&1 \
            || cleanup_status=1
    fi
    rm -f "$PUBLIC_HEADER_FILE" >/dev/null 2>&1 || cleanup_status=1
    if [[ $cleanup_status -ne 0 ]]; then
        echo "WARNING: inspect disposable cleanup for $TEST_NAME" >&2
    fi
    return $cleanup_status
}
trap cleanup EXIT

now="$(date +%s)"
sql "INSERT INTO starfree_users
    (name,password,mail,created,activated,logged,\`group\`,authCode,bantime,vip)
    VALUES
    ('$TEST_NAME','$TEST_HASH','$TEST_MAIL',$now,$now,0,'contributor',NULL,0,0)"
TEST_UID="$(sql "SELECT uid FROM starfree_users WHERE name = '$TEST_NAME' LIMIT 1")"
[[ "$TEST_UID" =~ ^[1-9][0-9]*$ ]] || {
    echo "Could not resolve disposable user id" >&2
    exit 11
}
echo "disposable_uid=$TEST_UID"

login_params="$(printf '{\"name\":\"%s\",\"password\":\"%s\"}' \
    "$TEST_NAME" "$TEST_PASSWORD")"
login_response="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeUsers/userLogin" \
    --data-urlencode "params=$login_params")"
assert_code replacement_login 1 "$login_response"

TOKEN="$(sql "SELECT authCode FROM starfree_users WHERE uid = $TEST_UID")"
[[ "$TOKEN" =~ ^${TEST_NAME}[0-9a-f]{32}$ ]] || {
    echo "Replacement issued an unexpected token format" >&2
    exit 12
}
SESSION_ACTIVE=true

# MySQL cannot authenticate this token during the following checks. Success in
# either service therefore comes from the shared Redis session only.
sql "UPDATE starfree_users SET authCode = NULL WHERE uid = $TEST_UID"
mysql_token_count="$(sql "SELECT COUNT(*) FROM starfree_users WHERE authCode = '$TOKEN'")"
[[ "$mysql_token_count" == 0 ]] || {
    echo "Disposable token still exists in MySQL" >&2
    exit 13
}
echo "mysql_token_rows=0"

replacement_status="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/userStatus" \
    --data-urlencode "token=$TOKEN")"
assert_code replacement_redis_status 1 "$replacement_status"

legacy_status="$(curl -fsS -G "$LEGACY_URL/SFreeUsers/userStatus" \
    --data-urlencode "token=$TOKEN")"
assert_code legacy_redis_status 1 "$legacy_status"

sql "INSERT INTO starfree_space
    (uid,created,modified,text,pic,type,likes,toid,status,onlyMe)
    VALUES
    ($TEST_UID,$now,$now,'codex redis-only private smoke',NULL,0,0,0,1,1)"
SPACE_ID="$(sql "SELECT id FROM starfree_space
    WHERE uid = $TEST_UID ORDER BY id DESC LIMIT 1")"
[[ "$SPACE_ID" =~ ^[1-9][0-9]*$ ]] || {
    echo "Could not resolve disposable Space id" >&2
    exit 14
}
echo "disposable_space_id=$SPACE_ID"

anonymous_info="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceInfo" \
    --data-urlencode "id=$SPACE_ID")"
assert_code replacement_anonymous_private 0 "$anonymous_info"

replacement_info="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/spaceInfo" \
    --data-urlencode "id=$SPACE_ID" --data-urlencode "token=$TOKEN")"
assert_code replacement_owner_private 1 "$replacement_info"

legacy_info="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/spaceInfo" \
    --data-urlencode "id=$SPACE_ID" --data-urlencode "token=$TOKEN")"
assert_code legacy_owner_private 1 "$legacy_info"

# Exercise the public Nginx route with the same Redis-only token. This is the
# final cutover proof: MySQL authCode is still NULL, the private record is not
# visible anonymously, and both authenticated Space reads must be served by the
# replacement backend through HTTPS.
public_info="$(curl -fsS -D "$PUBLIC_HEADER_FILE" -G \
    "$PUBLIC_URL/SFreeSpace/spaceInfo" \
    --data-urlencode "id=$SPACE_ID" --data-urlencode "token=$TOKEN")"
assert_code public_owner_private_info 1 "$public_info"
if ! grep -qi '^X-Starfree-Backend: replacement-public-read' "$PUBLIC_HEADER_FILE"; then
    echo "Public spaceInfo did not report replacement-public-read" >&2
    sed -n '/^[Xx]-[Ss]tarfree-[Bb]ackend:/p' "$PUBLIC_HEADER_FILE" >&2
    exit 15
fi
echo "public_space_info_backend=replacement-public-read"

search_params="$(printf '{\"uid\":%s}' "$TEST_UID")"
public_list="$(curl -fsS -D "$PUBLIC_HEADER_FILE" -G \
    "$PUBLIC_URL/SFreeSpace/spaceList" \
    --data-urlencode "searchParams=$search_params" \
    --data-urlencode "limit=10" --data-urlencode "page=1" \
    --data-urlencode "token=$TOKEN")"
assert_code public_owner_private_list 1 "$public_list"
if ! grep -qi '^X-Starfree-Backend: replacement-public-read' "$PUBLIC_HEADER_FILE"; then
    echo "Public spaceList did not report replacement-public-read" >&2
    sed -n '/^[Xx]-[Ss]tarfree-[Bb]ackend:/p' "$PUBLIC_HEADER_FILE" >&2
    exit 16
fi
if ! printf '%s' "$public_list" | "$PYTHON_BIN" -c \
    'import json,sys; wanted=int(sys.argv[1]); data=json.load(sys.stdin).get("data") or []; raise SystemExit(0 if any(int(item.get("id", 0)) == wanted for item in data) else 1)' \
    "$SPACE_ID"; then
    echo "Public authenticated spaceList did not contain the private test row" >&2
    exit 17
fi
echo "public_space_list_backend=replacement-public-read"

# Normal logout proves that the same serializer can also remove the Redis hash
# and account link. authCode is restored only for this logout call.
sql "UPDATE starfree_users SET authCode = '$TOKEN' WHERE uid = $TEST_UID"
signout_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" \
    --data-urlencode "token=$TOKEN")"
assert_code replacement_signout 1 "$signout_response"
SESSION_ACTIVE=false

signed_out_status="$(curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/userStatus" \
    --data-urlencode "token=$TOKEN")"
assert_code replacement_signed_out_status 0 "$signed_out_status"

cleanup
trap - EXIT
echo "redis_session_smoke=PASS"
