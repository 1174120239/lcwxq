CREATE TABLE IF NOT EXISTS starfree_email_broadcast (
  id BIGINT NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(100) NOT NULL,
  subject VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  created BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  total INT NOT NULL DEFAULT 0,
  success INT NOT NULL DEFAULT 0,
  failed INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_starfree_email_broadcast_request (request_id),
  KEY idx_starfree_email_broadcast_created (created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
