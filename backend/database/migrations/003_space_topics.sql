-- Dynamic topics reuse starfree_metas(type='tag') for names and admin editing.
-- These three tables keep dynamic relations separate from article relationships.

CREATE TABLE IF NOT EXISTS `starfree_topic_meta` (
  `mid` int(10) unsigned NOT NULL,
  `creator_uid` int(10) unsigned NOT NULL DEFAULT '0',
  `is_official` tinyint(1) unsigned NOT NULL DEFAULT '0',
  `created` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`mid`),
  KEY `idx_topic_official_created` (`is_official`, `created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dynamic topic ownership and official state';

CREATE TABLE IF NOT EXISTS `starfree_space_topics` (
  `space_id` int(10) unsigned NOT NULL,
  `mid` int(10) unsigned NOT NULL,
  `created` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`space_id`, `mid`),
  KEY `idx_space_topic_mid_space` (`mid`, `space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dynamic to topic relationships';

CREATE TABLE IF NOT EXISTS `starfree_topic_follows` (
  `uid` int(10) unsigned NOT NULL,
  `mid` int(10) unsigned NOT NULL,
  `created` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`uid`, `mid`),
  KEY `idx_topic_follow_mid_uid` (`mid`, `uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User followed dynamic topics';
