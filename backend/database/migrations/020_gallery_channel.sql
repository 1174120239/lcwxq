-- Standalone visual gallery channel, independent from forum dynamics and posts.
CREATE TABLE IF NOT EXISTS `starfree_gallery_items` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `author_uid` int unsigned NOT NULL,
  `title` varchar(120) NOT NULL,
  `description` varchar(2000) NOT NULL DEFAULT '',
  `image_urls` mediumtext NOT NULL,
  `image_ratio` decimal(7,4) NOT NULL DEFAULT 0.8000,
  `tags` varchar(500) NOT NULL DEFAULT '',
  `status` varchar(16) NOT NULL DEFAULT 'submitted' COMMENT 'submitted,published,rejected,hidden,deleted',
  `review_reason` varchar(500) NOT NULL DEFAULT '',
  `views` int unsigned NOT NULL DEFAULT 0,
  `sort_order` int NOT NULL DEFAULT 0,
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  `published_at` bigint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_gallery_public` (`status`,`sort_order`,`published_at`,`id`),
  KEY `idx_gallery_author` (`author_uid`,`status`,`modified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Independent campus image gallery';

CREATE TABLE IF NOT EXISTS `starfree_gallery_actions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `gallery_id` bigint unsigned NOT NULL,
  `operator_uid` int unsigned NOT NULL,
  `operator_name` varchar(64) NOT NULL DEFAULT '',
  `from_status` varchar(16) NOT NULL,
  `to_status` varchar(16) NOT NULL,
  `action` varchar(32) NOT NULL,
  `reason` varchar(500) NOT NULL DEFAULT '',
  `created` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_gallery_action_item` (`gallery_id`,`created`),
  KEY `idx_gallery_action_operator` (`operator_uid`,`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Gallery moderation audit log';
