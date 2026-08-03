#!/usr/bin/env bash
set -euo pipefail

# Read-only post-cutover audit. Disposable verification must leave no cca_
# records; route probes use invalid tokens and cannot create content.
PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
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
DB_USERNAME=${DB_USERNAME:-$(read_property spring.datasource.username)}
DB_PASSWORD=${DB_PASSWORD:-$(read_property spring.datasource.password)}
REDIS_HOST=${REDIS_HOST:-$(read_property spring.redis.host)}
REDIS_PORT=${REDIS_PORT:-$(read_property spring.redis.port)}
REDIS_PASSWORD=${REDIS_PASSWORD-$(read_property spring.redis.password)}
REDIS_HOST=${REDIS_HOST:-127.0.0.1}
REDIS_PORT=${REDIS_PORT:-6379}
export MYSQL_PWD="$DB_PASSWORD"
if [[ -n "$REDIS_PASSWORD" ]]; then export REDISCLI_AUTH="$REDIS_PASSWORD"; else unset REDISCLI_AUTH || true; fi
sql() { mysql --protocol=TCP --host=127.0.0.1 --user="$DB_USERNAME" --batch --skip-column-names "$DB_NAME" --execute="$1"; }

[[ "$(systemctl is-active starfree-replacement.service)" == active ]] || { echo "Replacement service is not active" >&2; exit 10; }
[[ "$(grep -c '^location = /SFreeContents/contentsAdd {' "$CONF")" == 1 ]] || { echo "contentsAdd exact route count is not one" >&2; exit 11; }
SQL_RESIDUE="$(sql "SELECT (SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='cca_') + (SELECT COUNT(*) FROM starfree_contents WHERE LEFT(title,4)='cca_') + (SELECT COUNT(*) FROM starfree_metas WHERE LEFT(slug,4)='cca-')")"
echo "sql_cca_residue=$SQL_RESIDUE"
[[ "$SQL_RESIDUE" == 0 ]] || exit 12

if command -v python3 >/dev/null 2>&1; then PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1; then PYTHON_BIN=python
else echo "Python is required" >&2; exit 2; fi
REDIS_CCA_RESIDUE="$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --raw KEYS '*' | "$PYTHON_BIN" -c '
import struct,sys
count=0
source=getattr(sys.stdin,"buffer",sys.stdin)
for raw in source.read().splitlines():
    if len(raw)<7 or raw[:5]!=b"\xac\xed\x00\x05\x74": continue
    size=struct.unpack(">H",raw[5:7])[0]
    if len(raw)<7+size: continue
    try: value=raw[7:7+size].decode("utf-8")
    except UnicodeDecodeError: continue
    if "cca_" in value: count+=1
print(count)
')"
echo "redis_cca_residue=$REDIS_CCA_RESIDUE"
[[ "$REDIS_CCA_RESIDUE" == 0 ]] || exit 12

HEADER_FILE="/tmp/starfree-contents-add-audit-$$"
trap 'rm -f "$HEADER_FILE"' EXIT
ordinary="$(curl -fsS --resolve api.lcxqy.cn:443:127.0.0.1 -D "$HEADER_FILE" -X POST \
    "$PUBLIC_URL/SFreeContents/contentsAdd" --data-urlencode token=cca_invalid_token \
    --data-urlencode 'params={"title":"audit","sid":-1,"type":"post"}' --data-urlencode text=audit)"
grep -qi '^X-Starfree-Backend: replacement-content-add' "$HEADER_FILE" || exit 13
if grep -qi '^X-Starfree-Delegate:' "$HEADER_FILE"; then echo "Ordinary probe was delegated" >&2; exit 13; fi

delegated="$(curl -fsS --resolve api.lcxqy.cn:443:127.0.0.1 -D "$HEADER_FILE" -X POST \
    "$PUBLIC_URL/SFreeContents/contentsAdd" --data-urlencode token=cca_invalid_token \
    --data-urlencode 'params={"title":"audit","sid":-1,"type":"post"}' --data-urlencode text=audit \
    --data-urlencode isDraft=1)"
grep -qi '^X-Starfree-Backend: replacement-content-add' "$HEADER_FILE" || exit 14
grep -qi '^X-Starfree-Delegate: legacy-contents-add' "$HEADER_FILE" || exit 14
printf '%s' "$ordinary$delegated" | grep -q '"code"[[:space:]]*:[[:space:]]*0' || exit 15
nginx -t
echo "contents_add_cutover_audit=PASS"
