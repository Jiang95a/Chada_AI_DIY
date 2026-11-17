-- 013_hide_general_templates.sql
-- 心屿同行：模板库只保留心理活动 / 生涯场景。
-- 通用模板（摄影评选、比赛报名、志愿者招募、社团招新等）不再出现在新建流程或模板库。
-- 采用停用而不是删除，已有项目文档不受影响。
--
-- ⚠️ 本文件必须**最后**执行。
--   003_seed_fix.sql 的 ON DUPLICATE 分支里写的是 is_active = 1，
--   所以只要之后重跑了 003_seed_fix（例如按 README「旧库全部重跑一遍」），
--   那四套通用模板就会被重新启用，这里的效果被抵消。
--   重跑过 003_seed_fix，就把本文件再跑一次。

UPDATE templates
SET is_active = 0
WHERE activity_kind = 'general';
