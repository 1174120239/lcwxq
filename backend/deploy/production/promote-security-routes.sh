#!/usr/bin/env bash
set -euo pipefail

# Run only in a production maintenance session after the replacement JAR and PHP admin are deployed.
CONF=${CONF:-/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP="$CONF.rollback-security-$STAMP"

declare -A ROUTES=(
    [/SFreeUsers/userLogin]=replacement-secure-session
    [/SFreeUsers/phoneLogin]=replacement-secure-session
    [/SFreeUsers/signOut]=replacement-secure-session
    [/SFreeUsers/apiLogin]=replacement-secure-session
    [/SFreeUsers/userList]=replacement-private-projection
    [/SFreeUsers/followList]=replacement-private-projection
    [/SFreeUsers/fanList]=replacement-private-projection
    [/SFreeUsers/violationList]=replacement-private-projection
    [/SFreeChat/allChat]=replacement-staff-gateway
    [/SFreeUsers/manageUserEdit]=replacement-security-management
    [/SFreeUsers/userDelete]=replacement-security-management
    [/SFreeUsers/banUser]=replacement-security-management
    [/SFreeUsers/unblockUser]=replacement-security-management
    [/SFreeUsers/userClean]=replacement-security-management
    [/SFreeUsers/restrict]=replacement-security-management
    [/SFreeUsers/giftVIP]=replacement-security-management
    [/SFreeUsers/withdrawStatus]=replacement-security-economy
    [/SFreeContents/contentsAudit]=replacement-security-management
    [/SFreeContents/allData]=replacement-security-management
    [/SFreeComments/commentsAudit]=replacement-security-management
    [/pay/financeList]=replacement-security-economy
    [/pay/financeTotal]=replacement-security-economy
    [/pay/tokenPayList]=replacement-security-legacy-admin
    [/pay/tokenPayExcel]=replacement-security-legacy-admin
    [/pay/madetoken]=replacement-security-legacy-admin
    [/SFreeUsers/apiBind]=replacement-security-legacy-user
    [/SFreeUsers/userBindStatus]=replacement-security-legacy-user
    [/upload/full]=replacement-security-legacy-user
    [/upload/base64]=replacement-security-legacy-user
    [/SFreeChat/getPrivateChat]=replacement-security-legacy-user
    [/SFreeChat/sendMsg]=replacement-security-legacy-user
    [/SFreeChat/myChat]=replacement-security-legacy-user
    [/SFreeChat/msgList]=replacement-security-legacy-user
    [/SFreeChat/deleteChat]=replacement-security-legacy-user
    [/SFreeChat/deleteMsg]=replacement-security-legacy-user
    [/SFreeChat/createGroup]=replacement-security-legacy-user
    [/SFreeChat/editGroup]=replacement-security-legacy-user
    [/SFreeChat/banChat]=replacement-security-legacy-staff
    [/SFreeChat/groupInfo]=replacement-security-legacy-user
    [/pay/scancodePayStar]=replacement-security-legacy-user
    [/pay/WxPayStar]=replacement-security-legacy-user
    [/pay/tokenPay]=replacement-security-legacy-user
    [/pay/tokenPayStar]=replacement-security-legacy-user
    [/pay/EPayStar]=replacement-security-legacy-user
    [/pay/qrCodeStar]=replacement-security-legacy-user
)

for required in awk cp curl grep nginx seq sha256sum; do
    command -v "$required" >/dev/null 2>&1 || {
        echo "Required command not found: $required" >&2
        exit 2
    }
done
[[ -f "$CONF" ]] || { echo "Nginx include missing: $CONF" >&2; exit 2; }

# Secure login is insufficient if profiles, password changes, or public projections can still
# reach the vulnerable closed backend.
declare -A PREREQUISITES=(
    [/SFreeUsers/userStatus]=replacement-user-status
    [/SFreeUsers/userInfo]=replacement-user-info
    [/SFreeUsers/userFoget]=replacement-account-password-reset
    [/SFreeUsers/userEdit]=replacement-account-edit
    [/SFreeContents/contentsList]=replacement-public-read
    [/SFreeSpace/spaceList]=replacement-public-read
)
for path in "${!PREREQUISITES[@]}"; do
    block=$(awk -v target="location = $path {" '
        $0 == target { copy=1 }
        copy { print }
        copy && $0 == "}" { exit }
    ' "$CONF")
    [[ -n "$block" ]] || { echo "Missing prerequisite route: $path" >&2; exit 3; }
    grep -Fq 'proxy_pass http://127.0.0.1:18082;' <<<"$block" || {
        echo "Unsafe prerequisite backend: $path" >&2
        exit 3
    }
done
if grep -Eq 'legacy-token|if \(\$arg_token' "$CONF"; then
    echo "Token-based legacy routing remains in $CONF" >&2
    exit 3
fi

health=$(curl -fsS --max-time 15 "$REPLACEMENT_URL/health")
grep -Eq '"code"[[:space:]]*:[[:space:]]*1' <<<"$health" || {
    echo "Replacement health check did not return code=1" >&2
    exit 4
}

declare -A ACTIVE_HEADERS=()
missing=()
for path in "${!ROUTES[@]}"; do
    location_count=$(grep -Fc "location = $path {" "$CONF" || true)
    [[ "$location_count" -le 1 ]] || { echo "Duplicate route detected: $path" >&2; exit 5; }
    if [[ "$location_count" == 0 ]]; then
        missing+=("$path")
        continue
    fi
    block=$(awk -v target="location = $path {" '
        $0 == target { copy=1 }
        copy { print }
        copy && $0 == "}" { exit }
    ' "$CONF")
    grep -Fq 'proxy_pass http://127.0.0.1:18082;' <<<"$block" || {
        echo "Existing route bypasses replacement security: $path" >&2
        exit 5
    }
    header=$(awk '/add_header X-Starfree-Backend/ {print $3; exit}' <<<"$block")
    [[ "$header" == replacement-* ]] || {
        echo "Existing route lacks a replacement backend marker: $path" >&2
        exit 5
    }
    ACTIVE_HEADERS[$path]=$header
done

if [[ "${#missing[@]}" == 0 ]]; then
    BACKUP=already-promoted
else
    cp -p "$CONF" "$BACKUP"
    rollback() {
        echo "Security route promotion failed; restoring $BACKUP" >&2
        cp -p "$BACKUP" "$CONF"
        nginx -t
        nginx -s reload
    }
    trap rollback ERR INT TERM
    for path in "${missing[@]}"; do
        header=${ROUTES[$path]}
        cat >>"$CONF" <<NGINX

# Security remediation: validate the new session before serving or forwarding this route.
location = $path {
    proxy_pass http://127.0.0.1:18082;
    add_header X-Starfree-Backend $header always;
    proxy_connect_timeout 10;
    proxy_read_timeout 200;
    proxy_send_timeout 200;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header REMOTE-HOST \$remote_addr;
}
NGINX
        ACTIVE_HEADERS[$path]=$header
    done
    nginx -t
    nginx -s reload
    trap - ERR INT TERM
fi

verify_header() {
    local path=$1 expected=$2 observed=''
    for _ in $(seq 1 10); do
        observed=$(curl -sk --max-time 20 -D - -o /dev/null "$PUBLIC_URL$path" \
            | awk 'BEGIN{IGNORECASE=1} /^x-starfree-backend:/ {sub(/\r$/, ""); print $2}')
        [[ "$observed" == "$expected" ]] && break
        sleep 1
    done
    if [[ "$observed" != "$expected" ]]; then
        echo "Backend header mismatch for $path: ${observed:-<missing>}" >&2
        [[ "$BACKUP" == already-promoted ]] || rollback
        exit 21
    fi
    echo "$path=$observed"
}

for path in "${!ROUTES[@]}"; do
    verify_header "$path" "${ACTIVE_HEADERS[$path]}"
done

legacy_status=$(curl -sk --max-time 20 -G "$PUBLIC_URL/SFreeUsers/userStatus" \
    --data-urlencode 'token=legacy-token-format-must-be-rejected')
grep -Eq '"code"[[:space:]]*:[[:space:]]*0' <<<"$legacy_status" || {
    echo "Legacy token format was not rejected" >&2
    [[ "$BACKUP" == already-promoted ]] || rollback
    exit 22
}

for path in /SFreeUsers/userList /SFreeContents/contentsList /SFreeSpace/spaceList; do
    body=$(curl -sk --max-time 30 -G "$PUBLIC_URL$path" \
        --data-urlencode 'page=1' --data-urlencode 'limit=5')
    if grep -Eq '"(ip|local|logged|clientId)"[[:space:]]*:' <<<"$body"; then
        echo "Private field found in public response: $path" >&2
        [[ "$BACKUP" == already-promoted ]] || rollback
        exit 23
    fi
done

echo "rollback=$BACKUP"
sha256sum "$CONF"
