#!/usr/bin/env bash
set -euo pipefail

# Disposable anonymous dynamics verification. The public config must not leak the
# anonymous account, unauthenticated writes must be rejected, and the owner
# lookup must fail for non-anonymous ids. Run after route promotion.
REPLACEMENT_URL=${REPLACEMENT_URL:-http://127.0.0.1:18082}
PUBLIC_URL=${PUBLIC_URL:-https://api.lcxqy.cn}
VERIFY_PUBLIC=${VERIFY_PUBLIC:-0}

for cmd in curl python; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "Missing $cmd" >&2; exit 2; }
done

json_value() {
    local response="$1" key="$2"
    printf '%s' "$response" | python -c \
        'import json,sys; print(json.load(sys.stdin).get(sys.argv[1], ""))' "$key"
}

check_public_config() {
    local url="$1" label="$2"
    local response
    response="$(curl -sS "$url/SFreeAnonymous/config")"
    [[ "$(json_value "$response" code)" == 1 ]] || {
        echo "$label config did not return code=1" >&2
        echo "$response" >&2
        exit 10
    }
    if printf '%s' "$response" | python -c \
        'import json,sys; data=json.load(sys.stdin).get("data",{}); assert isinstance(data.get("enabled"), bool); assert set(data.keys()) <= {"enabled","categoryPick"}, data.keys()'; then
        echo "$label config=code:1,no-identity"
    else
        echo "$label config leaked identity or changed shape" >&2
        echo "$response" >&2
        exit 11
    fi
}

check_public_config "$REPLACEMENT_URL" "local"
[[ "$VERIFY_PUBLIC" == 1 ]] && check_public_config "$PUBLIC_URL" "public"

unauth_post="$(curl -sS -X POST "$REPLACEMENT_URL/SFreeAnonymous/post" \
    --data 'type=0' --data 'text=匿名动态验收正文' --data 'pic=' --data 'topicIds=0')"
[[ "$(json_value "$unauth_post" code)" == 0 ]] || {
    echo "Anonymous post accepted without token" >&2
    echo "$unauth_post" >&2
    exit 12
}
echo "unauth-post=rejected"

owner_invalid="$(curl -sS "$REPLACEMENT_URL/SFreeAnonymous/owner?cid=1")"
[[ "$(json_value "$owner_invalid" code)" == 0 ]] || {
    echo "Anonymous owner lookup accepted an invalid request" >&2
    echo "$owner_invalid" >&2
    exit 13
}
echo "owner-invalid=rejected"

admin_config="$(curl -sS "$REPLACEMENT_URL/SFreeAnonymous/admin/config")"
[[ "$(json_value "$admin_config" code)" == 0 ]] || {
    echo "Anonymous admin config exposed without administrator token" >&2
    echo "$admin_config" >&2
    exit 14
}
echo "admin-config=rejected-unauth"
echo "verify-anonymous=ok"
