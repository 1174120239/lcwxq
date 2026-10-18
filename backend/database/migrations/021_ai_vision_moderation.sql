-- Gallery review and image-aware moderation for gallery and dynamic posts.
-- Text-only configured scopes keep working when vision is disabled.

SET @ai_schema = DATABASE();

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'gallery_enabled') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `gallery_enabled` tinyint unsigned NOT NULL DEFAULT 1 AFTER `comment_enabled`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'vision_enabled') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `vision_enabled` tinyint unsigned NOT NULL DEFAULT 1 AFTER `gallery_enabled`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;

SET @ai_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @ai_schema AND TABLE_NAME = 'starfree_ai_moderation_config'
     AND COLUMN_NAME = 'vision_model') = 0,
  'ALTER TABLE `starfree_ai_moderation_config` ADD COLUMN `vision_model` varchar(80) NOT NULL DEFAULT ''deepseek-flash'' AFTER `model`',
  'SELECT 1'
);
PREPARE ai_statement FROM @ai_ddl;
EXECUTE ai_statement;
DEALLOCATE PREPARE ai_statement;
