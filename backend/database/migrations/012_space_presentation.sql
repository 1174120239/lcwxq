-- Dynamic presentation controls: featured, list pin and banner pin.
-- Additive columns keep the retained legacy API compatible with starfree_space.

SET @space_schema = DATABASE();

SET @space_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @space_schema AND TABLE_NAME = 'starfree_space'
     AND COLUMN_NAME = 'featured') = 0,
  'ALTER TABLE `starfree_space` ADD COLUMN `featured` tinyint unsigned NOT NULL DEFAULT 0 AFTER `onlyMe`',
  'SELECT 1'
);
PREPARE space_statement FROM @space_ddl;
EXECUTE space_statement;
DEALLOCATE PREPARE space_statement;
SET @space_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @space_schema AND TABLE_NAME = 'starfree_space'
     AND COLUMN_NAME = 'pin_type') = 0,
  'ALTER TABLE `starfree_space` ADD COLUMN `pin_type` tinyint unsigned NOT NULL DEFAULT 0 AFTER `featured`',
  'SELECT 1'
);
PREPARE space_statement FROM @space_ddl;
EXECUTE space_statement;
DEALLOCATE PREPARE space_statement;

SET @space_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @space_schema AND TABLE_NAME = 'starfree_space'
     AND COLUMN_NAME = 'pin_order') = 0,
  'ALTER TABLE `starfree_space` ADD COLUMN `pin_order` int unsigned NOT NULL DEFAULT 0 AFTER `pin_type`',
  'SELECT 1'
);
PREPARE space_statement FROM @space_ddl;
EXECUTE space_statement;
DEALLOCATE PREPARE space_statement;

SET @space_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @space_schema AND TABLE_NAME = 'starfree_space'
     AND COLUMN_NAME = 'pin_start') = 0,
  'ALTER TABLE `starfree_space` ADD COLUMN `pin_start` bigint unsigned NOT NULL DEFAULT 0 AFTER `pin_order`',
  'SELECT 1'
);
PREPARE space_statement FROM @space_ddl;
EXECUTE space_statement;
DEALLOCATE PREPARE space_statement;

SET @space_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @space_schema AND TABLE_NAME = 'starfree_space'
     AND COLUMN_NAME = 'pin_end') = 0,
  'ALTER TABLE `starfree_space` ADD COLUMN `pin_end` bigint unsigned NOT NULL DEFAULT 0 AFTER `pin_start`',
  'SELECT 1'
);
PREPARE space_statement FROM @space_ddl;
EXECUTE space_statement;
DEALLOCATE PREPARE space_statement;

SET @space_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = @space_schema AND TABLE_NAME = 'starfree_space'
     AND INDEX_NAME = 'idx_space_presentation') = 0,
  'ALTER TABLE `starfree_space` ADD KEY `idx_space_presentation` (`status`,`onlyMe`,`pin_type`,`pin_order`,`pin_end`)',
  'SELECT 1'
);
PREPARE space_statement FROM @space_ddl;
EXECUTE space_statement;
DEALLOCATE PREPARE space_statement;
