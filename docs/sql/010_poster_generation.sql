-- ============================================================================
-- 010_poster_generation.sql
-- 给 ai_jobs 放行「AI 海报生成」这种任务。
--
--   mysql -u campus_app -p --default-character-set=utf8mb4 campus_diy < docs/sql/010_poster_generation.sql
--
-- 背景：POST /ai/poster/generate 要求「记录来源、模型、生成时间和任务 ID」
-- （见 docs/poster-generation-api.md 的安全条款）。能记这件事的表就是 ai_jobs，
-- 但它有两道闸拦住了海报生成：
--
--   1) ck_ai_jobs_kind 只允许 generate_from_prompt / generate_from_poster /
--      component_suggestions / quality_check 四种取值，海报生成的 kind 写不进去；
--   2) project_id NOT NULL + 外键指向 projects。
--
-- 第 2 条**故意不改**。理由：把 project_id 放开成可空，等于改动了
-- ai_proposals / 页面生成那条链路的既有约束，影响面比这个功能大得多；
-- 而页面工坊本来就是在某个项目里生成海报的（CreateModal 是项目创建弹窗），
-- 所以海报生成老老实实带上 projectId 即可。接口侧把它做成了可选字段，
-- 前端现在不传也能用（见 PosterService 的注释），将来要按项目归档时再传。
--
-- 幂等：先探 information_schema，再重建约束，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- ck_ai_jobs_kind
-- 加 poster_generate。用 information_schema 判断现有定义里有没有它，
-- 避免重复执行时 DROP + ADD 一遍（虽然不影响结果，但会锁表）。
SET @has_poster := (SELECT COUNT(*)
                    FROM information_schema.CHECK_CONSTRAINTS
                    WHERE CONSTRAINT_SCHEMA = DATABASE()
                      AND CONSTRAINT_NAME = 'ck_ai_jobs_kind'
                      AND CHECK_CLAUSE LIKE '%poster_generate%');

SET @s := IF(@has_poster = 0,
  'ALTER TABLE ai_jobs DROP CHECK ck_ai_jobs_kind',
  'DO 0');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @s := IF(@has_poster = 0,
  'ALTER TABLE ai_jobs ADD CONSTRAINT ck_ai_jobs_kind CHECK (kind IN (''generate_from_prompt'', ''generate_from_poster'', ''component_suggestions'', ''quality_check'', ''poster_generate''))',
  'DO 0');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- 校验
SELECT COUNT(*) AS ck_ai_jobs_kind_已含poster_generate
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'ck_ai_jobs_kind'
  AND CHECK_CLAUSE LIKE '%poster_generate%';
