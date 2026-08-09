-- Campus Q&A is intentionally separate from article and dynamic data.
-- Questions are staff-managed; ordinary users can answer and discuss answers.

CREATE TABLE IF NOT EXISTS `starfree_qa_questions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `title` varchar(160) NOT NULL,
  `description` text NULL,
  `topic` varchar(80) NOT NULL DEFAULT '',
  `cover_url` varchar(500) NOT NULL DEFAULT '',
  `status` tinyint unsigned NOT NULL DEFAULT '1',
  `recommended` tinyint unsigned NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_by` int unsigned NOT NULL DEFAULT '0',
  `created` int unsigned NOT NULL DEFAULT '0',
  `modified` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_qa_question_public` (`status`, `recommended`, `sort_order`, `modified`),
  KEY `idx_qa_question_creator` (`created_by`, `created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Staff-managed campus questions';

CREATE TABLE IF NOT EXISTS `starfree_qa_answers` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `question_id` bigint unsigned NOT NULL,
  `uid` int unsigned NOT NULL,
  `text` text NOT NULL,
  `likes` int unsigned NOT NULL DEFAULT '0',
  `status` tinyint unsigned NOT NULL DEFAULT '1',
  `created` int unsigned NOT NULL DEFAULT '0',
  `modified` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_qa_answer_question` (`question_id`, `status`, `created`),
  KEY `idx_qa_answer_user` (`uid`, `status`, `created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Campus question answers';

CREATE TABLE IF NOT EXISTS `starfree_qa_answer_likes` (
  `answer_id` bigint unsigned NOT NULL,
  `uid` int unsigned NOT NULL,
  `created` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`answer_id`, `uid`),
  KEY `idx_qa_answer_like_user` (`uid`, `created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Answer likes';

CREATE TABLE IF NOT EXISTS `starfree_qa_comments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `answer_id` bigint unsigned NOT NULL,
  `uid` int unsigned NOT NULL,
  `parent_id` bigint unsigned NOT NULL DEFAULT '0',
  `root_id` bigint unsigned NOT NULL DEFAULT '0',
  `text` varchar(1000) NOT NULL,
  `status` tinyint unsigned NOT NULL DEFAULT '1',
  `created` int unsigned NOT NULL DEFAULT '0',
  `modified` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_qa_comment_answer` (`answer_id`, `status`, `created`),
  KEY `idx_qa_comment_root` (`root_id`, `status`, `created`),
  KEY `idx_qa_comment_user` (`uid`, `status`, `created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Answer comments and replies';
