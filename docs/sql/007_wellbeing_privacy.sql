-- ============================================================================
-- 007_wellbeing_privacy.sql
--
-- 心屿心理社团平台：让"匿名 / 知情同意 / 信息保留期限"从前端 localStorage
-- 变成服务端能证明的事实。
--
-- 幂等：可以重复执行。用 information_schema 判断列 / 表是否存在，
-- 不依赖 MySQL 8.0.29+ 才有的 ADD COLUMN IF NOT EXISTS。
--
-- 目标库：campus_diy（MySQL 8.0）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. projects.privacy_mode —— 把隐私档位冗余成一列，方便公开列表 / 统计直接过滤
-- ----------------------------------------------------------------------------
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE projects ADD COLUMN privacy_mode VARCHAR(16) NOT NULL DEFAULT ''optional'' AFTER activity_type',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projects' AND COLUMN_NAME = 'privacy_mode'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. form_submissions 的隐私快照
--    为什么要快照：活动发布时的隐私设置可能在事后被改。
--    报名那一刻适用的是哪套规则，必须原样留在报名记录上，否则"我知道的信息"
--    和"当初被告知的信息"对不上，那就不是知情同意了。
-- ----------------------------------------------------------------------------
SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE form_submissions ADD COLUMN consent_id CHAR(36) NULL AFTER idempotency_key',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions' AND COLUMN_NAME = 'consent_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE form_submissions ADD COLUMN privacy_mode_snapshot VARCHAR(16) NULL AFTER consent_id',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions' AND COLUMN_NAME = 'privacy_mode_snapshot'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE form_submissions ADD COLUMN retention_days_snapshot INT NULL AFTER privacy_mode_snapshot',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions' AND COLUMN_NAME = 'retention_days_snapshot'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE form_submissions ADD COLUMN anonymous TINYINT(1) NOT NULL DEFAULT 0 AFTER retention_days_snapshot',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions' AND COLUMN_NAME = 'anonymous'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 匿名编号：现场签到、组织者沟通都只看这个，不暴露 submitter_hash
SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE form_submissions ADD COLUMN anonymous_token VARCHAR(32) NULL AFTER anonymous',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions' AND COLUMN_NAME = 'anonymous_token'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 到期清除时间。NULL = 这条不走自动清理（老数据保持原样）
SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE form_submissions ADD COLUMN purge_after DATETIME NULL AFTER anonymous_token',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions' AND COLUMN_NAME = 'purge_after'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE form_submissions ADD INDEX idx_submissions_purge (purge_after)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions' AND INDEX_NAME = 'idx_submissions_purge'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3. activity_consents —— 知情同意记录
--    这张表是"匿名是真的"这条主张的物证：
--      * 存的是同意文本的 sha256，不是正文（正文在快照里，改一个字 hash 就变）
--      * 存 submitter_hash 而不是明文身份；匿名活动连 hash 都不落
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity_consents (
  id               CHAR(36)      NOT NULL,
  project_id       CHAR(36)      NOT NULL,
  version_id       CHAR(36)      NULL,
  consent_version  VARCHAR(16)   NOT NULL DEFAULT 'v1',
  consent_text     VARCHAR(1000) NOT NULL,
  consent_hash     CHAR(64)      NOT NULL,
  privacy_mode     VARCHAR(16)   NOT NULL DEFAULT 'optional',
  retention_days   INT           NULL,
  submitter_hash   CHAR(64)      NULL,
  accepted         TINYINT(1)    NOT NULL DEFAULT 1,
  accepted_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  purge_after      DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_consent_project (project_id, accepted_at),
  KEY idx_consent_purge (purge_after),
  CONSTRAINT fk_consent_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 4. activity_feedback —— 活动结束后的心情反馈（以前只存 localStorage）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity_feedback (
  id               CHAR(36)      NOT NULL,
  project_id       CHAR(36)      NOT NULL,
  choice           VARCHAR(32)   NOT NULL,
  score            INT           NULL,
  comment          VARCHAR(500)  NULL,
  anonymous        TINYINT(1)    NOT NULL DEFAULT 1,
  submitter_hash   CHAR(64)      NULL,
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  purge_after      DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_feedback_project (project_id, created_at),
  KEY idx_feedback_choice (project_id, choice),
  KEY idx_feedback_purge (purge_after),
  CONSTRAINT fk_feedback_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 5. user_notifications —— 工作台铃铛（前端目前是两条写死的假数据）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_notifications (
  id               CHAR(36)      NOT NULL,
  user_id          CHAR(36)      NOT NULL,
  type             VARCHAR(32)   NOT NULL,
  title            VARCHAR(200)  NOT NULL,
  detail           VARCHAR(500)  NULL,
  target_path      VARCHAR(200)  NULL,
  dedupe_key       VARCHAR(100)  NULL,
  read_at          DATETIME      NULL,
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_dedupe (user_id, dedupe_key),
  KEY idx_notification_user (user_id, created_at),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 6. 回填：把老数据补成"不匿名、无到期"的形态，保持原样可读
-- ----------------------------------------------------------------------------
UPDATE projects
   SET privacy_mode = 'optional'
 WHERE privacy_mode IS NULL OR privacy_mode = '';

UPDATE form_submissions
   SET anonymous = 0
 WHERE anonymous IS NULL;

-- ----------------------------------------------------------------------------
-- 7. 自检
-- ----------------------------------------------------------------------------
SELECT 'projects.privacy_mode' AS item, COUNT(*) AS present FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projects' AND COLUMN_NAME = 'privacy_mode'
UNION ALL
SELECT 'form_submissions 隐私六列', COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions'
   AND COLUMN_NAME IN ('consent_id', 'privacy_mode_snapshot', 'retention_days_snapshot',
                       'anonymous', 'anonymous_token', 'purge_after')
UNION ALL
SELECT '新表数量', COUNT(*) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('activity_consents', 'activity_feedback', 'user_notifications');
