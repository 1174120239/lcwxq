#!/usr/bin/env bash
set -euo pipefail

APP_DIR=/opt/starfree-replacement
LEGACY_PROPERTIES=/opt/application.properties
JAVA_BIN=/opt/jdk1.8.0_311/bin/java

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
    ' "$LEGACY_PROPERTIES"
}

if [[ ! -x "$JAVA_BIN" ]]; then
    echo "Java runtime not found: $JAVA_BIN" >&2
    exit 1
fi
if [[ ! -r "$LEGACY_PROPERTIES" ]]; then
    echo "Legacy configuration not found: $LEGACY_PROPERTIES" >&2
    exit 1
fi

export APP_PORT="${APP_PORT:-18082}"
export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-3306}"
export DB_NAME="${DB_NAME:-lcxqy}"
export DB_USERNAME="${DB_USERNAME:-$(read_property spring.datasource.username)}"
export DB_PASSWORD="${DB_PASSWORD:-$(read_property spring.datasource.password)}"
export LEGACY_API_BASE_URL="${LEGACY_API_BASE_URL:-http://127.0.0.1:8081}"
export REDIS_HOST="${REDIS_HOST:-$(read_property spring.redis.host)}"
export REDIS_PORT="${REDIS_PORT:-$(read_property spring.redis.port)}"
export REDIS_PASSWORD="${REDIS_PASSWORD-$(read_property spring.redis.password)}"
export LEGACY_REDIS_ENABLED="${LEGACY_REDIS_ENABLED:-true}"
export LEGACY_REDIS_PREFIX="${LEGACY_REDIS_PREFIX:-$(read_property web.prefix)}"
export LEGACY_REDIS_SESSION_TTL="${LEGACY_REDIS_SESSION_TTL:-$(read_property webinfo.usertime)}"


if [[ -z "$DB_USERNAME" || -z "$DB_PASSWORD" ]]; then
    echo "Database credentials could not be read from $LEGACY_PROPERTIES" >&2
    exit 1
fi

exec "$JAVA_BIN" \
    -Dfile.encoding=UTF-8 \
    -jar "$APP_DIR/starfree-replacement.jar" \
    --server.address=127.0.0.1 \
    --spring.profiles.active=production
