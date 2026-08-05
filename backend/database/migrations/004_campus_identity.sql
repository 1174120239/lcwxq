-- Campus and admission-year options are stored by stable id so renames are reflected
-- consistently for both existing users and new registrations. Used values are disabled,
-- never hard-deleted.

CREATE TABLE IF NOT EXISTS `starfree_identity_options` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `type` varchar(16) NOT NULL,
  `name` varchar(40) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT '0',
  `enabled` tinyint(1) unsigned NOT NULL DEFAULT '1',
  `created` int(10) unsigned NOT NULL DEFAULT '0',
  `modified` int(10) unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_type_name` (`type`, `name`),
  KEY `idx_identity_type_enabled_sort` (`type`, `enabled`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Campus and admission-year registration options';

ALTER TABLE `starfree_users`
  ADD COLUMN `campus_option_id` int(10) unsigned NULL DEFAULT NULL AFTER `local`,
  ADD COLUMN `grade_option_id` int(10) unsigned NULL DEFAULT NULL AFTER `campus_option_id`,
  ADD KEY `idx_users_campus_option` (`campus_option_id`),
  ADD KEY `idx_users_grade_option` (`grade_option_id`);

INSERT IGNORE INTO `starfree_identity_options`
  (`type`, `name`, `sort_order`, `enabled`, `created`, `modified`)
VALUES
  ('campus', '东校区', 20, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
  ('campus', '西校区', 10, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
  ('grade', '2024级', 20, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
  ('grade', '2023级', 10, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP());
