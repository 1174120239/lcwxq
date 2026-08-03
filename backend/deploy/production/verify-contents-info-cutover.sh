#!/usr/bin/env bash
set -euo pipefail

# Post-cutover audit. CLEAN_CCI_REDIS=1 removes only Java-serialized Redis keys
# carrying this verifier's reserved cci_/cci- marker; normal runs are read-only.
PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
DB_NAME=${DB_NAME:-lcxqy}
CLEAN_CCI_REDIS=${CLEAN_CCI_REDIS:-0}

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
export MYSQL_PWD="$DB_PASSWORD" REDIS_PREFIX REDIS_HOST REDIS_PORT CLEAN_CCI_REDIS
if [[ -n "$REDIS_PASSWORD" ]]; then export REDISCLI_AUTH="$REDIS_PASSWORD"; else unset REDISCLI_AUTH || true; fi
sql() { mysql --protocol=TCP --host=127.0.0.1 --user="$DB_USERNAME" --batch --skip-column-names "$DB_NAME" --execute="$1"; }

[[ "$(systemctl is-active starfree-replacement.service)" == active ]] || { echo "Replacement service is not active" >&2; exit 10; }
[[ "$(grep -c '^location = /SFreeContents/contentsInfo {' "$CONF")" == 1 ]] || { echo "contentsInfo exact route count is not one" >&2; exit 11; }

SQL_RESIDUE="$(sql "SELECT (SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='cci_') + (SELECT COUNT(*) FROM starfree_contents WHERE LEFT(slug,9)='cci-post-') + (SELECT COUNT(*) FROM starfree_metas WHERE LEFT(slug,4)='cci-')")"
echo "sql_cci_residue=$SQL_RESIDUE"
[[ "$SQL_RESIDUE" == 0 ]] || exit 12

"$PYTHON_BIN" - <<'PY'
from __future__ import print_function
import os, struct, subprocess, sys

host=os.environ['REDIS_HOST']; port=os.environ['REDIS_PORT']; prefix=os.environ['REDIS_PREFIX']
clean=os.environ.get('CLEAN_CCI_REDIS') == '1'
command=['redis-cli','-h',host,'-p',port,'--raw','KEYS','*']
raw_keys=subprocess.check_output(command)
matches=[]
for raw in raw_keys.splitlines():
    if len(raw) < 7 or raw[:5] != b'\xac\xed\x00\x05\x74':
        continue
    size=struct.unpack('>H', raw[5:7])[0]
    if len(raw) < 7 + size:
        continue
    try:
        text=raw[7:7+size].decode('utf-8')
    except UnicodeDecodeError:
        continue
    if text.startswith(prefix + '_') and ('cci_' in text or 'cci-' in text):
        matches.append((raw, text))

for _, text in matches:
    print('redis_cci_key=' + text)
if clean:
    for raw, _ in matches:
        process=subprocess.Popen(['redis-cli','-h',host,'-p',port,'--raw','-x','DEL'], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
        process.communicate(raw)
        if process.returncode != 0:
            raise SystemExit(process.returncode)
    print('redis_cci_residue_removed=' + str(len(matches)))
else:
    print('redis_cci_residue=' + str(len(matches)))
    if matches:
        raise SystemExit(13)
PY

HEADER_FILE="$(mktemp /tmp/starfree-contents-info-audit.XXXXXX)"
trap 'rm -f "$HEADER_FILE"' EXIT
PUBLIC_RESPONSE="$(curl -fsS -D "$HEADER_FILE" -G "$PUBLIC_URL/SFreeContents/contentsInfo" --data-urlencode key=1 --data-urlencode isMd=0)"
grep -qi '^X-Starfree-Backend: replacement-content-info' "$HEADER_FILE" || { echo "Public backend header mismatch" >&2; exit 14; }
printf '%s' "$PUBLIC_RESPONSE" | "$PYTHON_BIN" -c 'import json,sys; value=json.load(sys.stdin); raise SystemExit(0 if value.get("cid")==1 and "code" not in value else 1)'
echo "public_contents_info_backend=replacement-content-info"
echo "contents_info_cutover_audit=PASS"
