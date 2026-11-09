-- ============================================================================
-- 009_career_and_referral.sql
-- 方案 B：心理社团 + 生涯规划，以及隐私做深后的两个闭环。
--
--   mysql -u campus_app -p --default-character-set=utf8mb4 campus_diy < docs/sql/009_career_and_referral.sql
--
-- 这个脚本做四件事：
--   1) templates 加 activity_kind（wellbeing / career / general）
--      —— 解决「心理知识问答」和「生涯知识测试」category 都是 vote、
--         前端无法区分的问题。category 的 CHECK 不动，四类照旧。
--   2) 新建 referral_requests（转介工单）
--      —— 参与者勾「希望有人联系我」，服务端只记「需要联系」这个事实，
--         不记他说了什么，也不帮他记身份。
--   3) form_submissions 加 user_id / project_id 两份冗余，支撑成长档案与组织者待办
--      —— user_id 是参与者本人登录时的 id（匿名档也写：给「我参加过什么」用，
--         不给组织者看名单用；组织者查询一律走 submissions 接口，那里不含 user_id）。
--   4) 补 4 个生涯规划模板。
--
-- 幂等：全部用 information_schema 判断后 ALTER / CREATE TABLE IF NOT EXISTS /
--       INSERT ... ON DUPLICATE KEY UPDATE，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 1) templates.activity_kind
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'templates'
             AND COLUMN_NAME = 'activity_kind');
SET @s := IF(@c = 0,
  'ALTER TABLE templates ADD COLUMN activity_kind VARCHAR(16) NOT NULL DEFAULT ''general'' AFTER category',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 心理类四套（008 建的）标记为 wellbeing；老模板保持 general 之外的心理语义，
-- 这里只给明确属于心理的几条打上标签，其余留给 general。
UPDATE templates SET activity_kind = 'wellbeing'
 WHERE code IN ('wellbeing-club-recruit', 'wellbeing-quiz',
                'wellbeing-event', 'wellbeing-selfcare-checkin');

-- 索引：前端按 kind 分栏取模板
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'templates'
             AND INDEX_NAME = 'idx_templates_kind');
SET @s := IF(@c = 0,
  'ALTER TABLE templates ADD KEY idx_templates_kind (activity_kind, category, is_active)',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- CHECK 约束（MySQL 8.0.16+ 支持；旧版本会报错，可注释掉这三行）
SET @c := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'templates'
             AND CONSTRAINT_NAME = 'ck_templates_kind');
SET @s := IF(@c = 0,
  'ALTER TABLE templates ADD CONSTRAINT ck_templates_kind CHECK (activity_kind IN (''wellbeing'', ''career'', ''general''))',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- 2) 转介工单
CREATE TABLE IF NOT EXISTS referral_requests (
  id             CHAR(36)     NOT NULL,
  project_id     CHAR(36)     NOT NULL,
  -- 参与者本人（登录时才有）；匿名档也写，但组织者接口从不返回它
  user_id        CHAR(36)     NULL,
  -- 参与者自填的联系方式（可空）。组织者只能看到这个，看不到报名答案
  contact        VARCHAR(40)  NULL,
  preferred_time VARCHAR(60)  NULL,
  -- pending → contacted → closed；由组织者推进
  status         VARCHAR(16)  NOT NULL DEFAULT 'pending',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at     DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_referral_project_status (project_id, status, created_at),
  KEY idx_referral_created (created_at),
  CONSTRAINT fk_referral_project FOREIGN KEY (project_id)
    REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT ck_referral_status CHECK (status IN ('pending', 'contacted', 'closed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 3) form_submissions 补两列
-- user_id：成长档案用（本人查询自己的参与轨迹）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions'
             AND COLUMN_NAME = 'user_id');
SET @s := IF(@c = 0,
  'ALTER TABLE form_submissions ADD COLUMN user_id CHAR(36) NULL AFTER project_id',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_submissions'
             AND INDEX_NAME = 'idx_submissions_user');
SET @s := IF(@c = 0,
  'ALTER TABLE form_submissions ADD KEY idx_submissions_user (user_id, submitted_at)',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- 4) 生涯规划模板
-- 注意：category 只能是 recruitment / registration / vote / checkin 四类（001 的 CHECK），
--       activity_kind 才是区分心理 / 生涯的那个字段。
--       pages/blocks 不写 id，由 ProjectService.fillIds() 实例化时补齐。

-- 生涯规划 · 简历门诊
INSERT INTO templates (id, code, name, category, activity_kind, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000301',
  'career-resume-clinic',
  '简历门诊',
  'registration',
  'career',
  '生涯规划：简历门诊预约。活动说明 + 可预约时段 + 预约表 + 预约成功页，不推荐写成绩和隐私信息',
  JSON_OBJECT(
    'intent', '简历门诊预约',
    'reason', '模板：简历门诊的预约结构，含不收集敏感信息的提示',
    'theme', JSON_OBJECT(
      'primary', '#2F5D8C', 'accent', '#8FB3D9', 'bg', '#F5F8FC', 'text', '#182533',
      'radius', 14, 'fontScale', 1, 'spacing', 1.05, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '秋季简历门诊 · 一对一改简历',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '职业发展中心')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '把简历改到能投出去', 'subtitle', '一对一 30 分钟 · 免费 · 每周三下午')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '谁来帮你改', 'text', '由有招聘经验的企业 HR 和往届学长学姐担任志愿导师')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '可以带什么', 'text', '一份现有简历（没有也可以），以及你想投递的岗位名称')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '在哪', 'text', '就业指导中心 2 楼 208 室')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '预约一个时段'))
        )),
      JSON_OBJECT('name', '改什么', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '一次门诊，改这几件事')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '结构与篇幅', 'text', '压缩到一页，把最相关的经历放到最前面')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '经历怎么写', 'text', '把「参与了 XX」改成「做了什么、结果如何」')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '岗位匹配', 'text', '按目标岗位的关键词重排经历顺序')),
          JSON_OBJECT('type', 'divider', 'props', JSON_OBJECT()),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '我们不收集你的成绩单、身份证号和家庭情况。简历请自带，不要上传到本页面。'))
        )),
      JSON_OBJECT('name', '预约表', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '选一个时段')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '预约后若无法到场，请提前一天在通知里回复，把名额让给同学。')),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '预约',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'name', 'label', '姓名或昵称', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'major', 'label', '专业与年级', 'type', 'text', 'required', false),
              JSON_OBJECT('id', 'target', 'label', '想投递的岗位', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'slot', 'label', '希望的时间段', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '周三 14:00-14:30', 'value', 'w1'),
                  JSON_OBJECT('label', '周三 14:40-15:10', 'value', 'w2'),
                  JSON_OBJECT('label', '周三 15:20-15:50', 'value', 'w3'),
                  JSON_OBJECT('label', '周四 14:00-14:30', 'value', 't1')
                )),
              JSON_OBJECT('id', 'notes', 'label', '想重点解决的问题（可选）', 'type', 'textarea', 'required', false)
            )))
        )),
      JSON_OBJECT('name', '预约成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '预约成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '导师会在活动前一天通过站内通知确认时间段。')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '记得带上你现在的简历。没有简历也没关系，我们会从零开始搭框架。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description),
  name = VALUES(name), activity_kind = VALUES(activity_kind);

-- 生涯规划 · 模拟面试工作坊
INSERT INTO templates (id, code, name, category, activity_kind, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000302',
  'career-mock-interview',
  '模拟面试工作坊',
  'registration',
  'career',
  '生涯规划：模拟面试报名。流程说明 + 常见问题 + 报名表 + 报名成功页，含录像回放授权选项',
  JSON_OBJECT(
    'intent', '模拟面试工作坊',
    'reason', '模板：模拟面试报名结构，含录像授权与回放说明',
    'theme', JSON_OBJECT(
      'primary', '#3B4B8C', 'accent', '#9AA6D9', 'bg', '#F6F7FC', 'text', '#1A2038',
      'radius', 14, 'fontScale', 1, 'spacing', 1.05, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '模拟面试工作坊 · 群面 + 单面',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '职业发展中心')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '先被面试一次，再去面试', 'subtitle', '真实流程 · 现场录像 · 一对一复盘')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '形式', 'text', '先 40 分钟无领导小组讨论，再 15 分钟一对一面试')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '时间地点', 'text', '周六 上午 9:00，学生活动中心 401')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '我要报名'))
        )),
      JSON_OBJECT('name', '你会经历什么', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '三步走完一轮')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '① 群面', 'text', '六人一组给一个题目，40 分钟内出结论。观察你在团队里怎么说话')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '② 单面', 'text', '15 分钟，问三个常规问题加一个专业问题')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '③ 复盘', 'text', '当天给书面反馈，一周内可以看自己的录像')),
          JSON_OBJECT('type', 'divider', 'props', JSON_OBJECT()),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '录像只用于你自己复盘和导师点评，不会公开，活动结束 30 天后自动删除。报名时可以选择不录像。'))
        )),
      JSON_OBJECT('name', '报名表', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '报名信息')),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '提交报名',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'name', 'label', '姓名', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'major', 'label', '专业与年级', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'target', 'label', '想面试的岗位方向', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'record', 'label', '是否同意现场录像（用于自己复盘）', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '同意录像', 'value', 'yes'),
                  JSON_OBJECT('label', '不录像，只要书面反馈', 'value', 'no')
                )),
              JSON_OBJECT('id', 'question', 'label', '你最怕被问到什么问题（可选）', 'type', 'textarea', 'required', false)
            )))
        )),
      JSON_OBJECT('name', '报名成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '报名成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '活动前一天会发到场提醒。请穿你面试时会穿的衣服来。')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '如果当天临时有事来不了，请一定在通知里回复一声，名额会留给候补的同学。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description),
  name = VALUES(name), activity_kind = VALUES(activity_kind);

-- 生涯规划 · 校友经验分享会
INSERT INTO templates (id, code, name, category, activity_kind, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000303',
  'career-alumni-talk',
  '校友经验分享会',
  'registration',
  'career',
  '生涯规划：校友分享会报名。嘉宾介绍 + 议程 + 想听什么 + 报名表，可按方向分组',
  JSON_OBJECT(
    'intent', '校友经验分享会',
    'reason', '模板：校友分享会的报名与提问收集结构',
    'theme', JSON_OBJECT(
      'primary', '#4A6B4F', 'accent', '#A8C0A8', 'bg', '#F7FAF6', 'text', '#1B2A1D',
      'radius', 18, 'fontScale', 1, 'spacing', 1.1, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '就业经验谈 · 校友分享会',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '校友会 · 就业分享')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '走过的路，讲给你听', 'subtitle', '三位毕业学长学姐 · 现场答疑')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '嘉宾', 'text', '互联网产品、国企技术、考研上岸各一位，毕业 3-5 年')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '时间地点', 'text', '周五 19:00，图书馆报告厅')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '报名并提问'))
        )),
      JSON_OBJECT('name', '议程', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '两个小时的安排')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '19:00 - 19:40', 'text', '三位嘉宾各讲 12 分钟：怎么选第一份工作')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '19:40 - 20:20', 'text', '分组座谈：按互联网 / 国企 / 升学三个房间')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '20:20 - 21:00', 'text', '自由问答与简历初步建议')),
          JSON_OBJECT('type', 'divider', 'props', JSON_OBJECT()),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '没有标准答案。听别人怎么选，是为了更清楚自己想选什么。'))
        )),
      JSON_OBJECT('name', '报名表', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '报名')),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '提交报名',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'name', 'label', '姓名或昵称', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'grade', 'label', '年级', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'room', 'label', '想参加哪个分组', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '互联网方向', 'value', 'tech'),
                  JSON_OBJECT('label', '国企方向', 'value', 'soe'),
                  JSON_OBJECT('label', '考研升学', 'value', 'grad')
                )),
              JSON_OBJECT('id', 'question', 'label', '你最想问的一个问题（可选）', 'type', 'textarea', 'required', false)
            )))
        )),
      JSON_OBJECT('name', '报名成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '收到')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '你的问题已经转到对应嘉宾那边，他们会挑一部分在问答环节回应。')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '这个活动不收集成绩和家庭情况，报名只需要花一分钟。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description),
  name = VALUES(name), activity_kind = VALUES(activity_kind);

-- 生涯规划 · 职业兴趣自测
INSERT INTO templates (id, code, name, category, activity_kind, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000304',
  'career-interest-quiz',
  '职业兴趣自测',
  'vote',
  'career',
  '生涯规划：职业兴趣小测。说明页 + 六道选择题 + 结果提示，不是测评工具，只做方向参考',
  JSON_OBJECT(
    'intent', '职业兴趣自测',
    'reason', '模板：轻量职业兴趣问卷，用于引导参与后续生涯活动',
    'theme', JSON_OBJECT(
      'primary', '#6B5B95', 'accent', '#B3A6D1', 'bg', '#F9F7FC', 'text', '#221C33',
      'radius', 16, 'fontScale', 1, 'spacing', 1.1, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '职业兴趣自测 · 你更想和什么打交道',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '职业发展中心')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '六个问题，看清倾向', 'subtitle', '约 3 分钟 · 不收集个人信息 · 结果只给你自己看')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '这不是心理测评，也不做能力判断。它只帮你把「我想做什么」说得更具体一点，好去选对应的活动。')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '开始'))
        )),
      JSON_OBJECT('name', '开始自测', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '按第一感觉选')),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '看结果',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'q1', 'label', '周末有半天时间，你更想', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '整理资料、做表格', 'value', 'a'),
                  JSON_OBJECT('label', '出门拍点东西、剪个视频', 'value', 'b')
                )),
              JSON_OBJECT('id', 'q2', 'label', '小组作业里你通常负责', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '把人组织起来、定分工', 'value', 'c'),
                  JSON_OBJECT('label', '把内容做扎实', 'value', 'b')
                )),
              JSON_OBJECT('id', 'q3', 'label', '下面哪种描述你更认同', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '希望工作有明确规则和稳定节奏', 'value', 'a'),
                  JSON_OBJECT('label', '希望每天遇到的新问题不一样', 'value', 'd')
                )),
              JSON_OBJECT('id', 'q4', 'label', '你更想听哪种分享', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '某份工作的日常到底是什么样', 'value', 'a'),
                  JSON_OBJECT('label', '怎么从零做成一件事', 'value', 'd')
                )),
              JSON_OBJECT('id', 'q5', 'label', '遇到分歧时你更倾向', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '先听大家说完，找共同点', 'value', 'c'),
                  JSON_OBJECT('label', '先做个小样出来再讨论', 'value', 'd')
                )),
              JSON_OBJECT('id', 'q6', 'label', '你希望三年后自己', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '在一个方向上有积累', 'value', 'a'),
                  JSON_OBJECT('label', '试过好几件事，找到擅长的', 'value', 'd')
                ))
            )))
        )),
      JSON_OBJECT('name', '结果', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '提交成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '组织者会把相同倾向的同学聚到一起，推荐对应的简历门诊、模拟面试或校友分享场次。')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '这些答案只用于分组，不给成绩，也不进任何测评报告。想撤销可以随时告诉我们。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description),
  name = VALUES(name), activity_kind = VALUES(activity_kind);
