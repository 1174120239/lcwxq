-- Independent download/introduction site configuration.
-- Apply only after backing up the shared database; this migration is additive.

CREATE TABLE IF NOT EXISTS `lcxqy_download_site_config` (
  `id` TINYINT UNSIGNED NOT NULL DEFAULT 1,
  `hero_kicker` VARCHAR(120) NOT NULL DEFAULT '聊城一中 · 校园社区',
  `hero_title` VARCHAR(255) NOT NULL DEFAULT '让校园里的每一次连接都有回响',
  `hero_intro` TEXT NOT NULL,
  `web_url` VARCHAR(1000) NOT NULL DEFAULT 'https://prev.lcxqy.cn/',
  `cors_origins` TEXT NOT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Independent download site public configuration';

INSERT INTO `lcxqy_download_site_config`
  (`id`,`hero_kicker`,`hero_title`,`hero_intro`,`web_url`,`cors_origins`)
VALUES
  (1,
   '聊城一中 · 校园社区',
   '让校园里的每一次连接都有回响',
   '在这里，分享动态、发现同好、互相帮助。聊城一中论坛，把真实的校园生活留在同学们共同的空间里。',
   'https://prev.lcxqy.cn/',
   'https://prev.lcxqy.cn\nhttps://lcyz.site\nhttps://www.lcyz.site')
ON DUPLICATE KEY UPDATE `id`=`id`;
