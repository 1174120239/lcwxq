-- Anonymous dynamics support, ported from the ng_music StarPro plugin by user request.
-- Anonymous dynamics are normal starfree_space rows whose uid is a dedicated
-- anonymous account; this migration only owns the mapping and the operator config.

CREATE TABLE IF NOT EXISTS `starfree_anonymous_posts` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `uid` int(11) unsigned NOT NULL COMMENT '真实发布用户UID',
  `sid` int(11) unsigned NOT NULL COMMENT '匿名动态ID',
  `created` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_anonymous_uid_sid` (`uid`, `sid`),
  KEY `idx_anonymous_sid` (`sid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匿名动态归属映射（不对外泄露）';

CREATE TABLE IF NOT EXISTS `starfree_anonymous_config` (
  `id` tinyint(3) unsigned NOT NULL DEFAULT '1',
  `fid` int(11) unsigned NOT NULL DEFAULT '0' COMMENT '匿名发布账号UID，0=未启用匿名动态',
  `review` tinyint(1) unsigned NOT NULL DEFAULT '0' COMMENT '1=匿名动态进入待审核，0=直接发布',
  `created` int(10) unsigned NOT NULL DEFAULT '0',
  `modified` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匿名动态配置';

INSERT IGNORE INTO `starfree_anonymous_config`
  (`id`, `fid`, `review`, `created`, `modified`)
VALUES
  (1, 0, 0, UNIX_TIMESTAMP(), UNIX_TIMESTAMP());
