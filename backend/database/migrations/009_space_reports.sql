-- User reports for public dynamics. Review is available to administrator/editor staff.

CREATE TABLE IF NOT EXISTS `starfree_space_reports` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `space_id` bigint unsigned NOT NULL,
  `reporter_uid` int unsigned NOT NULL,
  `reason` varchar(40) NOT NULL,
  `detail` varchar(500) NOT NULL DEFAULT '',
  `status` tinyint unsigned NOT NULL DEFAULT '0',
  `reviewer_uid` int unsigned NOT NULL DEFAULT '0',
  `review_note` varchar(500) NOT NULL DEFAULT '',
  `created` int unsigned NOT NULL DEFAULT '0',
  `modified` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_space_reporter` (`space_id`, `reporter_uid`),
  KEY `idx_space_report_review` (`status`, `created`),
  KEY `idx_space_report_target` (`space_id`, `status`),
  KEY `idx_space_report_reporter` (`reporter_uid`, `created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User reports and staff review audit for dynamics';
