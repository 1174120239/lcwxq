#!/usr/bin/env bash
set -euo pipefail

# Move authenticated Space read traffic to the replacement backend after the
# Redis-only session smoke test has passed. Space write routes and every other
# legacy API route remain untouched by this script.

CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
BACKUP="$CONF.rollback-$(date +%Y%m%d-%H%M%S)"

if [[ ! -f "$CONF" ]]; then
    echo "Nginx include not found: $CONF" >&2
    exit 2
fi

cp -p "$CONF" "$BACKUP"

if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN=python
else
    echo "Python is required to rewrite the Nginx include safely" >&2
    exit 2
fi

"$PYTHON_BIN" - <<'PY'
from __future__ import print_function

conf_path = '/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf'
with open(conf_path, 'r') as handle:
    text = handle.read()
routes = ['/SFreeSpace/spaceList', '/SFreeSpace/spaceInfo']

for route in routes:
    marker = 'location = %s {' % route
    try:
        start = text.index(marker)
        end = text.index('\n}\n', start) + 3
    except ValueError:
        raise SystemExit('Could not locate complete Nginx block for %s' % route)

    block = text[start:end]
    old = (
        '    if ($arg_token != "") {\n'
        '        set $starfree_public_read http://127.0.0.1:8081;\n'
        '        set $starfree_backend legacy-token;\n'
        '    }\n'
    )
    if old not in block:
        raise SystemExit('Expected token fallback not found in %s' % route)
    block = block.replace(old, '')
    text = text[:start] + block + text[end:]

with open(conf_path, 'w') as handle:
    handle.write(text)
PY

if ! nginx -t; then
    echo "Nginx test failed; restoring $BACKUP" >&2
    cp -p "$BACKUP" "$CONF"
    nginx -t
    exit 20
fi

nginx -s reload

sha256sum "$CONF"
printf 'rollback=%s\n' "$BACKUP"

for url in \
    'https://api.lcxqy.cn/SFreeSpace/spaceList?searchParams=%7B%7D&limit=1&page=1' \
    'https://api.lcxqy.cn/SFreeSpace/spaceList?searchParams=%7B%7D&limit=1&page=1&token=codex_invalid_token' \
    'https://api.lcxqy.cn/SFreeSpace/spaceInfo?id=1' \
    'https://api.lcxqy.cn/SFreeSpace/spaceInfo?id=1&token=codex_invalid_token' \
    'https://api.lcxqy.cn/SFreeContents/contentsList?searchParams=%7B%7D&limit=1&page=1&token=codex_invalid_token'; do
    printf '%s -> ' "$url"
    curl -skI "$url" | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {print $0}'
done
