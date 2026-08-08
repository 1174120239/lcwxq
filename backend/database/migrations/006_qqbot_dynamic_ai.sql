-- QQ personal-account AI bot support for dynamic-only forum operations.
-- Secrets may be configured through the admin panel, but production should
-- prefer environment variables where possible.

CREATE TABLE IF NOT EXISTS lcxqy_bot_config (
  config_key VARCHAR(64) NOT NULL,
  config_value TEXT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lcxqy_bot_bind_challenge (
  id BIGINT NOT NULL AUTO_INCREMENT,
  bind_token VARCHAR(96) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  qq_user_id VARCHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_bot_bind_token (bind_token),
  KEY idx_bot_bind_qq (platform, qq_user_id),
  KEY idx_bot_bind_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lcxqy_bot_bindings (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform VARCHAR(32) NOT NULL,
  qq_user_id VARCHAR(64) NOT NULL,
  forum_uid BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  last_used_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_bot_binding (platform, qq_user_id),
  KEY idx_bot_binding_uid (forum_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lcxqy_bot_group_sync (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform VARCHAR(32) NOT NULL,
  group_id VARCHAR(64) NOT NULL,
  group_name VARCHAR(128) NULL,
  unified_msg_origin VARCHAR(255) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  cursor_space_id BIGINT NOT NULL DEFAULT 0,
  topic_ids_json TEXT NULL,
  max_images INT NOT NULL DEFAULT 3,
  summary_length INT NOT NULL DEFAULT 120,
  last_success_at DATETIME NULL,
  last_error TEXT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_bot_group_sync (platform, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lcxqy_bot_operation_log (
  request_id VARCHAR(96) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  qq_user_id VARCHAR(64) NOT NULL,
  forum_uid BIGINT NULL,
  action VARCHAR(48) NOT NULL,
  payload_hash VARCHAR(64) NULL,
  target_type VARCHAR(32) NULL,
  target_id BIGINT NULL,
  status VARCHAR(24) NOT NULL,
  error_message TEXT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (request_id),
  KEY idx_bot_operation_user (platform, qq_user_id),
  KEY idx_bot_operation_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lcxqy_bot_delivery_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform VARCHAR(32) NOT NULL,
  group_id VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  message_id VARCHAR(128) NULL,
  status VARCHAR(24) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  error_message TEXT NULL,
  delivered_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_bot_delivery (platform, group_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO lcxqy_bot_config(config_key, config_value, updated_at)
VALUES
  ('enabled', '0', NOW()),
  ('bot_secret', '', NOW()),
  ('bot_public_base_url', '', NOW()),
  ('h5_base_url', 'https://prev.lcxqy.cn', NOW()),
  ('forum_register_url', 'https://prev.lcxqy.cn/#/pages/user/register', NOW()),
  ('deepseek_api_key', '', NOW()),
  ('deepseek_api_base', 'https://api.deepseek.com', NOW()),
  ('deepseek_model', 'deepseek-chat', NOW()),
  ('sync_interval_seconds', '45', NOW()),
  ('sync_max_images', '3', NOW()),
  ('sync_summary_length', '120', NOW()),
  ('tool_add_space', '1', NOW()),
  ('tool_update_profile', '1', NOW()),
  ('tool_status', '1', NOW()),
  ('tool_signin', '1', NOW())
ON DUPLICATE KEY UPDATE config_key = VALUES(config_key);
