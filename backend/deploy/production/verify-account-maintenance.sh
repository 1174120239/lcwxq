#!/usr/bin/env bash
set -euo pipefail

# End-to-end account maintenance smoke test. All Redis keys use the Java String
# serialization used by the closed API. Disposable SQL rows are deleted only by
# their resolved IDs; no prefix-wide DELETE is used.
PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LOGIN_URL=${LOGIN_URL:-$REPLACEMENT_URL}
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

data_value() {
    local key="$1"
    "$PYTHON_BIN" -c \
        'import json,sys; value=json.load(sys.stdin).get("data") or {}; print(value.get(sys.argv[1], ""))' \
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

assert_redis_missing() {
    local label="$1" key="$2"
    if ! redis_serialized missing "$key"; then
        echo "$label still exists in Redis" >&2
        return 1
    fi
    echo "$label=missing"
}

api_get() {
    local path="$1"
    shift
    curl -fsS -G --max-time 30 "$REPLACEMENT_URL$path" "$@"
}

api_post() {
    local path="$1"
    shift
    curl -fsS --max-time 30 -X POST "$REPLACEMENT_URL$path" "$@"
}

api_login() {
    curl -fsS --max-time 30 -X POST "$LOGIN_URL/SFreeUsers/userLogin" "$@"
}

# Sends RESP directly because redis-cli cannot pass NUL bytes in a serialized
# Java String key. The helper supports both String code keys and Hash session
# keys through EXISTS, without needing to deserialize a hash payload.
redis_serialized() {
    local action="$1" key="$2" value="${3:-}"
    REDIS_ACTION="$action" REDIS_KEY="$key" REDIS_VALUE="$value" \
    REDIS_HOST_VALUE="$REDIS_HOST" REDIS_PORT_VALUE="$REDIS_PORT" \
    REDIS_PASSWORD_VALUE="$REDIS_PASSWORD" REDIS_DATABASE_VALUE="$REDIS_DATABASE" \
    "$PYTHON_BIN" <<'PY'
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

def exact(size):
    value = stream.read(size)
    if len(value) != size:
        raise RuntimeError("Short Redis response")
    return value

def line():
    value = stream.readline()
    if not value.endswith(b"\r\n"):
        raise RuntimeError("Malformed Redis response")
    return value[:-2]

def response():
    marker = exact(1)
    if marker == b"+":
        return line()
    if marker == b"-":
        raise RuntimeError(line().decode("utf-8", "replace"))
    if marker == b":":
        return int(line())
    if marker == b"$":
        size = int(line())
        if size == -1:
            return None
        value = exact(size)
        if exact(2) != b"\r\n":
            raise RuntimeError("Malformed Redis bulk response")
        return value
    raise RuntimeError("Unsupported Redis response marker")

def command(*parts):
    values = [part if isinstance(part, bytes) else str(part).encode("utf-8")
              for part in parts]
    payload = b"*%d\r\n" % len(values)
    for part in values:
        payload += b"$%d\r\n" % len(part) + part + b"\r\n"
    sock.sendall(payload)
    return response()

password = os.environ.get("REDIS_PASSWORD_VALUE", "")
if password:
    command("AUTH", password)
database = int(os.environ.get("REDIS_DATABASE_VALUE", "0"))
if database:
    command("SELECT", database)

key = java_string(os.environ["REDIS_KEY"])
action = os.environ["REDIS_ACTION"]
if action == "set":
    result = command("SET", key, java_string(os.environ["REDIS_VALUE"]), "EX", 1800)
    if result != b"OK":
        raise RuntimeError("Could not set serialized Redis value")
elif action == "exists":
    if command("EXISTS", key) != 1:
        raise RuntimeError("Expected serialized Redis key is missing")
elif action == "missing":
    if command("EXISTS", key) != 0:
        raise RuntimeError("Serialized Redis key was not removed")
elif action == "delete":
    command("DEL", key)
else:
    raise RuntimeError("Unknown Redis action")
sock.close()
PY
}

suffix="$(date +%s)$(printf '%04d' "$(( $$ % 10000 ))")"
PRIMARY_NAME="cam_${suffix}"
DUPLICATE_NAME="cmb_${suffix}"
PRIMARY_MAIL="${PRIMARY_NAME}@example.invalid"
NEW_MAIL="${PRIMARY_NAME}.new@example.invalid"
DUPLICATE_MAIL="${DUPLICATE_NAME}@example.invalid"
PHONE="139${suffix: -8}"
ORIGINAL_PASSWORD='correct horse battery staple'
ORIGINAL_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
NEW_PASSWORD='account-maintenance-new-password'
FINAL_PASSWORD='account-maintenance-final-password'
MAIL_CODE='482731'
PHONE_CODE='654321'
PRIMARY_UID=''
DUPLICATE_UID=''
FINAL_TOKEN=''
TOKENS=()

cleanup() {
    local cleanup_status=0 token key sql_residue=0
    set +e
    if [[ -n "$FINAL_TOKEN" ]]; then
        api_get /SFreeUsers/signOut --data-urlencode "token=$FINAL_TOKEN" \
            >/dev/null 2>&1 || cleanup_status=1
    fi
    for token in "${TOKENS[@]:-}"; do
        [[ -n "$token" ]] && redis_serialized delete "${REDIS_PREFIX}_userInfo${token}" \
            >/dev/null 2>&1 || true
    done
    for key in \
        "${REDIS_PREFIX}_userkey${PRIMARY_NAME}" \
        "${REDIS_PREFIX}_userkey${PRIMARY_MAIL}" \
        "${REDIS_PREFIX}_userkey${NEW_MAIL}" \
        "${REDIS_PREFIX}_userkey${PHONE}" \
        "${REDIS_PREFIX}_sendCode${PRIMARY_NAME}" \
        "${REDIS_PREFIX}_sendCode${NEW_MAIL}" \
        "${REDIS_PREFIX}_sendSMS${PHONE}"; do
        redis_serialized delete "$key" >/dev/null 2>&1 || cleanup_status=1
    done
    if [[ "$PRIMARY_UID" =~ ^[1-9][0-9]*$ ]]; then
        sql "DELETE FROM starfree_users WHERE uid=$PRIMARY_UID" >/dev/null 2>&1 \
            || cleanup_status=1
    fi
    if [[ "$DUPLICATE_UID" =~ ^[1-9][0-9]*$ ]]; then
        sql "DELETE FROM starfree_users WHERE uid=$DUPLICATE_UID" >/dev/null 2>&1 \
            || cleanup_status=1
    fi
    for token in "${TOKENS[@]:-}"; do
        [[ -z "$token" ]] || redis_serialized missing "${REDIS_PREFIX}_userInfo${token}" \
            >/dev/null 2>&1 || cleanup_status=1
    done
    if [[ "$PRIMARY_UID" =~ ^[1-9][0-9]*$ && "$DUPLICATE_UID" =~ ^[1-9][0-9]*$ ]]; then
        sql_residue="$(sql "SELECT COUNT(*) FROM starfree_users
                            WHERE uid IN ($PRIMARY_UID,$DUPLICATE_UID)" 2>/dev/null)" \
            || cleanup_status=1
        [[ "$sql_residue" == 0 ]] || cleanup_status=1
    fi
    if [[ $cleanup_status -ne 0 ]]; then
        echo "WARNING: inspect disposable account cleanup for $suffix" >&2
    else
        echo "account_maintenance_cleanup=PASS"
    fi
    return $cleanup_status
}
trap cleanup EXIT

sql "INSERT INTO starfree_users
    (name,password,mail,screenName,created,\`group\`,authCode,assets,experience,points)
    VALUES
    ('$PRIMARY_NAME','$ORIGINAL_HASH','$PRIMARY_MAIL','$PRIMARY_NAME',UNIX_TIMESTAMP(),
     'contributor',NULL,41,42,43),
    ('$DUPLICATE_NAME','$ORIGINAL_HASH','$DUPLICATE_MAIL','reserved nickname',UNIX_TIMESTAMP(),
     'contributor',NULL,0,0,0)"
PRIMARY_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$PRIMARY_NAME' LIMIT 1")"
DUPLICATE_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$DUPLICATE_NAME' LIMIT 1")"
[[ "$PRIMARY_UID" =~ ^[1-9][0-9]*$ && "$DUPLICATE_UID" =~ ^[1-9][0-9]*$ ]] || {
    echo "Could not resolve disposable account IDs" >&2
    exit 11
}

config_response="$(api_get /SFreeUsers/regConfig)"
assert_code reg_config 1 "$config_response"
read -r CONFIG_EMAIL CONFIG_INVITE CONFIG_PHONE <<< \
    "$(sql "SELECT isEmail,isInvite,isPhone FROM starfree_apiconfig ORDER BY id LIMIT 1")"
for field in isEmail isInvite isPhone; do
    actual="$(printf '%s' "$config_response" | data_value "$field")"
    case "$field" in
        isEmail) expected="$CONFIG_EMAIL" ;;
        isInvite) expected="$CONFIG_INVITE" ;;
        isPhone) expected="$CONFIG_PHONE" ;;
    esac
    [[ "$actual" == "$expected" ]] || {
        echo "regConfig $field mismatch: $actual != $expected" >&2
        exit 12
    }
done

login_response="$(api_login \
    --data-urlencode "params={\"name\":\"$PRIMARY_NAME\",\"password\":\"$ORIGINAL_PASSWORD\"}")"
assert_code initial_login 1 "$login_response"
TOKEN1="$(printf '%s' "$login_response" | data_value token)"
[[ -n "$TOKEN1" ]] || { echo "Initial login omitted token" >&2; exit 13; }
TOKENS+=("$TOKEN1")
redis_serialized exists "${REDIS_PREFIX}_userInfo${TOKEN1}"

# Clearing MySQL proves userStatus can resolve this token from the shared
# Java-serialized Redis hash written by the replacement login endpoint.
sql "UPDATE starfree_users SET authCode=NULL WHERE uid=$PRIMARY_UID"
redis_status="$(api_get /SFreeUsers/userStatus --data-urlencode "token=$TOKEN1")"
assert_code redis_only_session 1 "$redis_status"
sql "UPDATE starfree_users SET authCode='$TOKEN1' WHERE uid=$PRIMARY_UID"

forged_response="$(api_get /SFreeUsers/userEdit \
    --data-urlencode "token=$TOKEN1" \
    --data-urlencode "params={\"uid\":$DUPLICATE_UID,\"screenName\":\"forged target\"}")"
assert_code forged_uid_rejected 0 "$forged_response"

profile_response="$(api_post /SFreeUsers/userEdit \
    --data-urlencode "token=$TOKEN1" \
    --data-urlencode "params={\"uid\":$PRIMARY_UID,\"name\":\"forged-name\",\"screenName\":\"${PRIMARY_NAME}_screen\",\"introduce\":\"account smoke\",\"address\":\"name|phone|address\",\"pay\":\"alipay|name|account|qr\",\"assets\":999999,\"experience\":999999,\"points\":999999,\"vip\":999999,\"group\":\"administrator\"}")"
assert_code profile_edit 1 "$profile_response"
projection="$(sql "SELECT CONCAT_WS('~',name,screenName,assets,experience,points,vip,\`group\`,introduce)
                    FROM starfree_users WHERE uid=$PRIMARY_UID")"
[[ "$projection" == "$PRIMARY_NAME~${PRIMARY_NAME}_screen~41~42~43~0~contributor~account smoke" ]] || {
    echo "Profile or protected projection mismatch: $projection" >&2
    exit 14
}

duplicate_response="$(api_get /SFreeUsers/userEdit \
    --data-urlencode "token=$TOKEN1" \
    --data-urlencode "params={\"uid\":$PRIMARY_UID,\"screenName\":\"reserved nickname\"}")"
assert_code duplicate_nickname_rejected 0 "$duplicate_response"

client_response="$(api_get /SFreeUsers/setClientId \
    --data-urlencode "token=$TOKEN1" \
    --data-urlencode "clientId=${PRIMARY_NAME}_push")"
assert_code set_client_id 1 "$client_response"
[[ "$(sql "SELECT clientId FROM starfree_users WHERE uid=$PRIMARY_UID")" == "${PRIMARY_NAME}_push" ]] \
    || { echo "Client ID projection mismatch" >&2; exit 15; }

redis_serialized set "${REDIS_PREFIX}_sendSMS${PHONE}" "$PHONE_CODE"
phone_response="$(api_get /SFreeUsers/userEdit \
    --data-urlencode "token=$TOKEN1" \
    --data-urlencode "params={\"uid\":$PRIMARY_UID,\"phone\":\"$PHONE\",\"code\":\"$PHONE_CODE\"}")"
assert_code phone_edit 1 "$phone_response"
[[ "$(sql "SELECT phone FROM starfree_users WHERE uid=$PRIMARY_UID")" == "$PHONE" ]] \
    || { echo "Phone projection mismatch" >&2; exit 16; }
assert_redis_missing phone_code "${REDIS_PREFIX}_sendSMS${PHONE}"

redis_serialized set "${REDIS_PREFIX}_sendCode${NEW_MAIL}" "$MAIL_CODE"
mail_response="$(api_get /SFreeUsers/userEdit \
    --data-urlencode "token=$TOKEN1" \
    --data-urlencode "params={\"uid\":$PRIMARY_UID,\"mail\":\"$NEW_MAIL\",\"code\":\"$MAIL_CODE\"}")"
assert_code mail_edit 1 "$mail_response"
[[ "$(sql "SELECT CONCAT_WS('|',mail,COALESCE(authCode,'')) FROM starfree_users WHERE uid=$PRIMARY_UID")" \
    == "$NEW_MAIL|" ]] || { echo "Mail edit did not revoke MySQL token" >&2; exit 17; }
assert_redis_missing mail_code "${REDIS_PREFIX}_sendCode${NEW_MAIL}"
assert_redis_missing mail_edit_session "${REDIS_PREFIX}_userInfo${TOKEN1}"

login_response="$(api_login \
    --data-urlencode "params={\"name\":\"$NEW_MAIL\",\"password\":\"$ORIGINAL_PASSWORD\"}")"
assert_code mail_login 1 "$login_response"
TOKEN2="$(printf '%s' "$login_response" | data_value token)"
TOKENS+=("$TOKEN2")

password_response="$(api_post /SFreeUsers/userEdit \
    --data-urlencode "token=$TOKEN2" \
    --data-urlencode "params={\"uid\":$PRIMARY_UID,\"password\":\"$NEW_PASSWORD\"}")"
assert_code password_edit 1 "$password_response"
assert_redis_missing password_edit_session "${REDIS_PREFIX}_userInfo${TOKEN2}"
[[ "$(sql "SELECT CONCAT_WS('|',LEFT(password,4),COALESCE(authCode,'')) FROM starfree_users WHERE uid=$PRIMARY_UID")" \
    == '$P$B|' ]] || { echo "Password edit projection mismatch" >&2; exit 18; }

login_response="$(api_login \
    --data-urlencode "params={\"name\":\"$PRIMARY_NAME\",\"password\":\"$NEW_PASSWORD\"}")"
assert_code changed_password_login 1 "$login_response"
TOKEN3="$(printf '%s' "$login_response" | data_value token)"
TOKENS+=("$TOKEN3")

redis_serialized set "${REDIS_PREFIX}_sendCode${PRIMARY_NAME}" "$MAIL_CODE"
forgot_response="$(api_get /SFreeUsers/userFoget \
    --data-urlencode "params={\"name\":\"$NEW_MAIL\",\"code\":\"$MAIL_CODE\",\"password\":\"$FINAL_PASSWORD\"}")"
assert_code password_reset 1 "$forgot_response"
assert_redis_missing password_reset_code "${REDIS_PREFIX}_sendCode${PRIMARY_NAME}"
assert_redis_missing password_reset_session "${REDIS_PREFIX}_userInfo${TOKEN3}"
[[ -z "$(sql "SELECT COALESCE(authCode,'') FROM starfree_users WHERE uid=$PRIMARY_UID")" ]] \
    || { echo "Password reset did not revoke MySQL token" >&2; exit 19; }

login_response="$(api_login \
    --data-urlencode "params={\"name\":\"$NEW_MAIL\",\"password\":\"$FINAL_PASSWORD\"}")"
assert_code reset_password_login 1 "$login_response"
FINAL_TOKEN="$(printf '%s' "$login_response" | data_value token)"
TOKENS+=("$FINAL_TOKEN")
redis_serialized exists "${REDIS_PREFIX}_userInfo${FINAL_TOKEN}"

residue="$(sql "SELECT COUNT(*) FROM starfree_users
                WHERE uid IN ($PRIMARY_UID,$DUPLICATE_UID)")"
[[ "$residue" == 2 ]] || { echo "Disposable users changed unexpectedly" >&2; exit 20; }

echo "account_maintenance_audit=PASS"
echo "primary_uid=$PRIMARY_UID"
echo "config=$CONFIG_EMAIL,$CONFIG_INVITE,$CONFIG_PHONE"
