-- Ordered multi-image support for questions and campus mutual-aid notices.
-- Legacy cover/image fields remain the first image for existing clients.

SET @publish_schema = DATABASE();

SET @publish_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@publish_schema
   AND TABLE_NAME='starfree_qa_questions' AND COLUMN_NAME='image_urls') = 0,
  'ALTER TABLE `starfree_qa_questions` ADD COLUMN `image_urls` text NULL AFTER `cover_url`',
  'SELECT 1'
);
PREPARE publish_statement FROM @publish_ddl;
EXECUTE publish_statement;
DEALLOCATE PREPARE publish_statement;

SET @publish_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@publish_schema
   AND TABLE_NAME='starfree_lost_found_items' AND COLUMN_NAME='image_urls') = 0,
  'ALTER TABLE `starfree_lost_found_items` ADD COLUMN `image_urls` text NULL AFTER `image_url`',
  'SELECT 1'
);
PREPARE publish_statement FROM @publish_ddl;
EXECUTE publish_statement;
DEALLOCATE PREPARE publish_statement;
