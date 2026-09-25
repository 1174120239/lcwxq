-- Independent Campus Editions (见字) domain. It intentionally does not reference
-- starfree_contents or starfree_metas: journals are editorial publications, not forum posts.
CREATE TABLE IF NOT EXISTS `starfree_journals` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(80) NOT NULL,
  `slug` varchar(100) NOT NULL DEFAULT '',
  `description` varchar(500) NOT NULL DEFAULT '',
  `tags` varchar(500) NOT NULL DEFAULT '',
  `cover_url` varchar(500) NOT NULL DEFAULT '',
  `banner_url` varchar(500) NOT NULL DEFAULT '',
  `theme` varchar(32) NOT NULL DEFAULT 'editorial',
  `status` tinyint unsigned NOT NULL DEFAULT 1,
  `sort_order` int NOT NULL DEFAULT 0,
  `featured` tinyint unsigned NOT NULL DEFAULT 0,
  `hot_weight` int NOT NULL DEFAULT 0,
  `created_by` int unsigned NOT NULL DEFAULT 0,
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_journal_slug` (`slug`),
  KEY `idx_journal_public` (`status`,`featured`,`sort_order`,`modified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Independent Campus Editions journals';

CREATE TABLE IF NOT EXISTS `starfree_journal_articles` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `journal_id` bigint unsigned NOT NULL,
  `author_uid` int unsigned NOT NULL,
  `title` varchar(200) NOT NULL,
  `subtitle` varchar(500) NOT NULL DEFAULT '',
  `summary` varchar(1000) NOT NULL DEFAULT '',
  `body_markdown` mediumtext NOT NULL,
  `cover_url` varchar(500) NOT NULL DEFAULT '',
  `layout_preset` varchar(32) NOT NULL DEFAULT 'classic',
  `theme_preset` varchar(32) NOT NULL DEFAULT 'paper',
  `status` varchar(16) NOT NULL DEFAULT 'submitted' COMMENT 'draft,submitted,published,rejected,hidden,deleted',
  `review_reason` varchar(500) NOT NULL DEFAULT '',
  `recommended` tinyint unsigned NOT NULL DEFAULT 0,
  `sort_order` int NOT NULL DEFAULT 0,
  `views` int unsigned NOT NULL DEFAULT 0,
  `likes` int unsigned NOT NULL DEFAULT 0,
  `dislikes` int unsigned NOT NULL DEFAULT 0,
  `comments` int unsigned NOT NULL DEFAULT 0,
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  `published_at` bigint unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_journal_article_public` (`journal_id`,`status`,`recommended`,`sort_order`,`published_at`),
  KEY `idx_journal_article_author` (`author_uid`,`status`,`modified`),
  KEY `idx_journal_article_hot` (`journal_id`,`status`,`likes`,`views`,`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Independent Campus Editions articles';

CREATE TABLE IF NOT EXISTS `starfree_journal_votes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `article_id` bigint unsigned NOT NULL,
  `uid` int unsigned NOT NULL,
  `vote` tinyint NOT NULL COMMENT '1 like, -1 dislike',
  `created` bigint unsigned NOT NULL,
  `modified` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_journal_vote` (`article_id`,`uid`),
  KEY `idx_journal_vote_user` (`uid`,`modified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-user journal article reactions';

CREATE TABLE IF NOT EXISTS `starfree_journal_actions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `article_id` bigint unsigned NOT NULL,
  `operator_uid` int unsigned NOT NULL,
  `from_status` varchar(16) NOT NULL,
  `to_status` varchar(16) NOT NULL,
  `action` varchar(32) NOT NULL,
  `reason` varchar(500) NOT NULL DEFAULT '',
  `created` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_journal_action_article` (`article_id`,`created`),
  KEY `idx_journal_action_operator` (`operator_uid`,`created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Journal moderation audit log';
