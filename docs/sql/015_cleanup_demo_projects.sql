-- ============================================================================
-- 015_cleanup_demo_projects.sql —— 收尾：只留心理活动，通用演示项目软删
--
-- 背景：
--   005_demo_data.sql 建了四场「通用」演示活动（摄影社招新 / 歌手大赛 / 阅读打卡 /
--   志愿者招募），它们是平台做成「什么活动都能办」阶段的产物。
--   现在产品定位已经收敛到校园心理社团，013 也把通用模板停用了，
--   但这四场活动仍躺在演示账号名下——评委登录后第一眼看到的是摄影社，
--   与「心理社团平台」的定位直接冲突。
--
-- 做三件事：
--   1) 修 published_version_id 的悬空引用（见下方说明）
--   2) 软删那四场通用演示活动（deleted_at，不真删）
--   3) 给「考试周心晴游园会」补 8 条提交，让「活动参与数据」页对评委不是空的
--
-- 关于第 1 条：014 里 page_versions 是按 slug 反查 project_id 插入的，
-- 若库里已有该项目的 version_no=1，插入会走 ON DUPLICATE 更新、主键仍是旧版本，
-- 而 014 末尾把 published_version_id 写成了固定的新 UUID —— 那个版本行并不存在。
-- 公开页因为 publishedDocument() 有「取不到版本就回退草稿」的兜底，所以看不出问题，
-- 但这终究是个悬空外键。这里统一指回真实存在的版本行，可重复执行。
--
-- 幂等：全部为 UPDATE / INSERT ... ON DUPLICATE，重复执行安全。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 1) 修版本链接
UPDATE projects p
   SET p.published_version_id = (
         SELECT v.id FROM page_versions v
          WHERE v.project_id = p.id
          ORDER BY v.version_no DESC LIMIT 1)
 WHERE p.public_slug IN ('campus-fair', 'wellbeing-quiz', 'mind-education', 'peer-support');

-- ---------------------------------------------------------------- 2) 软删通用演示项目
-- 不真删：form_submissions / page_versions 都是 ON DELETE CASCADE，
-- 真删会连带抹掉全部演示提交，且无法恢复。软删后所有查询都会过滤 deleted_at。
UPDATE projects
   SET deleted_at = NOW()
 WHERE public_slug IN ('photo-club-2026', 'voice-vote', 'reading-plan', 'volunteer-2026')
   AND deleted_at IS NULL;

-- ---------------------------------------------------------------- 3) 游园会的演示提交
-- version_id 用子查询取该项目最新版本，避免写死 UUID 再次制造悬空外键。
-- page_id / form_component_id 取自 014 里那份文档的「报名」页与其表单区块。
INSERT INTO form_submissions
  (id, project_id, version_id, page_id, form_component_id,
   answers, idempotency_key, submitter_hash, status, submitted_at)
SELECT v.id, p.id, p.published_version_id, 'page_mwktgo04101ny5a', 'blk_mwktgo04vvyv6u',
       v.answers, v.idem, SHA2(v.idem, 256), 'valid', DATE_SUB(NOW(), INTERVAL v.hours HOUR)
  FROM projects p
  JOIN (
    SELECT 'c1000000-0000-4000-8000-000000000001' AS id,
           'c2000000-0000-4000-8000-000000000001' AS idem,
           '{"怎么称呼你":"小林","联系方式":"lin@example.com","是否匿名参加":"匿名参加","想说的话":"想找个安静一点的角落"}' AS answers,
           6 AS hours
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000002', 'c2000000-0000-4000-8000-000000000002',
           '{"怎么称呼你":"阿泽","联系方式":"ze@example.com","是否匿名参加":"匿名参加","想说的话":""}', 11
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000003', 'c2000000-0000-4000-8000-000000000003',
           '{"怎么称呼你":"周周","联系方式":"zhou@example.com","是否匿名参加":"可以留称呼","想说的话":"想学黏土那摊"}', 26
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000004', 'c2000000-0000-4000-8000-000000000004',
           '{"怎么称呼你":"匿名同学","联系方式":"anon@example.com","是否匿名参加":"匿名参加","想说的话":"只想听歌，不打算说话"}', 32
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000005', 'c2000000-0000-4000-8000-000000000005',
           '{"怎么称呼你":"悦悦","联系方式":"yue@example.com","是否匿名参加":"可以留称呼","想说的话":"想给未来的自己写张便签"}', 48
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000006', 'c2000000-0000-4000-8000-000000000006',
           '{"怎么称呼你":"小满","联系方式":"man@example.com","是否匿名参加":"匿名参加","想说的话":"考试周有点撑不住，想找人说说话"}', 55
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000007', 'c2000000-0000-4000-8000-000000000007',
           '{"怎么称呼你":"阿原","联系方式":"yuan@example.com","是否匿名参加":"匿名参加","想说的话":""}', 70
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000008', 'c2000000-0000-4000-8000-000000000008',
           '{"怎么称呼你":"橙子","联系方式":"cheng@example.com","是否匿名参加":"可以留称呼","想说的话":"想找人一起涂鸦那面墙"}', 92
  ) AS v
 WHERE p.public_slug = 'campus-fair'
   AND p.published_version_id IS NOT NULL
ON DUPLICATE KEY UPDATE id = VALUES(id);

-- 自查：
--   SELECT public_slug, deleted_at IS NULL AS 在用 FROM projects WHERE public_slug LIKE '%-2026' OR public_slug IN ('voice-vote','reading-plan');
--   -- 期望这四行「在用」都是 0
--   SELECT COUNT(*) FROM form_submissions WHERE project_id = (SELECT id FROM projects WHERE public_slug='campus-fair');
--   -- 新库期望 8
