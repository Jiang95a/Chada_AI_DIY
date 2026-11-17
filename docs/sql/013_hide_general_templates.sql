-- 013_hide_general_templates.sql
-- 心屿同行：模板库只保留心理活动 / 生涯场景。
-- 通用模板（摄影评选、比赛报名、志愿者招募、社团招新等）不再出现在新建流程或模板库。
-- 采用停用而不是删除，已有项目文档不受影响。

UPDATE templates
SET is_active = 0
WHERE activity_kind = 'general';
