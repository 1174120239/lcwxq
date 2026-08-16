-- Campus mutual-aid data is intentionally separate from the legacy shop and economy tables.
-- Discussion stays in public comments. A QQ number is disclosed only through an explicit,
-- comment-bound one-way grant to the selected counterpart; no contact field is published here.

CREATE TABLE IF NOT EXISTS `starfree_lost_found_items` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `uid` int unsigned NOT NULL,
  `kind` tinyint unsigned NOT NULL COMMENT '1=request help, 2=offer help',
  `category` tinyint unsigned NOT NULL DEFAULT '5' COMMENT '1=lost-found,2=borrowing,3=study,4=campus-life,5=other',
  `title` varchar(120) NOT NULL,
  `description` text NOT NULL,
  `image_url` varchar(500) NOT NULL DEFAULT '',
  `location` varchar(120) NOT NULL DEFAULT '',
  `occurred_at` bigint unsigned NOT NULL DEFAULT '0',
  `status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0=pending,1=active,2=resolved,3=rejected,4=closed',
  `review_reason` varchar(500) NOT NULL DEFAULT '',
  `reviewed_by` int unsigned NOT NULL DEFAULT '0',
  `reviewed_at` bigint unsigned NOT NULL DEFAULT '0',
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lost_found_public` (`status`,`kind`,`category`,`modified`),
  KEY `idx_lost_found_owner` (`uid`,`status`,`modified`),
  KEY `idx_lost_found_occurred` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Campus mutual-aid notices';

CREATE TABLE IF NOT EXISTS `starfree_lost_found_actions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `item_id` bigint unsigned NOT NULL,
  `operator_uid` int unsigned NOT NULL,
  `from_status` tinyint unsigned NOT NULL,
  `to_status` tinyint unsigned NOT NULL,
  `action` varchar(24) NOT NULL,
  `reason` varchar(500) NOT NULL DEFAULT '',
  `created` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lost_found_action_item` (`item_id`,`created`),
  KEY `idx_lost_found_action_operator` (`operator_uid`,`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only lost-and-found moderation audit';

CREATE TABLE IF NOT EXISTS `starfree_lost_found_comments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `item_id` bigint unsigned NOT NULL,
  `uid` int unsigned NOT NULL,
  `parent_id` bigint unsigned NOT NULL DEFAULT '0',
  `root_id` bigint unsigned NOT NULL DEFAULT '0',
  `text` varchar(1000) NOT NULL,
  `status` tinyint unsigned NOT NULL DEFAULT '1',
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lost_found_comment_item` (`item_id`,`status`,`created`),
  KEY `idx_lost_found_comment_root` (`root_id`,`status`,`created`),
  KEY `idx_lost_found_comment_user` (`uid`,`status`,`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Public discussion under mutual-aid notices';

CREATE TABLE IF NOT EXISTS `starfree_lost_found_contact_grants` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `item_id` bigint unsigned NOT NULL,
  `comment_id` bigint unsigned NOT NULL,
  `sender_uid` int unsigned NOT NULL,
  `receiver_uid` int unsigned NOT NULL,
  `created` bigint unsigned NOT NULL,
  `viewed` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lost_found_contact_grant` (`item_id`,`comment_id`,`sender_uid`,`receiver_uid`),
  KEY `idx_lost_found_contact_receiver` (`receiver_uid`,`item_id`,`created`),
  KEY `idx_lost_found_contact_sender` (`sender_uid`,`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='One-way private QQ contact authorization';

CREATE TABLE IF NOT EXISTS `starfree_lost_found_config` (
  `id` tinyint unsigned NOT NULL DEFAULT '1',
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `minimum_level` tinyint unsigned NOT NULL DEFAULT '2',
  `audit_required` tinyint unsigned NOT NULL DEFAULT '1',
  `contact_enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `daily_contact_limit` int unsigned NOT NULL DEFAULT '5',
  `item_expiry_days` int unsigned NOT NULL DEFAULT '30',
  `modified_by` int unsigned NOT NULL DEFAULT '0',
  `modified` bigint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Campus mutual-aid feature configuration';

INSERT INTO `starfree_lost_found_config`
  (`id`,`enabled`,`minimum_level`,`audit_required`,`contact_enabled`,`daily_contact_limit`,
   `item_expiry_days`,`modified_by`,`modified`)
VALUES (1,1,2,1,1,5,30,0,UNIX_TIMESTAMP())
ON DUPLICATE KEY UPDATE `id`=`id`;
