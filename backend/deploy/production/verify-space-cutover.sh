#!/usr/bin/env bash
set -euo pipefail

# Read-only post-cutover audit. It verifies service health, disposable test-data
# cleanup, exact Space read routing, and every isolated Space write route. No
# database rows or Nginx files are changed here.

PROPERTIES_FILE=${PROPERTIES_FILE:-/opt/application.properties}
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
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

header_for() {
    local method="$1"
    local url="$2"
    curl -sk -X "$method" -D - -o /dev/null "$url" \
        | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $0}'
}

assert_header() {
    local label="$1" expected="$2" method="$3" url="$4" actual
    actual="$(header_for "$method" "$url")"
    if [[ "$actual" != "X-Starfree-Backend: $expected" ]]; then
        echo "$label backend mismatch: ${actual:-<missing>}" >&2
        exit 10
    fi
    echo "$label=$expected"
}

DB_USERNAME=${DB_USERNAME:-$(read_property spring.datasource.username)}
DB_PASSWORD=${DB_PASSWORD:-$(read_property spring.datasource.password)}
if [[ -z "$DB_USERNAME" || -z "$DB_PASSWORD" ]]; then
    echo "Database credentials are missing" >&2
    exit 2
fi
export MYSQL_PWD="$DB_PASSWORD"

sql() {
    mysql --protocol=TCP --host="$DB_HOST" --port="$DB_PORT" \
        --user="$DB_USERNAME" --batch --skip-column-names \
        "$DB_NAME" --execute="$1"
}

service_state="$(systemctl is-active starfree-replacement.service)"
if [[ "$service_state" != active ]]; then
    echo "Replacement service is not active: $service_state" >&2
    exit 11
fi
echo "service=active"
systemctl show -p MainPID starfree-replacement.service
sha256sum /opt/starfree-replacement/starfree-replacement.jar
sha256sum "$CONF"

disposable_users="$(sql "SELECT COUNT(*) FROM starfree_users WHERE name LIKE 'codex_r_%' OR LEFT(name,4) IN ('csl_','csf_','csa_')")"
disposable_space="$(sql "SELECT COUNT(*) FROM starfree_space WHERE text = 'codex redis-only private smoke'")"
disposable_like_space="$(sql "SELECT COUNT(*) FROM starfree_space WHERE text = 'codex disposable Space-like smoke'")"
disposable_follow_space="$(sql "SELECT COUNT(*) FROM starfree_space WHERE text LIKE 'codex follow %'")"
disposable_add_space="$(sql "SELECT COUNT(*) FROM starfree_space WHERE text LIKE 'csa\\_%'")"
disposable_edit_users="$(sql "SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='cse_'")"
disposable_edit_space="$(sql "SELECT COUNT(*) FROM starfree_space WHERE text LIKE 'cse\\_%'")"
disposable_review_users="$(sql "SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='csr_'")"
disposable_review_space="$(sql "SELECT COUNT(*) FROM starfree_space WHERE text LIKE 'csr\\_%'")"
disposable_lock_users="$(sql "SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='csk_'")"
disposable_lock_space="$(sql "SELECT COUNT(*) FROM starfree_space WHERE text LIKE 'csk\\_%'")"
disposable_delete_users="$(sql "SELECT COUNT(*) FROM starfree_users WHERE LEFT(name,4)='csd_'")"
disposable_delete_space="$(sql "SELECT COUNT(*) FROM starfree_space WHERE text LIKE 'csd\\_%'")"
if [[ "$disposable_users" != 0 || "$disposable_space" != 0 || "$disposable_like_space" != 0 || "$disposable_follow_space" != 0 || "$disposable_add_space" != 0 || "$disposable_edit_users" != 0 || "$disposable_edit_space" != 0 || "$disposable_review_users" != 0 || "$disposable_review_space" != 0 || "$disposable_lock_users" != 0 || "$disposable_lock_space" != 0 || "$disposable_delete_users" != 0 || "$disposable_delete_space" != 0 ]]; then
    echo "Disposable data remains: users=$disposable_users private_space=$disposable_space like_space=$disposable_like_space follow_space=$disposable_follow_space add_space=$disposable_add_space edit_users=$disposable_edit_users edit_space=$disposable_edit_space review_users=$disposable_review_users review_space=$disposable_review_space lock_users=$disposable_lock_users lock_space=$disposable_lock_space delete_users=$disposable_delete_users delete_space=$disposable_delete_space" >&2
    exit 12
fi
echo "disposable_users=0"
echo "disposable_space=0"
echo "disposable_like_space=0"
echo "disposable_follow_space=0"
echo "disposable_add_space=0"
echo "disposable_edit_users=0"
echo "disposable_edit_space=0"
echo "disposable_review_users=0"
echo "disposable_review_space=0"
echo "disposable_lock_users=0"
echo "disposable_lock_space=0"
echo "disposable_delete_users=0"
echo "disposable_delete_space=0"

nginx -t

# The exact Space routes are two general reads, two followed-feed aliases,
# likes, add, edit, review, lock, and delete.
space_locations="$(grep -c '^location = /SFreeSpace/' "$CONF")"
if [[ "$space_locations" != 10 ]]; then
    echo "Unexpected number of exact Space locations: $space_locations" >&2
    exit 13
fi
if [[ "$(grep -c '^location = /SFreeSpace/spaceReview {' "$CONF")" != 1 ]]; then
    echo "The exact spaceReview location is missing or duplicated" >&2
    exit 14
fi
if [[ "$(grep -c '^location = /SFreeSpace/editSpace {' "$CONF")" != 1 ]]; then
    echo "The exact editSpace location is missing or duplicated" >&2
    exit 14
fi
if [[ "$(grep -c '^location = /SFreeSpace/addSpace {' "$CONF")" != 1 ]]; then
    echo "The exact addSpace location is missing or duplicated" >&2
    exit 14
fi
if [[ "$(grep -c '^location = /SFreeSpace/spaceLikes {' "$CONF")" != 1 ]]; then
    echo "The exact Space-like location is missing or duplicated" >&2
    exit 14
fi
if [[ "$(grep -Ec '^location = /SFreeSpace/(followSpace|myFollowSpace) {' "$CONF")" != 2 ]]; then
    echo "A followed-Space location is missing or duplicated" >&2
    exit 14
fi
if [[ "$(grep -c '^location = /SFreeSpace/spaceLock {' "$CONF")" != 1 ]]; then
    echo "The exact spaceLock location is missing or duplicated" >&2
    exit 14
fi
if [[ "$(grep -c '^location = /SFreeSpace/spaceDelete {' "$CONF")" != 1 ]]; then
    echo "The exact spaceDelete location is missing or duplicated" >&2
    exit 14
fi
echo "space_exact_locations=10"
echo "space_general_read_locations=2"
echo "space_follow_locations=2"
echo "space_like_location=1"
echo "space_add_location=1"
echo "space_edit_location=1"
echo "space_review_location=1"
echo "space_lock_location=1"
echo "space_delete_location=1"

assert_header space_list_anonymous replacement-public-read GET \
    "$PUBLIC_URL/SFreeSpace/spaceList?searchParams=%7B%7D&limit=1&page=1"
assert_header space_list_token replacement-public-read GET \
    "$PUBLIC_URL/SFreeSpace/spaceList?searchParams=%7B%7D&limit=1&page=1&token=codex_invalid_token"
assert_header space_info_anonymous replacement-public-read GET \
    "$PUBLIC_URL/SFreeSpace/spaceInfo?id=1"
assert_header space_info_token replacement-public-read GET \
    "$PUBLIC_URL/SFreeSpace/spaceInfo?id=1&token=codex_invalid_token"
assert_header contents_list_token legacy-token GET \
    "$PUBLIC_URL/SFreeContents/contentsList?searchParams=%7B%7D&limit=1&page=1&token=codex_invalid_token"
assert_header space_like replacement-space-like POST \
    "$PUBLIC_URL/SFreeSpace/spaceLikes?token=codex_invalid_token&id=0"
assert_header follow_space replacement-space-follow GET \
    "$PUBLIC_URL/SFreeSpace/followSpace?token=codex_invalid_token&page=1&limit=1"
assert_header my_follow_space replacement-space-follow GET \
    "$PUBLIC_URL/SFreeSpace/myFollowSpace?token=codex_invalid_token&page=1&limit=1"
assert_header space_add replacement-space-add POST \
    "$PUBLIC_URL/SFreeSpace/addSpace?token=codex_invalid_token"
assert_header space_edit replacement-space-edit POST \
    "$PUBLIC_URL/SFreeSpace/editSpace?token=codex_invalid_token"
assert_header space_review replacement-space-review POST \
    "$PUBLIC_URL/SFreeSpace/spaceReview?token=codex_invalid_token"
assert_header space_lock replacement-space-lock POST \
    "$PUBLIC_URL/SFreeSpace/spaceLock?token=codex_invalid_token"
assert_header space_delete replacement-space-delete POST \
    "$PUBLIC_URL/SFreeSpace/spaceDelete?token=codex_invalid_token"
echo "space_cutover_audit=PASS"
