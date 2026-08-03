#!/usr/bin/env bash
set -euo pipefail

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
MIGRATION_FILE=${MIGRATION_FILE:-/opt/starfree-replacement/001_economy_operation_journal.sql}
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

for required in awk mysql sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -r "$MIGRATION_FILE" ]] || { echo "Missing $MIGRATION_FILE" >&2; exit 2; }

if [[ -z "${DB_USERNAME:-}" || -z "${DB_PASSWORD:-}" ]]; then
    [[ -r "$PROPERTIES_FILE" ]] || { echo "Missing $PROPERTIES_FILE" >&2; exit 2; }
fi
DB_USERNAME=${DB_USERNAME:-$(read_property spring.datasource.username)}
DB_PASSWORD=${DB_PASSWORD:-$(read_property spring.datasource.password)}
[[ -n "$DB_USERNAME" && -n "$DB_PASSWORD" ]] || {
    echo "Database credentials are missing" >&2
    exit 2
}
export MYSQL_PWD="$DB_PASSWORD"

mysql_cmd=(mysql --protocol=TCP --host="$DB_HOST" --port="$DB_PORT"
    --user="$DB_USERNAME" "$DB_NAME")

echo "migration_sha256=$(sha256sum "$MIGRATION_FILE" | awk '{print $1}')"
"${mysql_cmd[@]}" <"$MIGRATION_FILE"

verification="$("${mysql_cmd[@]}" --batch --skip-column-names --execute="
    SELECT CONCAT(TABLE_NAME,':',ENGINE)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA='$DB_NAME'
      AND TABLE_NAME='starfree_economy_operations';
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA='$DB_NAME'
      AND TABLE_NAME='starfree_economy_operations'
      AND INDEX_NAME='operation_key'
      AND NON_UNIQUE=0;")"
printf '%s\n' "$verification"
grep -q '^starfree_economy_operations:InnoDB$' <<<"$verification" || {
    echo "Economy journal table is missing or not InnoDB" >&2
    exit 20
}
[[ "$(tail -n 1 <<<"$verification")" == 1 ]] || {
    echo "Economy journal unique operation key is missing" >&2
    exit 21
}

echo "economy_migration=ready"
