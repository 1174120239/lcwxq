-- Durable idempotency journal for every replacement-side balance operation.
--
-- The legacy balance, paylog, and userlog tables are MyISAM. This InnoDB table
-- records the request before any MyISAM change and stores the committed result
-- before the API responds. It is intentionally separate from official payment
-- tables: payment creation and callbacks continue to use the legacy backend.
CREATE TABLE IF NOT EXISTS `starfree_economy_operations` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `operation_key` varchar(191) NOT NULL,
  `operation_type` varchar(64) NOT NULL,
  `state` varchar(24) NOT NULL DEFAULT 'started',
  `actor_uid` int(10) unsigned NOT NULL DEFAULT '0',
  `target_uid` int(10) unsigned NOT NULL DEFAULT '0',
  `reference_id` bigint(20) NOT NULL DEFAULT '0',
  `payload_json` text,
  `result_json` text,
  `last_error` varchar(1000) DEFAULT NULL,
  `created` int(10) unsigned NOT NULL,
  `updated` int(10) unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `operation_key` (`operation_key`),
  KEY `state_updated` (`state`,`updated`),
  KEY `actor_created` (`actor_uid`,`created`),
  KEY `reference` (`operation_type`,`reference_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Replacement economy idempotency and recovery journal';
