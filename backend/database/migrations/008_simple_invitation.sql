-- Simple one-level invitation rewards and download-page configuration.
-- Apply only after the replacement backend containing SFreeInvitation is deployed.

CREATE TABLE IF NOT EXISTS lcxqy_invitation_config (
  id TINYINT UNSIGNED NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  reward_points INT UNSIGNED NOT NULL DEFAULT 10,
  reward_experience INT UNSIGNED NOT NULL DEFAULT 20,
  android_download_url VARCHAR(1000) NOT NULL DEFAULT '',
  ios_download_url VARCHAR(1000) NOT NULL DEFAULT '',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Simple invitation settings';

INSERT INTO lcxqy_invitation_config
  (id, enabled, reward_points, reward_experience, android_download_url, ios_download_url)
VALUES (1, 1, 10, 20, '', '')
ON DUPLICATE KEY UPDATE id = VALUES(id);

CREATE TABLE IF NOT EXISTS lcxqy_invitation_codes (
  uid BIGINT UNSIGNED NOT NULL,
  invite_code VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (uid),
  UNIQUE KEY uq_invitation_code (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reusable invitation code owned by each user';

CREATE TABLE IF NOT EXISTS lcxqy_invitation_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  inviter_uid BIGINT UNSIGNED NOT NULL,
  invitee_uid BIGINT UNSIGNED NOT NULL,
  invite_code VARCHAR(16) NOT NULL,
  reward_points INT UNSIGNED NOT NULL DEFAULT 0,
  reward_experience INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_invitation_invitee (invitee_uid),
  KEY idx_invitation_inviter (inviter_uid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One-time reward record for a successful invitation';
