-- User lifecycle, cross-module and app download analytics.
-- This migration is additive. Keep visitor_id opaque and never store raw IP, token or device identifiers.

ALTER TABLE `starfree_activity_events`
  ADD COLUMN `platform` varchar(16) NOT NULL DEFAULT '' AFTER `event_type`,
  ADD COLUMN `app_version` varchar(32) NOT NULL DEFAULT '' AFTER `platform`,
  ADD COLUMN `event_source` varchar(64) NOT NULL DEFAULT '' AFTER `app_version`,
  ADD COLUMN `visitor_id` varchar(64) NOT NULL DEFAULT '' AFTER `event_source`,
  ADD KEY `idx_activity_event_source_time` (`event_type`,`event_source`,`created`),
  ADD KEY `idx_activity_visitor_time` (`visitor_id`,`created`);
