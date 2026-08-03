#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <expected-jar-sha256>" >&2
    exit 2
fi

APP_DIR=/opt/starfree-replacement
LEGACY_PROPERTIES=/opt/application.properties
DATABASE=lcxqy
TABLE=starfree_space
MIGRATION="$APP_DIR/002_space_views.sql.new"
EXPECTED_SHA256="$1"

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

candidate_sha256="$(sha256sum "$APP_DIR/starfree-replacement.jar.new" | awk '{print $1}')"
echo "candidate_sha256=$candidate_sha256"
if [[ "$candidate_sha256" != "$EXPECTED_SHA256" ]]; then
    echo "Candidate JAR checksum mismatch" >&2
    exit 20
fi

db_username="$(read_property spring.datasource.username)"
db_password="$(read_property spring.datasource.password)"
if [[ -z "$db_username" || -z "$db_password" ]]; then
    echo "Database credentials could not be read" >&2
    exit 21
fi

mysql_query() {
    MYSQL_PWD="$db_password" mysql -N -u "$db_username" "$DATABASE" -e "$1"
}

space_engine="$(mysql_query "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DATABASE' AND TABLE_NAME='$TABLE'")"
views_before="$(mysql_query "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DATABASE' AND TABLE_NAME='$TABLE' AND COLUMN_NAME='views'")"
echo "space_engine=$space_engine"
echo "views_before=$views_before"

timestamp="$(date +%Y%m%d-%H%M%S)"
backup="$APP_DIR/$TABLE.before-views-$timestamp.sql.gz"
MYSQL_PWD="$db_password" mysqldump --quick --lock-tables -u "$db_username" "$DATABASE" "$TABLE" | gzip -c > "$backup"
if [[ ! -s "$backup" ]]; then
    echo "Database backup is empty" >&2
    exit 22
fi
echo "db_backup=$backup"
echo "db_backup_bytes=$(stat -c %s "$backup")"
echo "db_backup_sha256=$(sha256sum "$backup" | awk '{print $1}')"

if [[ "$views_before" == "0" ]]; then
    MYSQL_PWD="$db_password" mysql -u "$db_username" "$DATABASE" < "$MIGRATION"
    echo "migration=applied"
else
    echo "migration=already_present"
fi

install -m 0644 "$MIGRATION" "$APP_DIR/002_space_views.sql"
views_after="$(mysql_query "SELECT CONCAT(COLUMN_TYPE, '|', IS_NULLABLE, '|', IFNULL(COLUMN_DEFAULT, 'NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DATABASE' AND TABLE_NAME='$TABLE' AND COLUMN_NAME='views'")"
echo "views_after=$views_after"
if [[ "$views_after" != "int(10)|YES|0" ]]; then
    echo "Unexpected views column definition" >&2
    exit 23
fi

bash "$APP_DIR/deploy-jar.sh" "$EXPECTED_SHA256"
