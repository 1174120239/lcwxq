-- Dynamic-first community extensions: profile privacy, polls, AI moderation and analytics.
-- This migration is intentionally additive so the retained legacy API can keep using its tables.

CREATE TABLE IF NOT EXISTS `starfree_user_profiles` (
  `uid` int unsigned NOT NULL,
  `gender` varchar(16) NOT NULL DEFAULT '',
  `birthday` date DEFAULT NULL,
  `show_gender` tinyint unsigned NOT NULL DEFAULT 0,
  `show_birthday` tinyint unsigned NOT NULL DEFAULT 0,
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  PRIMARY KEY (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Optional user profile fields and visibility';

CREATE TABLE IF NOT EXISTS `starfree_space_polls` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `space_id` bigint unsigned NOT NULL,
  `title` varchar(80) NOT NULL,
  `description` varchar(240) NOT NULL DEFAULT '',
  `multiple` tinyint unsigned NOT NULL DEFAULT 0,
  `max_choices` tinyint unsigned NOT NULL DEFAULT 1,
  `total_votes` int unsigned NOT NULL DEFAULT 0,
  `created` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_space_poll_space` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Poll attached to a dynamic';

CREATE TABLE IF NOT EXISTS `starfree_space_poll_options` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `poll_id` bigint unsigned NOT NULL,
  `option_text` varchar(80) NOT NULL,
  `sort_order` tinyint unsigned NOT NULL,
  `vote_count` int unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_poll_option_order` (`poll_id`,`sort_order`),
  KEY `idx_poll_option_poll` (`poll_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dynamic poll options';

CREATE TABLE IF NOT EXISTS `starfree_space_poll_votes` (
  `poll_id` bigint unsigned NOT NULL,
  `option_id` bigint unsigned NOT NULL,
  `uid` int unsigned NOT NULL,
  `created` bigint unsigned NOT NULL,
  PRIMARY KEY (`poll_id`,`uid`,`option_id`),
  KEY `idx_poll_vote_option` (`option_id`),
  KEY `idx_poll_vote_user` (`uid`,`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Anonymous dynamic poll votes';

CREATE TABLE IF NOT EXISTS `starfree_ai_moderation_config` (
  `id` tinyint unsigned NOT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT 0,
  `provider` varchar(32) NOT NULL DEFAULT 'deepseek',
  `api_url` varchar(255) NOT NULL DEFAULT 'https://api.deepseek.com/chat/completions',
  `api_key` varchar(512) NOT NULL DEFAULT '',
  `model` varchar(80) NOT NULL DEFAULT 'deepseek-chat',
  `custom_prompt` text,
  `modified_by` int unsigned DEFAULT NULL,
  `modified` bigint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI moderation configuration';

INSERT INTO `starfree_ai_moderation_config`
  (`id`,`enabled`,`provider`,`api_url`,`api_key`,`model`,`custom_prompt`,`modified_by`,`modified`)
VALUES
  (1,0,'deepseek','https://api.deepseek.com/chat/completions','','deepseek-chat','',NULL,0)
ON DUPLICATE KEY UPDATE `id`=`id`;

CREATE TABLE IF NOT EXISTS `starfree_space_ai_reviews` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `space_id` bigint unsigned NOT NULL,
  `author_uid` int unsigned NOT NULL,
  `status` varchar(24) NOT NULL,
  `risk_category` varchar(40) NOT NULL DEFAULT '',
  `reason` varchar(500) NOT NULL DEFAULT '',
  `provider` varchar(32) NOT NULL DEFAULT '',
  `model` varchar(80) NOT NULL DEFAULT '',
  `raw_response` text,
  `reviewer_uid` int unsigned DEFAULT NULL,
  `review_note` varchar(500) NOT NULL DEFAULT '',
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_review_space` (`space_id`),
  KEY `idx_ai_review_queue` (`status`,`created`),
  KEY `idx_ai_review_author` (`author_uid`,`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI and staff moderation audit for dynamics';

CREATE TABLE IF NOT EXISTS `starfree_activity_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `uid` int unsigned DEFAULT NULL,
  `event_type` varchar(40) NOT NULL,
  `space_id` bigint unsigned DEFAULT NULL,
  `created` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_activity_time_type` (`created`,`event_type`),
  KEY `idx_activity_user_time` (`uid`,`created`),
  KEY `idx_activity_space` (`space_id`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Privacy-minimized events for dynamic analytics';
