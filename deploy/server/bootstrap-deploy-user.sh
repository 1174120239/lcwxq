#!/usr/bin/env bash
set -euo pipefail

PUBLIC_KEY_FILE=${1:-}
DEPLOY_USER=${DEPLOY_USER:-deploy}
[[ "$(id -u)" -eq 0 ]] || { echo 'Run this one-time bootstrap as root.' >&2; exit 1; }
[[ -r "$PUBLIC_KEY_FILE" ]] || { echo 'Usage: bootstrap-deploy-user.sh PUBLIC_KEY_FILE' >&2; exit 2; }
public_key=$(tr -d '\r\n' < "$PUBLIC_KEY_FILE")
[[ "$public_key" =~ ^ssh-ed25519\  ]] || { echo 'Only an Ed25519 public key is accepted.' >&2; exit 2; }

if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
    useradd --create-home --shell /bin/bash "$DEPLOY_USER"
fi
home_dir=$(getent passwd "$DEPLOY_USER" | cut -d: -f6)
install -d -m 0700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$home_dir/.ssh"
printf '%s\n' "$public_key" > "$home_dir/.ssh/authorized_keys"
chown "$DEPLOY_USER:$DEPLOY_USER" "$home_dir/.ssh/authorized_keys"
chmod 0600 "$home_dir/.ssh/authorized_keys"

cat > /etc/sudoers.d/lcxqy-deploy <<EOF
Defaults:${DEPLOY_USER} !requiretty
${DEPLOY_USER} ALL=(root) NOPASSWD: /usr/local/sbin/lcxqy-deploy, /usr/local/sbin/lcxqy-deploy *, /usr/local/sbin/lcxqy-rollback, /usr/local/sbin/lcxqy-rollback *
EOF
chmod 0440 /etc/sudoers.d/lcxqy-deploy
visudo -cf /etc/sudoers.d/lcxqy-deploy

echo "deploy_user=$DEPLOY_USER"
echo "authorized_keys=$home_dir/.ssh/authorized_keys"
echo 'Test the new login in a second terminal before changing any existing SSH setting.'
