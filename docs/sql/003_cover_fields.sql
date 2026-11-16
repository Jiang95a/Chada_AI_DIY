-- =============================================================================
-- 003_cover_fields.sql
-- 给 projects 表补「卡片封面」字段。
--
-- 背景：前端 ProjectRecord 定义了 4 个封面字段，用来渲染项目列表的卡片：
--   type        '社团招新' / '投票评选' …     → activity_type
--   coverTone   'photo' / 'music' …           → cover_tone（决定卡片配色）
--   coverCode   'PHOTO / 26'                  → cover_code（卡片角标文字）
--   coverTitle  '寻找校园里的\n下一帧光'      → cover_title（卡片大标题）
-- 后端原先没存这些，列表接口只能返回纯文本标题，卡片会一片空白。
--
-- 执行顺序：00_create_database.sql → 001_init.sql → 002_seed.sql → 本文件
--           （完整的迁移顺序见 docs/sql/README.md）
--
-- 注意：campus_app 账号没有 CREATE ROUTINE 权限，所以这里不用存储过程，
-- 改用「先查 information_schema，再按需执行」的写法。
--
-- 【第一步】先跑下面这段诊断，看哪些列已经存在：
--
--   SELECT COLUMN_NAME FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projects'
--     AND COLUMN_NAME IN ('activity_type','cover_tone','cover_code','cover_title');
--
-- 【第二步】只执行「还不存在」的那些 ALTER 语句（MySQL 8 没有 ADD COLUMN IF NOT EXISTS）
-- =============================================================================

-- 【第三版】改为幂等：原先这里是四条裸 ALTER TABLE ... ADD COLUMN，
-- 重复执行会直接报 Duplicate column name 中断脚本。现在每列先查 information_schema，
-- 已存在就跳过（DO 0），缺哪列补哪列。与 004_projects_cover_columns.sql 的写法一致。
--
-- 注意列定义取的是本文件的宽松版本（VARCHAR(64) 且可空），不是 001_init.sql 里的
-- NOT NULL DEFAULT。原因：线上库就是按本文件建的，改成 NOT NULL 需要先回填存量
-- 数据，属于另一个迁移；两种定义下 Java 侧都是 String，读取行为一致。

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE projects ADD COLUMN activity_type VARCHAR(64) NULL COMMENT ''活动类型，如 社团招新/投票评选'' AFTER description',
    'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'projects' AND column_name = 'activity_type');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE projects ADD COLUMN cover_tone VARCHAR(32) NULL COMMENT ''卡片配色主题，前端 ProjectRecord.coverTone'' AFTER activity_type',
    'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'projects' AND column_name = 'cover_tone');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE projects ADD COLUMN cover_code VARCHAR(64) NULL COMMENT ''卡片角标文字，前端 ProjectRecord.coverCode'' AFTER cover_tone',
    'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'projects' AND column_name = 'cover_code');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE projects ADD COLUMN cover_title VARCHAR(160) NULL COMMENT ''卡片大标题，前端 ProjectRecord.coverTitle'' AFTER cover_code',
    'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'projects' AND column_name = 'cover_title');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 回填：已有项目按「类型 → 配色 → 角标 → 标题」的顺序补默认值，
-- 避免列表接口返回一堆 null 让卡片整体空白。
-- 配色规则跟模板 category 走，跟前端 templates.ts 里的语义对齐。
-- -----------------------------------------------------------------------------
UPDATE projects p
LEFT JOIN templates t ON t.id = p.template_id
SET
  p.activity_type = COALESCE(p.activity_type, t.name, '校园活动'),
  p.cover_tone = COALESCE(p.cover_tone, CASE COALESCE(t.category, '')
        WHEN 'recruitment'  THEN 'photo'
        WHEN 'registration' THEN 'music'
        WHEN 'vote'         THEN 'vote'
        WHEN 'checkin'      THEN 'checkin'
        ELSE 'photo' END),
  p.cover_code = COALESCE(p.cover_code, UPPER(LEFT(COALESCE(t.category, 'CAMPUS'), 6))),
  p.cover_title = COALESCE(p.cover_title, p.title)
WHERE p.deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- 便于按活动类型筛选
-- -----------------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE projects ADD KEY idx_projects_activity_type (activity_type)',
    'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'projects' AND index_name = 'idx_projects_activity_type');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'projects'
  AND COLUMN_NAME IN ('activity_type', 'cover_tone', 'cover_code', 'cover_title')
ORDER BY ORDINAL_POSITION;
