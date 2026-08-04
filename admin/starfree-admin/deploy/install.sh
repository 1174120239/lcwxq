#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$(cd "${SCRIPT_DIR}/../source" && pwd)"
TARGET_DIR="${TARGET_DIR:-/www/wwwroot/admin.lcxqy.cn}"
WEB_USER="${WEB_USER:-www}"
WEB_GROUP="${WEB_GROUP:-www}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

if [[ "$(id -u)" -ne 0 ]]; then
    echo "Run this installer as root." >&2
    exit 1
fi
if ! command -v php >/dev/null 2>&1; then
    echo "PHP CLI is required for the syntax check." >&2
    exit 1
fi

if ! lint_output="$(find "$SOURCE_DIR" -type f -name '*.php' -print0 | xargs -0 -n1 php -l 2>&1)"; then
    printf '%s\n' "$lint_output" >&2
    echo "PHP syntax check failed." >&2
    exit 1
fi

install -d -m 0755 "$TARGET_DIR"
if [[ -f "$TARGET_DIR/Config_DB.php" ]]; then
    cp -p "$TARGET_DIR/Config_DB.php" "$TARGET_DIR/Config_DB.php.backup-${TIMESTAMP}"
fi
if [[ -d "$TARGET_DIR" ]] && find "$TARGET_DIR" -mindepth 1 -maxdepth 1 -type f | grep -q .; then
    backup_dir="${TARGET_DIR}.backup-${TIMESTAMP}"
    install -d -m 0755 "$backup_dir"
    cp -a "$TARGET_DIR"/. "$backup_dir"/
fi

cp -a "$SOURCE_DIR"/. "$TARGET_DIR"/
if [[ ! -f "$TARGET_DIR/Config_DB.php" ]]; then
    install -m 0600 "$SOURCE_DIR/Config_DB.example.php" "$TARGET_DIR/Config_DB.php"
    echo "Created $TARGET_DIR/Config_DB.php from the safe template."
    echo "Fill every CHANGE_ME value, then run this installer again." >&2
    exit 2
fi
if grep -q 'CHANGE_ME' "$TARGET_DIR/Config_DB.php"; then
    echo "$TARGET_DIR/Config_DB.php still contains CHANGE_ME placeholders." >&2
    exit 2
fi

chown -R "$WEB_USER:$WEB_GROUP" "$TARGET_DIR"
find "$TARGET_DIR" -type d -exec chmod 0755 {} +
find "$TARGET_DIR" -type f -exec chmod 0644 {} +
chmod 0600 "$TARGET_DIR/Config_DB.php"

echo "Admin files installed at $TARGET_DIR."
echo "Run nginx -t and reload Nginx after checking deploy/nginx-admin.conf."
