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
# The replacement backend owns the hardened session lifetime. Keep active users signed in for
# 90 days of inactivity even when the closed API still carries its historical shorter value.
export LEGACY_REDIS_SESSION_TTL="${LEGACY_REDIS_SESSION_TTL:-7776000}"

# Reuse the closed API's SMTP runtime configuration without copying secrets into
# the repository. Verification and dynamic-comment notices share the SMTP account,
# while their failure handling remains independent.
SPRING_MAIL_HOST_VALUE="${SPRING_MAIL_HOST:-$(read_property spring.mail.host)}"
SPRING_MAIL_PORT_VALUE="${SPRING_MAIL_PORT:-$(read_property spring.mail.port)}"
export SPRING_MAIL_HOST="${SPRING_MAIL_HOST_VALUE:-smtp.qq.com}"
export SPRING_MAIL_PORT="${SPRING_MAIL_PORT_VALUE:-465}"
export SPRING_MAIL_USERNAME="${SPRING_MAIL_USERNAME:-$(read_property spring.mail.username)}"
export SPRING_MAIL_PASSWORD="${SPRING_MAIL_PASSWORD:-$(read_property spring.mail.password)}"
export SPRING_MAIL_FROM="${SPRING_MAIL_FROM:-$SPRING_MAIL_USERNAME}"
export VERIFICATION_EMAIL_ENABLED="${VERIFICATION_EMAIL_ENABLED:-true}"
export VERIFICATION_EMAIL_MAX_CONCURRENT="${VERIFICATION_EMAIL_MAX_CONCURRENT:-2}"
export NOTIFICATION_EMAIL_ENABLED="${NOTIFICATION_EMAIL_ENABLED:-true}"

# UniPush is opt-in. When enabled on the server, the operator adds these keys to
# /opt/application.properties (unipush.enabled=true, unipush.app-id, unipush.app-key,
# unipush.app-secret) or exports the matching environment variables. Empty values
# keep the service running with push disabled; credentials never live in Git.
UNIPUSH_ENABLED_VALUE="${UNIPUSH_ENABLED:-$(read_property unipush.enabled)}"
if [[ -n "$UNIPUSH_ENABLED_VALUE" ]]; then
    export UNIPUSH_ENABLED="$UNIPUSH_ENABLED_VALUE"
fi
export UNIPUSH_APP_ID="${UNIPUSH_APP_ID:-$(read_property unipush.app-id)}"
export UNIPUSH_APP_KEY="${UNIPUSH_APP_KEY:-$(read_property unipush.app-key)}"
export UNIPUSH_APP_SECRET="${UNIPUSH_APP_SECRET:-$(read_property unipush.app-secret)}"


if [[ -z "$DB_USERNAME" || -z "$DB_PASSWORD" ]]; then
    echo "Database credentials could not be read from $LEGACY_PROPERTIES" >&2
    exit 1
fi

exec "$JAVA_BIN" \
    -Dfile.encoding=UTF-8 \
    -jar "$APP_DIR/starfree-replacement.jar" \
    --server.address=127.0.0.1 \
    --spring.profiles.active=production
