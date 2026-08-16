-- Unified AI moderation for dynamics, campus Q&A and daily comment review.
-- Configuration and history are retained when either moderation switch is disabled.

SET @ai_schema = DATABASE();

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'space_enabled') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `space_enabled` tinyint unsigned NOT NULL DEFAULT 1 AFTER `enabled`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'question_enabled') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `question_enabled` tinyint unsigned NOT NULL DEFAULT 1 AFTER `space_enabled`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'comment_enabled') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `comment_enabled` tinyint unsigned NOT NULL DEFAULT 0 AFTER `question_enabled`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'comment_review_time') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `comment_review_time` char(5) NOT NULL DEFAULT ''03:30'' AFTER `comment_enabled`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'comment_action') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `comment_action` varchar(24) NOT NULL DEFAULT ''hide'' AFTER `comment_review_time`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'last_comment_review_date') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `last_comment_review_date` date DEFAULT NULL AFTER `custom_prompt`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'last_comment_review_started') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `last_comment_review_started` bigint unsigned NOT NULL DEFAULT 0 AFTER `last_comment_review_date`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'last_comment_review_finished') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `last_comment_review_finished` bigint unsigned NOT NULL DEFAULT 0 AFTER `last_comment_review_started`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'last_comment_review_error') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `last_comment_review_error` varchar(500) NOT NULL DEFAULT '''' AFTER `last_comment_review_finished`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

CREATE TABLE IF NOT EXISTS `starfree_ai_moderation_reviews` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `content_type` varchar(32) NOT NULL,
  `content_id` bigint unsigned NOT NULL,
  `parent_id` bigint unsigned NOT NULL DEFAULT 0,
  `author_uid` int unsigned NOT NULL DEFAULT 0,
  `review_source` varchar(24) NOT NULL DEFAULT 'realtime',
  `content_hash` char(64) NOT NULL,
  `content_snapshot` mediumtext,
  `attachment_summary` varchar(500) NOT NULL DEFAULT '',
  `ai_decision` varchar(24) NOT NULL,
  `risk_category` varchar(80) NOT NULL DEFAULT '',
  `reason` varchar(1000) NOT NULL DEFAULT '',
  `provider` varchar(32) NOT NULL DEFAULT '',
  `model` varchar(80) NOT NULL DEFAULT '',
  `raw_response` mediumtext,
  `content_status` tinyint unsigned NOT NULL DEFAULT 0,
  `human_decision` varchar(24) NOT NULL DEFAULT '',
  `reviewer_uid` int unsigned DEFAULT NULL,
  `review_note` varchar(1000) NOT NULL DEFAULT '',
  `reviewed` bigint unsigned NOT NULL DEFAULT 0,
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_review_content_version` (`content_type`,`content_id`,`content_hash`),
  KEY `idx_ai_review_content` (`content_type`,`content_id`,`created`),
  KEY `idx_ai_review_decision` (`ai_decision`,`created`),
  KEY `idx_ai_review_human` (`human_decision`,`reviewed`),
  KEY `idx_ai_review_author` (`author_uid`,`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Unified AI decisions and staff overrides';

CREATE TABLE IF NOT EXISTS `starfree_ai_comment_daily_summaries` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `review_date` date NOT NULL,
  `range_start` bigint unsigned NOT NULL,
  `range_end` bigint unsigned NOT NULL,
  `scanned_count` int unsigned NOT NULL DEFAULT 0,
  `approved_count` int unsigned NOT NULL DEFAULT 0,
  `risk_count` int unsigned NOT NULL DEFAULT 0,
  `hidden_count` int unsigned NOT NULL DEFAULT 0,
  `failed_count` int unsigned NOT NULL DEFAULT 0,
  `space_comment_count` int unsigned NOT NULL DEFAULT 0,
  `qa_answer_count` int unsigned NOT NULL DEFAULT 0,
  `qa_comment_count` int unsigned NOT NULL DEFAULT 0,
  `category_summary` text,
  `summary_text` text,
  `last_error` varchar(1000) NOT NULL DEFAULT '',
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_comment_summary_date` (`review_date`),
  KEY `idx_ai_comment_summary_created` (`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Daily AI review summary for dynamic and Q&A comments';

CREATE TABLE IF NOT EXISTS `starfree_ai_moderation_actions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `review_id` bigint unsigned DEFAULT NULL,
  `content_type` varchar(32) NOT NULL,
  `content_id` bigint unsigned NOT NULL,
  `operator_uid` int unsigned NOT NULL DEFAULT 0,
  `from_status` tinyint unsigned NOT NULL,
  `to_status` tinyint unsigned NOT NULL,
  `action` varchar(24) NOT NULL,
  `note` varchar(1000) NOT NULL DEFAULT '',
  `created` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_action_content` (`content_type`,`content_id`,`created`),
  KEY `idx_ai_action_review` (`review_id`,`created`),
  KEY `idx_ai_action_operator` (`operator_uid`,`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only staff overrides for AI reviewed content';

INSERT IGNORE INTO `starfree_ai_moderation_reviews`
  (`content_type`,`content_id`,`parent_id`,`author_uid`,`review_source`,`content_hash`,
   `content_snapshot`,`attachment_summary`,`ai_decision`,`risk_category`,`reason`,
   `provider`,`model`,`raw_response`,`content_status`,`human_decision`,`reviewer_uid`,
   `review_note`,`reviewed`,`created`,`modified`)
SELECT
  'space',r.space_id,0,r.author_uid,'legacy',
  SHA2(CONCAT('space:',r.space_id,':',COALESCE(s.text,''),':',COALESCE(s.pic,'')),256),
  COALESCE(s.text,''),CASE WHEN COALESCE(s.pic,'')='' THEN '' ELSE '历史记录包含图片或视频附件' END,
  CASE WHEN r.status='approved' THEN 'approved'
       WHEN r.status='rejected' THEN 'rejected'
       WHEN r.status='pending' THEN 'rejected'
       ELSE 'error' END,
  r.risk_category,r.reason,r.provider,r.model,r.raw_response,
  COALESCE(s.status,0),
  CASE WHEN r.reviewer_uid IS NULL THEN ''
       WHEN r.status='approved' THEN 'approved'
       WHEN r.status='rejected' THEN 'rejected'
       ELSE '' END,
  r.reviewer_uid,r.review_note,
  CASE WHEN r.reviewer_uid IS NULL THEN 0 ELSE r.modified END,
  r.created,r.modified
FROM `starfree_space_ai_reviews` r
LEFT JOIN `starfree_space` s ON s.id=r.space_id;
