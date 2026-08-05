#!/usr/bin/env bash
set -euo pipefail

# End-to-end registration smoke test. It does not change registration config.
# Verification codes are written with Java String serialization so the key is
# byte-for-byte compatible with the legacy RedisTemplate.
PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
REGISTER_URL=${REGISTER_URL:-$REPLACEMENT_URL}
EXPECTED_BACKEND=${EXPECTED_BACKEND:-}
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
    echo "Python is required for JSON and binary Redis verification" >&2
    exit 2
fi

DB_USERNAME=${DB_USERNAME:-$(read_property spring.datasource.username)}
DB_PASSWORD=${DB_PASSWORD:-$(read_property spring.datasource.password)}
REDIS_HOST=${REDIS_HOST:-$(read_property spring.redis.host)}
REDIS_PORT=${REDIS_PORT:-$(read_property spring.redis.port)}
REDIS_PASSWORD=${REDIS_PASSWORD-$(read_property spring.redis.password)}
REDIS_DATABASE=${REDIS_DATABASE:-$(read_property spring.redis.database)}
REDIS_PREFIX=${REDIS_PREFIX:-$(read_property web.prefix)}
REDIS_HOST=${REDIS_HOST:-127.0.0.1}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_DATABASE=${REDIS_DATABASE:-0}
REDIS_PREFIX=${REDIS_PREFIX:-starfree}
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

json_value() {
    local key="$1"
    "$PYTHON_BIN" -c \
        'import json,sys; value=json.load(sys.stdin).get(sys.argv[1], ""); print(value)' \
        "$key"
}

assert_code() {
    local label="$1" expected="$2" response="$3" actual
    actual="$(printf '%s' "$response" | json_value code)"
    [[ "$actual" == "$expected" ]] || {
        echo "$label returned code=$actual, expected $expected" >&2
        echo "$response" >&2
        exit 10
    }
    echo "$label=code:$actual"
}

# Sends RESP directly because redis-cli cannot carry NUL bytes in a serialized
# Java String key. Only ASCII test values are used, so modified UTF-8 and UTF-8
# have the same payload bytes.
redis_code() {
    local action="$1"
    REDIS_ACTION="$action" REDIS_EMAIL="$TEST_MAIL" REDIS_CODE="$TEST_CODE" \
    REDIS_HOST_VALUE="$REDIS_HOST" REDIS_PORT_VALUE="$REDIS_PORT" \
    REDIS_PASSWORD_VALUE="$REDIS_PASSWORD" REDIS_DATABASE_VALUE="$REDIS_DATABASE" \
    REDIS_PREFIX_VALUE="$REDIS_PREFIX" "$PYTHON_BIN" <<'PY'
import os
import socket
import struct

def java_string(value):
    raw = value.encode("utf-8")
    if len(raw) > 65535:
        raise RuntimeError("Java test string is too long")
    return b"\xac\xed\x00\x05t" + struct.pack(">H", len(raw)) + raw

sock = socket.create_connection(
    (os.environ["REDIS_HOST_VALUE"], int(os.environ["REDIS_PORT_VALUE"])), 5)
stream = sock.makefile("rb")

def read_exact(size):
    value = stream.read(size)
    if len(value) != size:
        raise RuntimeError("Short Redis response")
    return value

def read_line():
    value = stream.readline()
    if not value.endswith(b"\r\n"):
        raise RuntimeError("Malformed Redis response")
    return value[:-2]

def response():
    marker = read_exact(1)
    if marker == b"+":
        return read_line()
    if marker == b"-":
        raise RuntimeError(read_line().decode("utf-8", "replace"))
    if marker == b":":
        return int(read_line())
    if marker == b"$":
        size = int(read_line())
        if size == -1:
            return None
        value = read_exact(size)
        if read_exact(2) != b"\r\n":
            raise RuntimeError("Malformed Redis bulk response")
        return value
    raise RuntimeError("Unsupported Redis response marker")

def command(*parts):
    encoded = []
    for part in parts:
        encoded.append(part if isinstance(part, bytes) else str(part).encode("utf-8"))
    payload = b"*%d\r\n" % len(encoded)
    for part in encoded:
        payload += b"$%d\r\n" % len(part) + part + b"\r\n"
    sock.sendall(payload)
    return response()

password = os.environ.get("REDIS_PASSWORD_VALUE", "")
if password:
    command("AUTH", password)
database = int(os.environ.get("REDIS_DATABASE_VALUE", "0"))
if database:
    command("SELECT", database)

key = java_string(
    os.environ["REDIS_PREFIX_VALUE"] + "_sendCode" + os.environ["REDIS_EMAIL"])
action = os.environ["REDIS_ACTION"]
if action == "set":
    result = command("SET", key, java_string(os.environ["REDIS_CODE"]), "EX", 1800)
    if result != b"OK":
        raise RuntimeError("Could not set the serialized verification code")
elif action == "missing":
    if command("GET", key) is not None:
        raise RuntimeError("Verification code was not consumed")
elif action == "delete":
    command("DEL", key)
else:
    raise RuntimeError("Unknown Redis action")
sock.close()
PY
}

suffix="$(date +%s)$(printf '%04d' "$(( $$ % 10000 ))")"
TEST_NAME="cr_${suffix}"
INVITER_NAME="cri_${suffix}"
TEST_MAIL="${TEST_NAME}@example.invalid"
INVITER_MAIL="${INVITER_NAME}@example.invalid"
INVITE_CODE="CDX${suffix}"
TEST_PASSWORD='correct horse battery staple'
TEST_CODE='482731'
TEST_UID=''
INVITER_UID=''
TOKEN=''
SESSION_ACTIVE=false
CODE_CREATED=false
HEADER_FILE="/tmp/starfree-register-headers-$$"

cleanup() {
    local cleanup_status=0
    set +e
    if [[ "$SESSION_ACTIVE" == true && "$TEST_UID" =~ ^[1-9][0-9]*$ && -n "$TOKEN" ]]; then
        sql "UPDATE starfree_users SET authCode='$TOKEN' WHERE uid=$TEST_UID" \
            >/dev/null 2>&1
        curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" \
            --data-urlencode "token=$TOKEN" >/dev/null 2>&1 || cleanup_status=1
    fi
    redis_code delete >/dev/null 2>&1 || cleanup_status=1
    if [[ "$INVITER_UID" =~ ^[1-9][0-9]*$ ]]; then
        sql "DELETE FROM starfree_paylog WHERE uid=$INVITER_UID" \
            >/dev/null 2>&1 || cleanup_status=1
    fi
    sql "DELETE FROM starfree_economy_operations
         WHERE operation_type='user-register' AND payload_json LIKE '%$TEST_NAME%';
         DELETE FROM starfree_invitation WHERE code='$INVITE_CODE';
         DELETE FROM starfree_users WHERE name IN ('$TEST_NAME','$INVITER_NAME');" \
        >/dev/null 2>&1 || cleanup_status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || cleanup_status=1
    if [[ $cleanup_status -ne 0 ]]; then
        echo "WARNING: inspect disposable registration cleanup for $suffix" >&2
    fi
    return $cleanup_status
}
trap cleanup EXIT

read -r IS_EMAIL IS_INVITE REBATE_LEVEL REBATE_NUM BAN_ROBOTS <<< \
    "$(sql "SELECT isEmail,isInvite,rebateLevel,rebateNum,banRobots
             FROM starfree_apiconfig ORDER BY id LIMIT 1")"
for value in "$IS_EMAIL" "$IS_INVITE" "$REBATE_LEVEL" "$REBATE_NUM" "$BAN_ROBOTS"; do
    [[ "$value" =~ ^[0-9]+$ ]] || {
        echo "Invalid registration configuration" >&2
        exit 11
    }
done

now="$(date +%s)"
sql "INSERT INTO starfree_users
    (name,mail,screenName,created,\`group\`,assets,experience,points)
    VALUES
    ('$INVITER_NAME','$INVITER_MAIL','$INVITER_NAME',$now,'contributor',20,0,0)"
INVITER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$INVITER_NAME' LIMIT 1")"
[[ "$INVITER_UID" =~ ^[1-9][0-9]*$ ]] || {
    echo "Could not resolve disposable inviter" >&2
    exit 12
}
if [[ "$IS_INVITE" == 1 ]]; then
    sql "INSERT INTO starfree_invitation (code,created,uid,status)
         VALUES ('$INVITE_CODE',$now,$INVITER_UID,0)"
fi
if [[ "$IS_EMAIL" -gt 0 ]]; then
    redis_code set
    CODE_CREATED=true
fi

CAMPUS_ID="$(sql "SELECT id FROM starfree_identity_options WHERE type='campus' AND enabled=1 ORDER BY sort_order DESC,id DESC LIMIT 1")"
GRADE_ID="$(sql "SELECT id FROM starfree_identity_options WHERE type='grade' AND enabled=1 ORDER BY sort_order DESC,id DESC LIMIT 1")"
[[ "$CAMPUS_ID" =~ ^[1-9][0-9]*$ && "$GRADE_ID" =~ ^[1-9][0-9]*$ ]] || {
    echo "No enabled campus or grade option is available" >&2
    exit 22
}

export TEST_NAME TEST_MAIL TEST_PASSWORD TEST_CODE INVITE_CODE IS_INVITE CAMPUS_ID GRADE_ID
register_params="$($PYTHON_BIN -c '
import json,os
data = {
    "name": os.environ["TEST_NAME"],
    "password": os.environ["TEST_PASSWORD"],
    "mail": os.environ["TEST_MAIL"],
    "code": os.environ["TEST_CODE"],
    "assets": 999999,
    "points": 999999,
    "experience": 999999,
    "vip": 999999,
    "campusId": int(os.environ["CAMPUS_ID"]),
    "gradeId": int(os.environ["GRADE_ID"]),
}
if os.environ["IS_INVITE"] == "1":
    data["inviteCode"] = os.environ["INVITE_CODE"]
print(json.dumps(data, separators=(",", ":")))
')"

register_response="$(curl -fsS -D "$HEADER_FILE" -X POST \
    "$REGISTER_URL/SFreeUsers/userRegister" \
    --data-urlencode "params=$register_params")"
assert_code registration_first 1 "$register_response"
[[ "$(printf '%s' "$register_response" | json_value data)" == 1 ]] || {
    echo "Registration did not preserve legacy data=1" >&2
    exit 13
}
if [[ -n "$EXPECTED_BACKEND" ]] && \
        ! grep -qi "^X-Starfree-Backend: $EXPECTED_BACKEND" "$HEADER_FILE"; then
    echo "Registration route did not report $EXPECTED_BACKEND" >&2
    sed -n '/^[Xx]-[Ss]tarfree-[Bb]ackend:/p' "$HEADER_FILE" >&2
    exit 14
fi

# The first request may create the legacy three-second IP burst key. Waiting
# makes the replay test valid even when banRobots is enabled.
sleep 4
replay_response="$(curl -fsS -D "$HEADER_FILE" -X POST \
    "$REGISTER_URL/SFreeUsers/userRegister" \
    --data-urlencode "params=$register_params")"
assert_code registration_replay 1 "$replay_response"
if [[ -n "$EXPECTED_BACKEND" ]] && \
        ! grep -qi "^X-Starfree-Backend: $EXPECTED_BACKEND" "$HEADER_FILE"; then
    echo "Registration replay route did not report $EXPECTED_BACKEND" >&2
    exit 14
fi

TEST_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$TEST_NAME' LIMIT 1")"
[[ "$TEST_UID" =~ ^[1-9][0-9]*$ ]] || {
    echo "Could not resolve registered user" >&2
    exit 15
}
expected_inviter=0
expected_assets=20
expected_rebate_logs=0
if [[ "$IS_INVITE" == 1 ]]; then
    expected_inviter="$INVITER_UID"
    if [[ "$REBATE_LEVEL" == 1 || "$REBATE_LEVEL" == 3 ]]; then
        expected_assets="$((20 + REBATE_NUM))"
        [[ "$REBATE_NUM" -gt 0 ]] && expected_rebate_logs=1
    fi
fi

IFS='|' read -r assets points experience vip campus_id grade_id invitation_user hash_prefix user_count \
    inviter_assets invitation_status rebate_logs committed_ops review_ops <<< \
    "$(sql "SELECT CONCAT_WS('|',u.assets,u.points,u.experience,u.vip,
        u.campus_option_id,u.grade_option_id,u.invitationUser,LEFT(u.password,4),
        (SELECT COUNT(*) FROM starfree_users WHERE name='$TEST_NAME'),
        (SELECT assets FROM starfree_users WHERE uid=$INVITER_UID),
        COALESCE((SELECT status FROM starfree_invitation WHERE code='$INVITE_CODE' LIMIT 1),0),
        (SELECT COUNT(*) FROM starfree_paylog WHERE uid=$INVITER_UID AND paytype='rebate'),
        (SELECT COUNT(*) FROM starfree_economy_operations
         WHERE operation_type='user-register' AND payload_json LIKE '%$TEST_NAME%'
           AND state='committed'),
        (SELECT COUNT(*) FROM starfree_economy_operations
         WHERE operation_type='user-register' AND payload_json LIKE '%$TEST_NAME%'
           AND state='needs_review'))
        FROM starfree_users u WHERE u.uid=$TEST_UID")"

[[ "$assets|$points|$experience|$vip" == '0|0|0|0' ]] || {
    echo "Client-controlled account values reached the user row" >&2
    exit 16
}
[[ "$campus_id" == "$CAMPUS_ID" && "$grade_id" == "$GRADE_ID" ]] || {
    echo "Campus or grade identity was not stored" >&2
    exit 23
}
[[ "$invitation_user" == "$expected_inviter" && "$hash_prefix" == '$P$B' ]] || {
    echo "Invitation relationship or PHPass hash is incorrect" >&2
    exit 17
}
[[ "$user_count" == 1 && "$inviter_assets" == "$expected_assets" \
    && "$rebate_logs" == "$expected_rebate_logs" \
    && "$committed_ops" == 1 && "$review_ops" == 0 ]] || {
    echo "Registration replay, rebate, or journal verification failed" >&2
    exit 18
}
if [[ "$IS_INVITE" == 1 && "$invitation_status" != 1 ]]; then
    echo "Invitation code was not consumed" >&2
    exit 19
fi
if [[ "$IS_EMAIL" -gt 0 ]]; then
    redis_code missing
fi

login_params="$(printf '{\"name\":\"%s\",\"password\":\"%s\"}' \
    "$TEST_NAME" "$TEST_PASSWORD")"
login_response="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeUsers/userLogin" \
    --data-urlencode "params=$login_params")"
assert_code generated_password_login 1 "$login_response"
TOKEN="$(sql "SELECT authCode FROM starfree_users WHERE uid=$TEST_UID")"
[[ -n "$TOKEN" ]] || {
    echo "Login did not issue a token" >&2
    exit 20
}
SESSION_ACTIVE=true

echo "registration_uid=$TEST_UID"
echo "registration_config=email:$IS_EMAIL,invite:$IS_INVITE,rebateLevel:$REBATE_LEVEL,rebateNum:$REBATE_NUM"
echo "registration_replay_rows=$user_count"
echo "registration_rebate_logs=$rebate_logs"
echo "registration_needs_review=$review_ops"

cleanup
trap - EXIT
residue="$(sql "SELECT CONCAT_WS('|',
    (SELECT COUNT(*) FROM starfree_users WHERE name IN ('$TEST_NAME','$INVITER_NAME')),
    (SELECT COUNT(*) FROM starfree_invitation WHERE code='$INVITE_CODE'),
    (SELECT COUNT(*) FROM starfree_economy_operations
     WHERE operation_type='user-register' AND payload_json LIKE '%$TEST_NAME%'))")"
[[ "$residue" == '0|0|0' ]] || {
    echo "Registration smoke left SQL residue: $residue" >&2
    exit 21
}
echo "registration_residue=$residue"
echo "user_registration_smoke=PASS"
