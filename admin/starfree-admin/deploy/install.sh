#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$(cd "${SCRIPT_DIR}/../source" && pwd)"
TARGET_DIR="${TARGET_DIR:-/www/wwwroot/admin.lcxqy.cn}"
WEB_USER="${WEB_USER:-www}"
WEB_GROUP="${WEB_GROUP:-www}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
USER_INI="$TARGET_DIR/.user.ini"
USER_INI_WAS_IMMUTABLE=0

restore_user_ini_immutable() {
    local exit_code=$?
    if [[ "$USER_INI_WAS_IMMUTABLE" -eq 1 && -e "$USER_INI" ]]; then
        if ! chattr +i -- "$USER_INI"; then
            echo "CRITICAL: failed to restore the immutable attribute on $USER_INI." >&2
            [[ "$exit_code" -ne 0 ]] || exit_code=1
        fi
    fi
    trap - EXIT
    exit "$exit_code"
}

if [[ "$(id -u)" -ne 0 ]]; then
    echo "Run this installer as root." >&2
    exit 1
fi
if ! command -v php >/dev/null 2>&1; then
    echo "PHP CLI is required for the syntax check." >&2
    exit 1
fi
if [[ -e "$USER_INI" ]]; then
    if ! command -v lsattr >/dev/null 2>&1; then
        echo "lsattr is required to inspect the existing $USER_INI safely." >&2
        exit 1
    fi
    if ! user_ini_attributes="$(lsattr -d -- "$USER_INI" 2>/dev/null)"; then
        echo "Unable to inspect attributes for $USER_INI; no files were changed." >&2
        exit 1
    fi
    if [[ "${user_ini_attributes%% *}" == *i* ]]; then
        if ! command -v chattr >/dev/null 2>&1; then
            echo "chattr is required to update the immutable $USER_INI safely." >&2
            exit 1
        fi
        USER_INI_WAS_IMMUTABLE=1
    fi
fi

if ! lint_output="$(find "$SOURCE_DIR" -type f -name '*.php' -print0 | xargs -0 -n1 php -l 2>&1)"; then
    printf '%s\n' "$lint_output" >&2
    echo "PHP syntax check failed." >&2
    exit 1
fi

install -d -m 0755 "$TARGET_DIR"

if [[ ! -f "$TARGET_DIR/Config_DB.php" && -d /srv/lcxqy/backups ]]; then
    target_name="$(basename "$TARGET_DIR")"
    while IFS= read -r archive; do
        [[ -n "$archive" ]] || continue
        if tar -tzf "$archive" | grep -Fx "$target_name/Config_DB.php" >/dev/null; then
            tar --no-same-owner --no-same-permissions \
                --exclude="$target_name/.user.ini" \
                -xzf "$archive" -C "$(dirname "$TARGET_DIR")"
            echo "Recovered admin runtime files from $archive."
            break
        fi
    done < <(find /srv/lcxqy/backups -mindepth 2 -maxdepth 2 \
        -type f -name admin.tar.gz -printf '%T@ %p\n' 2>/dev/null \
        | sort -nr | cut -d' ' -f2-)
fi

if [[ -f "$TARGET_DIR/Config_DB.php" ]]; then
    cp -p "$TARGET_DIR/Config_DB.php" "$TARGET_DIR/Config_DB.php.backup-${TIMESTAMP}"
fi
if [[ -d "$TARGET_DIR" ]] && find "$TARGET_DIR" -mindepth 1 -maxdepth 1 -type f | grep -q .; then
    backup_dir="${TARGET_DIR}.backup-${TIMESTAMP}"
    install -d -m 0755 "$backup_dir"
    cp -a "$TARGET_DIR"/. "$backup_dir"/
fi

if [[ -e "$USER_INI" ]]; then
    tar -C "$SOURCE_DIR" --exclude='./.user.ini' -cf - . | tar -C "$TARGET_DIR" -xf -
else
    cp -a "$SOURCE_DIR"/. "$TARGET_DIR"/
fi
if [[ ! -f "$TARGET_DIR/Config_DB.php" ]]; then
    install -m 0600 "$SOURCE_DIR/Config_DB.example.php" "$TARGET_DIR/Config_DB.php"
    echo "Created $TARGET_DIR/Config_DB.php from the safe template."
    echo "Fill every CHANGE_ME value, then run this installer again." >&2
    exit 2
fi
if [[ "$USER_INI_WAS_IMMUTABLE" -eq 1 ]]; then
    trap restore_user_ini_immutable EXIT
    if ! chattr -i -- "$USER_INI"; then
        echo "Unable to remove the immutable attribute from $USER_INI." >&2
        exit 1
    fi
fi
touch "$USER_INI"
while IFS= read -r setting; do
    key="${setting%%=*}"
    if grep -Eq "^[[:space:]]*${key}[[:space:]]*=" "$USER_INI"; then
        sed -i -E "s|^[[:space:]]*${key}[[:space:]]*=.*$|${setting}|" "$USER_INI"
    else
        printf '%s\n' "$setting" >>"$USER_INI"
    fi
done <"$SCRIPT_DIR/php-security.ini"
if grep -q 'CHANGE_ME' "$TARGET_DIR/Config_DB.php"; then
    echo "$TARGET_DIR/Config_DB.php still contains CHANGE_ME placeholders." >&2
    exit 2
fi

find "$TARGET_DIR" -mindepth 1 ! -path "$USER_INI" \
    -exec chown "$WEB_USER:$WEB_GROUP" {} +
find "$TARGET_DIR" -type d -exec chmod 0755 {} +
find "$TARGET_DIR" -type f ! -path "$USER_INI" -exec chmod 0644 {} +
chmod 0644 "$USER_INI"
chmod 0600 "$TARGET_DIR/Config_DB.php"

echo "Admin files installed at $TARGET_DIR."
echo "Run nginx -t and reload Nginx after checking deploy/nginx-admin.conf."
