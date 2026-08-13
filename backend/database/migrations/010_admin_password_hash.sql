-- Required before the PHP admin can persist password_hash() output.
-- Apply only in an explicitly authorized migration session after backing up this table.

ALTER TABLE `starfree_admin_login`
  MODIFY COLUMN `pw` VARCHAR(255) NOT NULL;
