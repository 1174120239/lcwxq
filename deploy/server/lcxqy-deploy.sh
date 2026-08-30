#!/usr/bin/env bash
set -euo pipefail

# The only production entrypoint used by the local script and GitHub Actions.
# It accepts a checksum-verified archive, stores a release record, creates a
# component backup, and restores the previous artifact after a failed health check.

REMOTE_ROOT=/srv/lcxqy
ARCHIVE=
EXPECTED_SHA256=
COMPONENT=
VERIFY_ONLY=false
RUN_MIGRATIONS=false
MIGRATION=
MIGRATION_014_SHA256=6903ceeb1ba12eca0b87e6cd36bafa6bf884a0e82ed1f95127808e1091d36271
MIGRATION_015_SHA256=9334f123e2470f64a20672afed73af1cd1226fcf60effaf827ffe935a0bf21a8
MIGRATION_016_SHA256=__MIGRATION_016_SHA256__

usage() {
    echo "Usage: $0 --archive FILE --expected-sha256 HASH [--remote-root DIR] [--run-migrations --migration 014|015|016]"
    echo "       $0 --verify --component COMPONENT"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --archive) ARCHIVE=${2:-}; shift 2 ;;
        --expected-sha256) EXPECTED_SHA256=${2:-}; shift 2 ;;
        --remote-root) REMOTE_ROOT=${2:-}; shift 2 ;;
        --component) COMPONENT=${2:-}; shift 2 ;;
        --verify) VERIFY_ONLY=true; shift ;;
        --run-migrations) RUN_MIGRATIONS=true; shift ;;
        --migration) MIGRATION=${2:-}; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

[[ "$(id -u)" -eq 0 ]] || { echo 'Run as root or through a narrowly scoped sudo rule.' >&2; exit 1; }
[[ "$REMOTE_ROOT" == /srv/lcxqy ]] || { echo 'Remote root must be /srv/lcxqy.' >&2; exit 2; }

health_check() {
    case "$1" in
        replacement-backend) curl -fsS --max-time 15 http://127.0.0.1:18082/health >/dev/null ;;
        legacy-api) curl -fsS --max-time 15 http://127.0.0.1:8081/ >/dev/null ;;
        admin) curl -fsS --max-time 20 "${ADMIN_HEALTH_URL:-https://admin.lcxqy.cn/}" >/dev/null ;;
        *) echo "Unknown component: $1" >&2; return 2 ;;
    esac
}

service_check() {
    case "$1" in
        replacement-backend) systemctl is-active --quiet starfree-replacement.service ;;
        legacy-api) systemctl is-active --quiet starfree-legacy.service ;;
        admin) return 0 ;;
        *) return 2 ;;
    esac
}

validate_jar() {
    local artifact="$1"
    if [[ -x /opt/jdk1.8.0_311/bin/jar ]]; then
        /opt/jdk1.8.0_311/bin/jar tf "$artifact" >/dev/null
    elif command -v jar >/dev/null 2>&1; then
        jar tf "$artifact" >/dev/null
    elif command -v unzip >/dev/null 2>&1; then
        unzip -t "$artifact" >/dev/null
    else
        echo 'No JAR validator is available on the server.' >&2
        return 2
    fi
}

read_property() {
    local key="$1"
    sed -n "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*//p" /opt/application.properties \
        | tail -n 1 | tr -d '\r'
}

configure_mysql_014() {
    local db_url db_target db_hostport db_user db_password escaped_user escaped_password
    [[ -r /opt/application.properties ]] || {
        echo 'Production database configuration is not readable.' >&2
        return 2
    }
    db_url=$(read_property 'spring\.datasource\.url')
    db_user=$(read_property 'spring\.datasource\.username')
    db_password=$(read_property 'spring\.datasource\.password')
    [[ "$db_url" == jdbc:mysql://* ]] || { echo 'Unsupported production JDBC URL.' >&2; return 2; }
    [[ -n "$db_user" ]] || { echo 'Production database username is empty.' >&2; return 2; }

    db_target=${db_url#jdbc:mysql://}
    db_target=${db_target%%\?*}
    db_hostport=${db_target%%/*}
    DB_NAME_014=${db_target#*/}
    [[ "$DB_NAME_014" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'Unsafe production database name.' >&2; return 2; }
    if [[ "$db_hostport" == *:* ]]; then
        DB_HOST_014=${db_hostport%%:*}
        DB_PORT_014=${db_hostport##*:}
    else
        DB_HOST_014=$db_hostport
        DB_PORT_014=3306
    fi
    [[ -n "$DB_HOST_014" && "$DB_PORT_014" =~ ^[0-9]+$ ]] || {
        echo 'Invalid production database address.' >&2
        return 2
    }

    MYSQL_CNF_014="$incoming/mysql-014.cnf"
    escaped_user=${db_user//\\/\\\\}
    escaped_user=${escaped_user//\"/\\\"}
    escaped_password=${db_password//\\/\\\\}
    escaped_password=${escaped_password//\"/\\\"}
    umask 077
    printf '[client]\nuser="%s"\npassword="%s"\n' "$escaped_user" "$escaped_password" > "$MYSQL_CNF_014"
    MYSQL_BASE_ARGS_014=(
        "--defaults-extra-file=$MYSQL_CNF_014"
        --protocol=tcp
        --host="$DB_HOST_014"
        --port="$DB_PORT_014"
    )
    mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" \
        --batch --skip-column-names -e 'SELECT 1' >/dev/null
}

migration_014_valid() {
    local table_count engine_count config_count unique_count unique_columns public_columns receiver_columns
    table_count=$(mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -Nse \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('starfree_lost_found_items','starfree_lost_found_actions','starfree_lost_found_comments','starfree_lost_found_contact_grants','starfree_lost_found_config')")
    [[ "$table_count" == 5 ]] || return 1
    engine_count=$(mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -Nse \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB' AND table_name IN ('starfree_lost_found_items','starfree_lost_found_actions','starfree_lost_found_comments','starfree_lost_found_contact_grants','starfree_lost_found_config')")
    [[ "$engine_count" == 5 ]] || return 1
    config_count=$(mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -Nse \
        'SELECT COUNT(*) FROM starfree_lost_found_config WHERE id=1')
    [[ "$config_count" == 1 ]] || return 1
    unique_count=$(mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -Nse \
        "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='starfree_lost_found_contact_grants' AND index_name='uk_lost_found_contact_grant' AND non_unique=0")
    [[ "$unique_count" == 4 ]] || return 1
    unique_columns=$(mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -Nse \
        "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='starfree_lost_found_contact_grants' AND index_name='uk_lost_found_contact_grant' AND non_unique=0")
    [[ "$unique_columns" == item_id,comment_id,sender_uid,receiver_uid ]] || return 1
    public_columns=$(mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -Nse \
        "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='starfree_lost_found_items' AND index_name='idx_lost_found_public'")
    [[ "$public_columns" == status,kind,category,modified ]] || return 1
    receiver_columns=$(mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -Nse \
        "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='starfree_lost_found_contact_grants' AND index_name='idx_lost_found_contact_receiver'")
    [[ "$receiver_columns" == receiver_uid,item_id,created ]]
}

backup_migration_014() {
    MIGRATION_014_EXISTING=()
    mapfile -t MIGRATION_014_EXISTING < <(mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -Nse \
        "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('starfree_lost_found_items','starfree_lost_found_actions','starfree_lost_found_comments','starfree_lost_found_contact_grants','starfree_lost_found_config') ORDER BY table_name")
    if (( ${#MIGRATION_014_EXISTING[@]} > 0 )); then
        printf '%s\n' "${MIGRATION_014_EXISTING[@]}" \
            > "$backup_dir/migration-014-preexisting-tables.txt"
        mysqldump "${MYSQL_BASE_ARGS_014[@]}" --single-transaction --skip-lock-tables \
            "$DB_NAME_014" "${MIGRATION_014_EXISTING[@]}" \
            > "$backup_dir/migration-014-existing-tables.sql"
        [[ -s "$backup_dir/migration-014-existing-tables.sql" ]] || {
            echo 'Migration 014 database backup is empty.' >&2
            return 2
        }
        sha256sum "$backup_dir/migration-014-existing-tables.sql" \
            > "$backup_dir/migration-014-existing-tables.sql.sha256"
    else
        : > "$backup_dir/migration-014-preexisting-tables.txt"
        touch "$backup_dir/migration-014.no-existing-tables"
    fi
}

rollback_migration_014() {
    echo 'Migration 014 failed validation; restoring its exact pre-migration table set.' >&2
    mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -e \
        'DROP TABLE IF EXISTS starfree_lost_found_contact_grants,starfree_lost_found_comments,starfree_lost_found_actions,starfree_lost_found_items,starfree_lost_found_config'
    if [[ -s "$backup_dir/migration-014-existing-tables.sql" ]]; then
        mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" \
            < "$backup_dir/migration-014-existing-tables.sql"
    fi
    echo 'migration_014_rollback=success' >&2
}

apply_migration_014() {
    local migration_file="$incoming/014_lost_and_found.sql" migration_hash existing_count
    for required in mysql mysqldump sha256sum; do
        command -v "$required" >/dev/null 2>&1 || {
            echo "Required migration command not found: $required" >&2
            return 2
        }
    done
    [[ -r "$migration_file" ]] || { echo 'Migration 014 is missing from the release.' >&2; return 2; }
    migration_hash=$(sha256sum "$migration_file" | awk '{print $1}')
    [[ "$migration_hash" == "$MIGRATION_014_SHA256" ]] || {
        echo 'Migration 014 SHA-256 mismatch.' >&2
        return 2
    }
    configure_mysql_014 || return
    existing_count=$(mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" -Nse \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('starfree_lost_found_items','starfree_lost_found_actions','starfree_lost_found_comments','starfree_lost_found_contact_grants','starfree_lost_found_config')")
    [[ "$existing_count" =~ ^[0-5]$ ]] || {
        echo "Unexpected campus mutual-aid table count: $existing_count" >&2
        return 2
    }
    backup_migration_014 || return
    if [[ "$existing_count" == 5 ]] && migration_014_valid; then
        echo 'migration_014=already_present'
        echo "migration_014_backup=$backup_dir"
        return 0
    fi
    if ! mysql "${MYSQL_BASE_ARGS_014[@]}" "$DB_NAME_014" < "$migration_file"; then
        rollback_migration_014
        return 20
    fi
    if ! migration_014_valid; then
        rollback_migration_014
        return 21
    fi
    echo 'migration_014=applied'
    echo "migration_014_backup=$backup_dir"
}

configure_mysql_015() {
    local db_url db_target db_hostport db_user db_password escaped_user escaped_password
    [[ -r /opt/application.properties ]] || { echo 'Production database configuration is not readable.' >&2; return 2; }
    db_url=$(read_property 'spring\.datasource\.url')
    db_user=$(read_property 'spring\.datasource\.username')
    db_password=$(read_property 'spring\.datasource\.password')
    [[ "$db_url" == jdbc:mysql://* ]] || { echo 'Unsupported production JDBC URL.' >&2; return 2; }
    [[ -n "$db_user" ]] || { echo 'Production database username is empty.' >&2; return 2; }
    db_target=${db_url#jdbc:mysql://}; db_target=${db_target%%\?*}; db_hostport=${db_target%%/*}
    DB_NAME_015=${db_target#*/}; [[ "$DB_NAME_015" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'Unsafe production database name.' >&2; return 2; }
    if [[ "$db_hostport" == *:* ]]; then DB_HOST_015=${db_hostport%%:*}; DB_PORT_015=${db_hostport##*:}; else DB_HOST_015=$db_hostport; DB_PORT_015=3306; fi
    [[ -n "$DB_HOST_015" && "$DB_PORT_015" =~ ^[0-9]+$ ]] || { echo 'Invalid production database address.' >&2; return 2; }
    MYSQL_CNF_015="$incoming/mysql-015.cnf"
    escaped_user=${db_user//\\/\\\\}
    escaped_user=${escaped_user//"/\\"}
    escaped_password=${db_password//\\/\\\\}
    escaped_password=${escaped_password//"/\\"}
    umask 077; printf '[client]\nuser="%s"\npassword="%s"\n' "$escaped_user" "$escaped_password" > "$MYSQL_CNF_015"
    MYSQL_BASE_ARGS_015=("--defaults-extra-file=$MYSQL_CNF_015" --protocol=tcp --host="$DB_HOST_015" --port="$DB_PORT_015")
    mysql "${MYSQL_BASE_ARGS_015[@]}" "$DB_NAME_015" --batch --skip-column-names -e 'SELECT 1' >/dev/null
}

migration_015_valid() {
    local column_count
    column_count=$(mysql "${MYSQL_BASE_ARGS_015[@]}" "$DB_NAME_015" -Nse "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='starfree_qa_questions' AND column_name='image_urls') OR (table_name='starfree_lost_found_items' AND column_name='image_urls'))")
    [[ "$column_count" == 2 ]] || return 1
    mysql "${MYSQL_BASE_ARGS_015[@]}" "$DB_NAME_015" -Nse "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='image_urls' AND data_type='text'" | grep -qx '2'
}

backup_migration_015() {
    mysql "${MYSQL_BASE_ARGS_015[@]}" "$DB_NAME_015" -Nse "SELECT CONCAT(table_name,'.',column_name) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='image_urls' AND table_name IN ('starfree_qa_questions','starfree_lost_found_items') ORDER BY table_name" > "$backup_dir/migration-015-preexisting-columns.txt"
    mysqldump "${MYSQL_BASE_ARGS_015[@]}" --single-transaction --skip-lock-tables "$DB_NAME_015" starfree_qa_questions starfree_lost_found_items > "$backup_dir/migration-015-affected-tables.sql"
    [[ -s "$backup_dir/migration-015-affected-tables.sql" ]] || { echo 'Migration 015 database backup is empty.' >&2; return 2; }
    sha256sum "$backup_dir/migration-015-affected-tables.sql" > "$backup_dir/migration-015-affected-tables.sql.sha256"
}

rollback_migration_015() {
    echo 'Migration 015 failed validation; removing only columns created by this migration.' >&2
    local table
    for table in starfree_qa_questions starfree_lost_found_items; do
        if ! grep -qx "$table.image_urls" "$backup_dir/migration-015-preexisting-columns.txt"; then
            mysql "${MYSQL_BASE_ARGS_015[@]}" "$DB_NAME_015" -e "ALTER TABLE \`$table\` DROP COLUMN \`image_urls\`" || true
        fi
    done
    echo 'migration_015_rollback=success' >&2
}

apply_migration_015() {
    local migration_file="$incoming/015_publish_rich_media.sql" migration_hash table_count
    for required in mysql mysqldump sha256sum; do command -v "$required" >/dev/null 2>&1 || { echo "Required migration command not found: $required" >&2; return 2; }; done
    [[ -r "$migration_file" ]] || { echo 'Migration 015 is missing from the release.' >&2; return 2; }
    migration_hash=$(sha256sum "$migration_file" | awk '{print $1}')
    [[ "$migration_hash" == "$MIGRATION_015_SHA256" ]] || { echo 'Migration 015 SHA-256 mismatch.' >&2; return 2; }
    configure_mysql_015 || return
    table_count=$(mysql "${MYSQL_BASE_ARGS_015[@]}" "$DB_NAME_015" -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('starfree_qa_questions','starfree_lost_found_items')")
    [[ "$table_count" == 2 ]] || { echo 'Migration 015 requires the existing Q&A and mutual-aid tables.' >&2; return 2; }
    backup_migration_015 || return
    if migration_015_valid; then echo 'migration_015=already_present'; echo "migration_015_backup=$backup_dir"; return 0; fi
    if ! mysql "${MYSQL_BASE_ARGS_015[@]}" "$DB_NAME_015" < "$migration_file"; then rollback_migration_015; return 20; fi
    if ! migration_015_valid; then rollback_migration_015; return 21; fi
    echo 'migration_015=applied'; echo "migration_015_backup=$backup_dir"
}

configure_mysql_016() {
    local db_url db_target db_hostport db_user db_password escaped_user escaped_password
    [[ -r /opt/application.properties ]] || { echo 'Production database configuration is not readable.' >&2; return 2; }
    db_url=$(read_property 'spring\.datasource\.url')
    db_user=$(read_property 'spring\.datasource\.username')
    db_password=$(read_property 'spring\.datasource\.password')
    [[ "$db_url" == jdbc:mysql://* ]] || { echo 'Unsupported production JDBC URL.' >&2; return 2; }
    [[ -n "$db_user" ]] || { echo 'Production database username is empty.' >&2; return 2; }
    db_target=${db_url#jdbc:mysql://}; db_target=${db_target%%\?*}; db_hostport=${db_target%%/*}
    DB_NAME_016=${db_target#*/}; [[ "$DB_NAME_016" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'Unsafe production database name.' >&2; return 2; }
    if [[ "$db_hostport" == *:* ]]; then DB_HOST_016=${db_hostport%%:*}; DB_PORT_016=${db_hostport##*:}; else DB_HOST_016=$db_hostport; DB_PORT_016=3306; fi
    [[ -n "$DB_HOST_016" && "$DB_PORT_016" =~ ^[0-9]+$ ]] || { echo 'Invalid production database address.' >&2; return 2; }
    MYSQL_CNF_016="$incoming/mysql-016.cnf"
    escaped_user=${db_user//\\/\\\\}; escaped_user=${escaped_user//\"/\\\"}
    escaped_password=${db_password//\\/\\\\}; escaped_password=${escaped_password//\"/\\\"}
    umask 077; printf '[client]\nuser="%s"\npassword="%s"\n' "$escaped_user" "$escaped_password" > "$MYSQL_CNF_016"
    MYSQL_BASE_ARGS_016=("--defaults-extra-file=$MYSQL_CNF_016" --protocol=tcp --host="$DB_HOST_016" --port="$DB_PORT_016")
    mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" --batch --skip-column-names -e 'SELECT 1' >/dev/null
}

migration_016_valid() {
    local table_count engine_count column_count config_count
    table_count=$(mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='lcxqy_download_site_config'")
    [[ "$table_count" == 1 ]] || return 1
    engine_count=$(mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='lcxqy_download_site_config' AND engine='InnoDB'")
    [[ "$engine_count" == 1 ]] || return 1
    column_count=$(mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" -Nse "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lcxqy_download_site_config' AND column_name IN ('hero_kicker','hero_title','hero_intro','web_url','cors_origins','updated_at')")
    [[ "$column_count" == 6 ]] || return 1
    config_count=$(mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" -Nse 'SELECT COUNT(*) FROM lcxqy_download_site_config WHERE id=1')
    [[ "$config_count" == 1 ]]
}

backup_migration_016() {
    local existing_count
    existing_count=$(mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='lcxqy_download_site_config'")
    if [[ "$existing_count" == 1 ]]; then
        mysqldump "${MYSQL_BASE_ARGS_016[@]}" --single-transaction --skip-lock-tables "$DB_NAME_016" lcxqy_download_site_config > "$backup_dir/migration-016-existing-table.sql"
        [[ -s "$backup_dir/migration-016-existing-table.sql" ]] || { echo 'Migration 016 database backup is empty.' >&2; return 2; }
        sha256sum "$backup_dir/migration-016-existing-table.sql" > "$backup_dir/migration-016-existing-table.sql.sha256"
    else
        touch "$backup_dir/migration-016.no-existing-table"
    fi
}

rollback_migration_016() {
    if [[ -f "$backup_dir/migration-016.no-existing-table" ]]; then
        mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" -e 'DROP TABLE IF EXISTS lcxqy_download_site_config'
    elif [[ -s "$backup_dir/migration-016-existing-table.sql" ]]; then
        mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" -e 'DROP TABLE IF EXISTS lcxqy_download_site_config'
        mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" < "$backup_dir/migration-016-existing-table.sql"
    fi
    echo 'migration_016_rollback=success' >&2
}

apply_migration_016() {
    local migration_file="$incoming/016_download_site_config.sql" migration_hash
    for required in mysql mysqldump sha256sum; do command -v "$required" >/dev/null 2>&1 || { echo "Required migration command not found: $required" >&2; return 2; }; done
    [[ -r "$migration_file" ]] || { echo 'Migration 016 is missing from the release.' >&2; return 2; }
    migration_hash=$(sha256sum "$migration_file" | awk '{print $1}')
    [[ "$migration_hash" == "$MIGRATION_016_SHA256" ]] || { echo 'Migration 016 SHA-256 mismatch.' >&2; return 2; }
    configure_mysql_016 || return
    backup_migration_016 || return
    if migration_016_valid; then echo 'migration_016=already_present'; echo "migration_016_backup=$backup_dir"; return 0; fi
    if ! mysql "${MYSQL_BASE_ARGS_016[@]}" "$DB_NAME_016" < "$migration_file"; then rollback_migration_016; return 20; fi
    if ! migration_016_valid; then rollback_migration_016; return 21; fi
    echo 'migration_016=applied'; echo "migration_016_backup=$backup_dir"
}

wait_for_component() {
    local component="$1"
    local timeout_seconds="${2:-180}"
    local deadline=$((SECONDS + timeout_seconds))
    while (( SECONDS < deadline )); do
        if service_check "$component" && health_check "$component"; then
            return 0
        fi
        sleep 2
    done
    service_check "$component" && health_check "$component"
}

verify_component() {
    local component="$1"
    service_check "$component"
    health_check "$component"
    echo "component=$component"
    case "$component" in
        replacement-backend) sha256sum /opt/starfree-replacement/starfree-replacement.jar ;;
        legacy-api) sha256sum /opt/StarFreeApi.jar ;;
        admin) echo 'admin_target=/www/wwwroot/admin.lcxqy.cn' ;;
    esac
    echo 'health=ok'
}

if [[ "$VERIFY_ONLY" == true ]]; then
    [[ "$COMPONENT" =~ ^(replacement-backend|legacy-api|admin)$ ]] || {
        echo '--component is required with --verify' >&2; exit 2;
    }
    verify_component "$COMPONENT"
    exit 0
fi

[[ -r "$ARCHIVE" ]] || { echo "Release archive is not readable: $ARCHIVE" >&2; exit 2; }
[[ "$EXPECTED_SHA256" =~ ^[0-9a-fA-F]{64}$ ]] || { echo 'Invalid expected SHA-256.' >&2; exit 2; }
actual_sha256=$(sha256sum "$ARCHIVE" | awk '{print $1}')
[[ "$actual_sha256" == "${EXPECTED_SHA256,,}" ]] || { echo 'Release archive checksum mismatch.' >&2; exit 20; }

mkdir -p "$REMOTE_ROOT/releases" "$REMOTE_ROOT/backups" "$REMOTE_ROOT/current"
incoming=$(mktemp -d "$REMOTE_ROOT/.incoming.XXXXXX")
cleanup() { rm -rf "$incoming"; }
trap cleanup EXIT
if tar -tzf "$ARCHIVE" | grep -E '(^/|(^|/)\.\.(/|$))' >/dev/null; then
    echo 'Unsafe path in release archive.' >&2
    exit 21
fi
tar --no-same-owner --no-same-permissions -xzf "$ARCHIVE" -C "$incoming"
if find "$incoming" -type l -print -quit | grep -q .; then
    echo 'Symbolic links are not allowed in a release archive.' >&2
    exit 21
fi
[[ -r "$incoming/manifest.env" ]] || { echo 'manifest.env is missing.' >&2; exit 21; }
if grep -Ev '^(COMPONENT=(replacement-backend|legacy-api|admin|all)|COMMIT=[0-9a-f]{40}|CREATED_UTC=[0-9T:.+Z-]+)$' "$incoming/manifest.env" | grep -q .; then
    echo 'manifest.env contains an unsupported line.' >&2
    exit 21
fi
grep -Eq '^COMPONENT=(replacement-backend|legacy-api|admin|all)$' "$incoming/manifest.env" || { echo 'Invalid manifest component.' >&2; exit 21; }
grep -Eq '^COMMIT=[0-9a-f]{40}$' "$incoming/manifest.env" || { echo 'Invalid manifest commit.' >&2; exit 21; }
COMPONENT=$(sed -n 's/^COMPONENT=//p' "$incoming/manifest.env")
COMMIT=$(sed -n 's/^COMMIT=//p' "$incoming/manifest.env")
[[ $(grep -c '^COMPONENT=' "$incoming/manifest.env") -eq 1 ]] || { echo 'Duplicate component in manifest.' >&2; exit 21; }
[[ $(grep -c '^COMMIT=' "$incoming/manifest.env") -eq 1 ]] || { echo 'Duplicate commit in manifest.' >&2; exit 21; }

case "$COMPONENT" in
    replacement-backend|legacy-api|admin|all) ;;
    *) echo "Unsupported component: $COMPONENT" >&2; exit 22 ;;
esac
if [[ "$RUN_MIGRATIONS" == true && "$COMPONENT" != replacement-backend ]]; then
    echo 'Database migrations are only allowed for replacement-backend.' >&2
    exit 30
fi
if [[ "$RUN_MIGRATIONS" == true && "$MIGRATION" != 014 && "$MIGRATION" != 015 && "$MIGRATION" != 016 ]]; then
    echo 'Run migrations requires --migration 014, 015 or 016.' >&2
    exit 30
fi
if [[ "$RUN_MIGRATIONS" == false && -n "$MIGRATION" ]]; then
    echo '--migration requires --run-migrations.' >&2
    exit 30
fi
release_dir="$REMOTE_ROOT/releases/$COMMIT/$COMPONENT"
backup_dir="$REMOTE_ROOT/backups/$(date +%Y%m%d-%H%M%S)-$COMMIT-$COMPONENT"
mkdir -p "$release_dir" "$backup_dir"
cp -p "$ARCHIVE" "$release_dir/release.tgz"
printf '%s  release.tgz\n' "$actual_sha256" > "$release_dir/SHA256SUMS"

if [[ "$RUN_MIGRATIONS" == true ]]; then
    if [[ "$MIGRATION" == 014 ]]; then
        migration_status=apply_migration_014
    elif [[ "$MIGRATION" == 015 ]]; then
        migration_status=apply_migration_015
    else
        migration_status=apply_migration_016
    fi
    if ! $migration_status; then
        echo "backup=$backup_dir" >&2
        exit 25
    fi
fi

rollback_component() {
    local component="$1"
    case "$component" in
        replacement-backend)
            if [[ -f "$backup_dir/starfree-replacement.jar" ]]; then
                if ! systemctl stop starfree-replacement.service; then
                    echo 'Could not stop replacement backend before rollback.' >&2
                    return 20
                fi
                install -m 0644 "$backup_dir/starfree-replacement.jar" /opt/starfree-replacement/starfree-replacement.jar
                systemctl start starfree-replacement.service || true
                wait_for_component replacement-backend 60 || true
            elif [[ -f "$backup_dir/replacement-backend.no-previous" ]]; then
                if ! systemctl stop starfree-replacement.service; then
                    echo 'Could not stop replacement backend before rollback.' >&2
                    return 20
                fi
                rm -f /opt/starfree-replacement/starfree-replacement.jar
            fi
            ;;
        legacy-api)
            if [[ -f "$backup_dir/StarFreeApi.jar" ]]; then
                if ! systemctl stop starfree-legacy.service; then
                    echo 'Could not stop legacy API before rollback.' >&2
                    return 20
                fi
                install -m 0644 "$backup_dir/StarFreeApi.jar" /opt/StarFreeApi.jar
                systemctl start starfree-legacy.service || true
                wait_for_component legacy-api 60 || true
            elif [[ -f "$backup_dir/legacy-api.no-previous" ]]; then
                if ! systemctl stop starfree-legacy.service; then
                    echo 'Could not stop legacy API before rollback.' >&2
                    return 20
                fi
                rm -f /opt/StarFreeApi.jar
            fi
            ;;
        admin)
            if [[ -f "$backup_dir/admin.tar.gz" ]]; then
                rm -rf /www/wwwroot/admin.lcxqy.cn
                mkdir -p /www/wwwroot
                tar -xzf "$backup_dir/admin.tar.gz" -C /www/wwwroot
            elif [[ -f "$backup_dir/admin.no-previous" ]]; then
                rm -rf /www/wwwroot/admin.lcxqy.cn
            fi
            ;;
    esac
}

backup_current_link() {
    local component="$1"
    local link="$REMOTE_ROOT/current/$component"
    if [[ -L "$link" ]]; then
        readlink "$link" > "$backup_dir/current-$component.link"
    else
        touch "$backup_dir/current-$component.no-previous"
    fi
}

restore_current_link() {
    local component="$1"
    local link="$REMOTE_ROOT/current/$component"
    if [[ -f "$backup_dir/current-$component.link" ]]; then
        ln -sfn "$(<"$backup_dir/current-$component.link")" "$link"
    elif [[ -f "$backup_dir/current-$component.no-previous" ]]; then
        rm -f "$link"
    fi
}

deploy_replacement() {
    local target=/opt/starfree-replacement/starfree-replacement.jar
    mkdir -p /opt/starfree-replacement || return 20
    if ! validate_jar "$incoming/replacement-backend.jar"; then
        echo 'Replacement JAR validation failed.' >&2
        return 23
    fi
    if [[ -f "$target" ]]; then
        cp -p "$target" "$backup_dir/starfree-replacement.jar" || return 20
    else
        touch "$backup_dir/replacement-backend.no-previous" || return 20
    fi
    if ! systemctl stop starfree-replacement.service; then
        echo 'Could not stop replacement backend before deployment.' >&2
        return 20
    fi
    if ! install -m 0644 "$incoming/replacement-backend.jar" "$target"; then
        rollback_component replacement-backend
        return 20
    fi
    if ! systemctl start starfree-replacement.service; then
        rollback_component replacement-backend
        return 20
    fi
    if ! wait_for_component replacement-backend 60; then
        rollback_component replacement-backend
        return 21
    fi
}

deploy_legacy() {
    local target=/opt/StarFreeApi.jar
    if ! validate_jar "$incoming/legacy-api.jar"; then
        echo 'Legacy JAR validation failed.' >&2
        return 23
    fi
    if [[ -f "$target" ]]; then
        cp -p "$target" "$backup_dir/StarFreeApi.jar" || return 20
    else
        touch "$backup_dir/legacy-api.no-previous" || return 20
    fi
    if ! systemctl stop starfree-legacy.service; then
        echo 'Could not stop legacy API before deployment.' >&2
        return 20
    fi
    if ! install -m 0644 "$incoming/legacy-api.jar" "$target"; then
        rollback_component legacy-api
        return 20
    fi
    if ! systemctl start starfree-legacy.service; then
        rollback_component legacy-api
        return 20
    fi
    if ! wait_for_component legacy-api 60; then
        rollback_component legacy-api
        return 21
    fi
}

deploy_admin() {
    local target=/www/wwwroot/admin.lcxqy.cn
    if [[ -d "$target" ]]; then
        tar -czf "$backup_dir/admin.tar.gz" -C /www/wwwroot "$(basename "$target")" || return 20
    else
        touch "$backup_dir/admin.no-previous" || return 20
    fi
    if tar -tzf "$incoming/admin.tar.gz" | grep -E '(^/|(^|/)\.\.(/|$))' >/dev/null; then
        echo 'Unsafe path in admin archive.' >&2
        return 23
    fi
    tar --no-same-owner --no-same-permissions -xzf "$incoming/admin.tar.gz" -C "$incoming" || return 23
    if find "$incoming/starfree-admin" -type l -print -quit | grep -q .; then
        echo 'Symbolic links are not allowed in the admin package.' >&2
        return 23
    fi
    if ! (cd "$incoming/starfree-admin" && TARGET_DIR="$target" bash deploy/install.sh); then
        rollback_component admin
        return 20
    fi
    if ! health_check admin; then
        rollback_component admin
        return 21
    fi
}

deployed=()
deploy_one() {
    local component="$1"
    backup_current_link "$component" || return 20
    case "$component" in
        replacement-backend) [[ -f "$incoming/replacement-backend.jar" ]] || { echo 'Replacement JAR missing.' >&2; return 23; }; deploy_replacement ;;
        legacy-api) [[ -f "$incoming/legacy-api.jar" ]] || { echo 'Legacy JAR missing.' >&2; return 23; }; deploy_legacy ;;
        admin) [[ -f "$incoming/admin.tar.gz" ]] || { echo 'Admin archive missing.' >&2; return 23; }; deploy_admin ;;
    esac
    ln -sfn "$release_dir" "$REMOTE_ROOT/current/$component" || return 20
    deployed+=("$component")
}

if [[ "$COMPONENT" == all ]]; then
    for item in replacement-backend legacy-api admin; do
        if ! deploy_one "$item"; then
            for ((i=${#deployed[@]}-1; i>=0; i--)); do
                rollback_component "${deployed[$i]}"
                restore_current_link "${deployed[$i]}"
            done
            echo 'Combined deployment failed and completed components were restored.' >&2
            exit 24
        fi
    done
else
    deploy_one "$COMPONENT"
fi

echo "component=$COMPONENT"
echo "commit=$COMMIT"
echo "archive_sha256=$actual_sha256"
echo "backup=$backup_dir"
for item in "${deployed[@]}"; do verify_component "$item"; done
