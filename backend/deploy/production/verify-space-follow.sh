#!/usr/bin/env bash
set -euo pipefail

# Disposable security/compatibility test for the followed-Space feed. The
# legacy query leaks followed users' onlyMe and reply rows; the replacement
# intentionally returns public, approved, non-reply rows only.

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
LEGACY_URL=${LEGACY_URL:-http://127.0.0.1:8081}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}
EXPECTED_PUBLIC_BACKEND=${EXPECTED_PUBLIC_BACKEND:-replacement-space-follow}
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
assert_ids() {
    local label="$1" response="$2" expected="$3"
    printf '%s' "$response" | "$PYTHON_BIN" -c '
import json,sys
expected=sorted(int(x) for x in sys.argv[1].split(",") if x)
actual=sorted(int(x.get("id",0)) for x in (json.load(sys.stdin).get("data") or []))
if actual != expected:
    sys.stderr.write("%s ids mismatch: expected=%s actual=%s\n" % (sys.argv[2],expected,actual))
    raise SystemExit(1)
' "$expected" "$label" || exit 11
    echo "$label=ids:$expected"
}

epoch="$(date +%s)"
suffix="${epoch: -6}$(printf '%03d' "$(( $$ % 1000 ))")"
OWNER_NAME="csf_o_$suffix"
VIEWER_NAME="csf_v_$suffix"
TEST_PASSWORD='correct horse battery staple'
TEST_HASH='$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/'
OWNER_UID=''; VIEWER_UID=''; TOKEN=''
PUBLIC_ID=''; PRIVATE_ID=''; REPLY_ID=''; PENDING_ID=''
HEADER_FILE="/tmp/starfree-space-follow-headers-$$"
LEGACY_CACHE_CREATED=false

cleanup() {
    local status=0 ids=''
    set +e
    if [[ "$VIEWER_UID" =~ ^[1-9][0-9]*$ && -n "$TOKEN" ]]; then
        sql "UPDATE starfree_users SET authCode='$TOKEN' WHERE uid=$VIEWER_UID" >/dev/null 2>&1
        curl -fsS -G "$REPLACEMENT_URL/SFreeUsers/signOut" --data-urlencode "token=$TOKEN" >/dev/null 2>&1 || status=1
    fi
    for uid in "$OWNER_UID" "$VIEWER_UID"; do [[ "$uid" =~ ^[1-9][0-9]*$ ]] && ids="${ids:+$ids,}$uid"; done
    if [[ -n "$ids" ]]; then
        sql "DELETE FROM starfree_userlog WHERE uid IN ($ids) OR toid IN ($ids)" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_fan WHERE uid IN ($ids) OR touid IN ($ids)" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_space WHERE uid IN ($ids)" >/dev/null 2>&1 || status=1
        sql "DELETE FROM starfree_users WHERE uid IN ($ids)" >/dev/null 2>&1 || status=1
    fi
    sql "DELETE FROM starfree_users WHERE name IN ('$OWNER_NAME','$VIEWER_NAME')" >/dev/null 2>&1 || status=1
    rm -f "$HEADER_FILE" >/dev/null 2>&1 || status=1
    if [[ "$LEGACY_CACHE_CREATED" == true ]]; then
        # Legacy RedisHelp.setList uses a five-second TTL. Waiting one extra
        # second guarantees its Java-serialized list cannot outlive SQL cleanup.
        sleep 6
    fi
    [[ $status -eq 0 ]] || echo "WARNING: inspect followed-Space cleanup for $suffix" >&2
    return $status
}
trap cleanup EXIT

now="$(date +%s)"
sql "INSERT INTO starfree_users (name,password,mail,created,activated,logged,\`group\`,authCode,bantime,vip) VALUES
    ('$OWNER_NAME','$TEST_HASH','$OWNER_NAME@invalid.local',$now,$now,0,'contributor',NULL,0,0),
    ('$VIEWER_NAME','$TEST_HASH','$VIEWER_NAME@invalid.local',$now,$now,0,'contributor',NULL,0,0)"
OWNER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$OWNER_NAME' LIMIT 1")"
VIEWER_UID="$(sql "SELECT uid FROM starfree_users WHERE name='$VIEWER_NAME' LIMIT 1")"
[[ "$OWNER_UID" =~ ^[1-9][0-9]*$ && "$VIEWER_UID" =~ ^[1-9][0-9]*$ ]] || { echo "User id lookup failed" >&2; exit 12; }
echo "disposable_uids=$OWNER_UID,$VIEWER_UID"

params="$(printf '{\"name\":\"%s\",\"password\":\"%s\"}' "$VIEWER_NAME" "$TEST_PASSWORD")"
login="$(curl -fsS -X POST "$REPLACEMENT_URL/SFreeUsers/userLogin" --data-urlencode "params=$params")"
assert_code replacement_login 1 "$login"
TOKEN="$(sql "SELECT authCode FROM starfree_users WHERE uid=$VIEWER_UID")"
[[ "$TOKEN" =~ ^${VIEWER_NAME}[0-9a-f]{32}$ ]] || { echo "Unexpected token format" >&2; exit 13; }
sql "UPDATE starfree_users SET authCode=NULL WHERE uid=$VIEWER_UID"
[[ "$(sql "SELECT COUNT(*) FROM starfree_users WHERE authCode='$TOKEN'")" == 0 ]] || exit 14
echo "mysql_token_rows=0"

sql "INSERT INTO starfree_fan (created,uid,touid) VALUES ($now,$VIEWER_UID,$OWNER_UID)"
sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
    ($OWNER_UID,$now,$now,'codex follow public',NULL,0,0,0,1,0)"
PUBLIC_ID="$(sql "SELECT id FROM starfree_space WHERE uid=$OWNER_UID AND text='codex follow public' LIMIT 1")"
sql "INSERT INTO starfree_space (uid,created,modified,text,pic,type,likes,toid,status,onlyMe) VALUES
    ($OWNER_UID,$((now+1)),$((now+1)),'codex follow private',NULL,0,0,0,1,1),
    ($OWNER_UID,$((now+2)),$((now+2)),'codex follow reply',NULL,3,0,$PUBLIC_ID,1,0),
    ($OWNER_UID,$((now+3)),$((now+3)),'codex follow pending',NULL,0,0,0,0,0)"
PRIVATE_ID="$(sql "SELECT id FROM starfree_space WHERE uid=$OWNER_UID AND text='codex follow private' LIMIT 1")"
REPLY_ID="$(sql "SELECT id FROM starfree_space WHERE uid=$OWNER_UID AND text='codex follow reply' LIMIT 1")"
PENDING_ID="$(sql "SELECT id FROM starfree_space WHERE uid=$OWNER_UID AND text='codex follow pending' LIMIT 1")"
echo "disposable_space_ids=$PUBLIC_ID,$PRIVATE_ID,$REPLY_ID,$PENDING_ID"

replacement="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/followSpace" --data-urlencode "token=$TOKEN" --data-urlencode page=1 --data-urlencode limit=20)"
assert_code replacement_follow 1 "$replacement"; assert_ids replacement_follow "$replacement" "$PUBLIC_ID"
alias_response="$(curl -fsS -G "$REPLACEMENT_URL/SFreeSpace/myFollowSpace" --data-urlencode "token=$TOKEN" --data-urlencode page=1 --data-urlencode limit=20)"
assert_code replacement_alias 1 "$alias_response"; assert_ids replacement_alias "$alias_response" "$PUBLIC_ID"

legacy="$(curl -fsS -G "$LEGACY_URL/SFreeSpace/followSpace" --data-urlencode "token=$TOKEN" --data-urlencode page=1 --data-urlencode limit=20)"
LEGACY_CACHE_CREATED=true
assert_code legacy_follow 1 "$legacy"; assert_ids legacy_follow "$legacy" "$PUBLIC_ID,$PRIVATE_ID,$REPLY_ID"
echo "legacy_private_reply_leak=CONFIRMED"

if [[ "$VERIFY_PUBLIC" == 1 ]]; then
    for route in followSpace myFollowSpace; do
        response="$(curl -fsS -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeSpace/$route" --data-urlencode "token=$TOKEN" --data-urlencode page=1 --data-urlencode limit=20)"
        assert_code "public_$route" 1 "$response"
        grep -qi "^X-Starfree-Backend: $EXPECTED_PUBLIC_BACKEND" "$HEADER_FILE" || { echo "$route backend header mismatch" >&2; exit 15; }
        echo "public_${route}_backend=$EXPECTED_PUBLIC_BACKEND"
        assert_ids "public_$route" "$response" "$PUBLIC_ID"
    done
fi

cleanup; trap - EXIT
[[ "$(sql "SELECT COUNT(*) FROM starfree_users WHERE name IN ('$OWNER_NAME','$VIEWER_NAME')")" == 0 ]] || exit 16
[[ "$(sql "SELECT COUNT(*) FROM starfree_space WHERE uid=$OWNER_UID")" == 0 ]] || exit 16
echo "disposable_cleanup=PASS"
echo "space_follow_smoke=PASS"
