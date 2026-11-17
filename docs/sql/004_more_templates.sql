-- ============================================================================
-- 004_more_templates.sql
-- 把模板从 1 条补到 7 条，覆盖四类活动。
--
--   mysql -u campus_app -p --default-character-set=utf8mb4 campus_diy < docs/sql/004_more_templates.sql
--
-- 用固定 UUID，可重复执行（ON DUPLICATE KEY UPDATE）。
--
-- ⚠ UUID 号段：本文件只用 105~110。
--   原来这里写的是 102/103/104，而 003_seed_fix 已经占用了 101~104——
--   按 docs/sql/README.md 的顺序（003_seed_fix 在 004 之前）跑，
--   ON DUPLICATE KEY UPDATE 会按主键把那三行模板的 name/description/document
--   全改成这里的“志愿者招募/讲座报名/比赛报名”，但 code 不变，导致 code 与名字对不上。
--   已改为 108/109/110。以后新增模板请往 110 之后排，别再碰 101~104。
--
-- 注意：模板的 pages/blocks 刻意不写 id —— id 是运行时由编辑器生成的，
-- 后端 ProjectService.fillIds() 在实例化时会自动补齐 pg-xxx / blk-xxx。
--
-- document 结构 ＝ 前端 types/editor.ts 的 DocPlan：
--   { intent, reason, theme:{primary,accent,bg,text,radius,fontScale,spacing,buttonStyle,font},
--     title, pages:[ {name, role, blocks:[ {type, props} ]} ] }
-- block.type 必须是 13 种之一：
--   nav banner heading text image infoCard button form radio checkbox qrcode divider countdown
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 招募类
INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000108',
  'recruitment-volunteer',
  '志愿者招募',
  'recruitment',
  '首页 + 岗位介绍 + 报名表单 + 成功页，适合志愿服务、公益岗招募',
  JSON_OBJECT(
    'intent', '志愿者招募',
    'reason', '模板：志愿服务类招募的标准四页结构',
    'theme', JSON_OBJECT(
      'primary', '#0F766E', 'accent', '#14B8A6', 'bg', '#F8FAFC', 'text', '#0F172A',
      'radius', 16, 'fontScale', 1, 'spacing', 1.1, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '校园马拉松志愿者招募',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '志愿服务队')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '和我们一起服务赛场', 'subtitle', '2026 秋季校园马拉松')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '招募对象', 'text', '全校在读本科生、研究生')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '服务时间', 'text', '11 月中旬，赛前培训半天')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '我要报名'))
        )),
      JSON_OBJECT('name', '岗位说明', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '岗位与职责')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '补给站、引导、计时记录三类岗位，按志愿填报顺序分配。')),
          JSON_OBJECT('type', 'divider', 'props', JSON_OBJECT()),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '完成服务可获得志愿时长认证与纪念奖品。'))
        )),
      JSON_OBJECT('name', '报名表单', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '提交报名',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'name', 'label', '姓名', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'studentNo', 'label', '学号', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'phone', 'label', '联系电话', 'type', 'text', 'required', true)
            )))
        )),
      JSON_OBJECT('name', '报名成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '报名成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '我们会在三个工作日内通知你面试安排。')),
          JSON_OBJECT('type', 'qrcode', 'props', JSON_OBJECT('caption', '扫码进志愿者群'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

-- ---------------------------------------------------------------- 报名类
INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000109',
  'registration-lecture',
  '讲座报名',
  'registration',
  '首页 + 讲座详情 + 报名表单 + 成功页，适合讲座、沙龙、分享会',
  JSON_OBJECT(
    'intent', '讲座报名',
    'reason', '模板：单场讲座的报名结构',
    'theme', JSON_OBJECT(
      'primary', '#312E81', 'accent', '#8B5CF6', 'bg', '#FFFFFF', 'text', '#1E1B4B',
      'radius', 10, 'fontScale', 1, 'spacing', 1, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', 'AI 与校园创作分享会',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '学术沙龙')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', 'AI 与校园创作', 'subtitle', '11 月 8 日 19:00 · 图书馆报告厅')),
          JSON_OBJECT('type', 'countdown', 'props', JSON_OBJECT('title', '距开讲还剩')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '免费预约'))
        )),
      JSON_OBJECT('name', '活动详情', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '你会听到什么')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '从提示词到完整页面：三组同学分享他们用 AI 做活动的真实过程。')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '地点', 'text', '图书馆一楼报告厅')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '时长', 'text', '约 90 分钟，含问答'))
        )),
      JSON_OBJECT('name', '报名表单', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '确认预约',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'name', 'label', '姓名', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'email', 'label', '邮箱', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'college', 'label', '所在学院', 'type', 'text', 'required', false)
            )))
        )),
      JSON_OBJECT('name', '报名成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '预约成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '凭手机号在门口签到即可入场。')),
          JSON_OBJECT('type', 'qrcode', 'props', JSON_OBJECT('caption', '加群接收资料'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000110',
  'registration-competition',
  '比赛报名',
  'registration',
  '首页 + 赛制说明 + 团队报名 + 成功页，适合各类竞赛、挑战赛',
  JSON_OBJECT(
    'intent', '比赛报名',
    'reason', '模板：竞赛类活动报名，含团队信息采集',
    'theme', JSON_OBJECT(
      'primary', '#7C2D12', 'accent', '#F97316', 'bg', '#FFFBEB', 'text', '#431407',
      'radius', 8, 'fontScale', 1, 'spacing', 1, 'buttonStyle', 'outline', 'font', 'sans'
    ),
    'title', '校园编程挑战赛',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '编程挑战赛')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '48 小时，做出你的产品', 'subtitle', '团队赛 · 1-3 人')),
          JSON_OBJECT('type', 'countdown', 'props', JSON_OBJECT('title', '报名截止')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '组队报名'))
        )),
      JSON_OBJECT('name', '赛制说明', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '赛制')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '初赛线上提交原型，前 12 支队伍进入现场决赛。')),
          JSON_OBJECT('type', 'divider', 'props', JSON_OBJECT()),
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '选题方向')),
          JSON_OBJECT('type', 'radio', 'props', JSON_OBJECT('label', '你最想做的方向', 'required', true,
            'options', JSON_ARRAY(
              JSON_OBJECT('id', 'a', 'label', '校园服务'),
              JSON_OBJECT('id', 'b', 'label', '学习工具'),
              JSON_OBJECT('id', 'c', 'label', '自由命题')
            )))
        )),
      JSON_OBJECT('name', '报名表单', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '提交队伍信息',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'teamName', 'label', '队伍名称', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'leader', 'label', '队长姓名', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'members', 'label', '队员（逗号分隔）', 'type', 'text', 'required', false),
              JSON_OBJECT('id', 'contact', 'label', '联系方式', 'type', 'text', 'required', true)
            )))
        )),
      JSON_OBJECT('name', '报名成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '组队成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '截止后会通过邮件发送赛题与提交入口。')),
          JSON_OBJECT('type', 'qrcode', 'props', JSON_OBJECT('caption', '加入参赛者群'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

-- ---------------------------------------------------------------- 投票类
INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000105',
  'vote-photo',
  '摄影作品评选',
  'vote',
  '作品展示 + 投票页 + 结果查看，适合作品类人气投票',
  JSON_OBJECT(
    'intent', '摄影作品评选',
    'reason', '模板：作品展示 + 单选投票',
    'theme', JSON_OBJECT(
      'primary', '#1F2937', 'accent', '#F59E0B', 'bg', '#0F172A', 'text', '#F8FAFC',
      'radius', 20, 'fontScale', 1, 'spacing', 1.2, 'buttonStyle', 'solid', 'font', 'serif'
    ),
    'title', '校园十大光影',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '光影评选')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '校园十大光影', 'subtitle', '为喜欢的作品投一票')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '每人一票，投票截止到本月底。')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '开始投票'))
        )),
      JSON_OBJECT('name', '投票', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '选择你最爱的作品')),
          JSON_OBJECT('type', 'radio', 'props', JSON_OBJECT('label', '作品', 'required', true,
            'options', JSON_ARRAY(
              JSON_OBJECT('id', 'w1', 'label', '01 早八的走廊'),
              JSON_OBJECT('id', 'w2', 'label', '02 雨后的操场'),
              JSON_OBJECT('id', 'w3', 'label', '03 图书馆四楼'),
              JSON_OBJECT('id', 'w4', 'label', '04 深夜食堂')
            ))),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '提交投票',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'voter', 'label', '你的昵称（可不填）', 'type', 'text', 'required', false)
            )))
        )),
      JSON_OBJECT('name', '投票成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '投票成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '结果将在评选结束后公布。')),
          JSON_OBJECT('type', 'qrcode', 'props', JSON_OBJECT('caption', '扫码看实时榜单'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000106',
  'vote-mvp',
  '人气之星评选',
  'vote',
  '候选人介绍 + 多选投票 + 成功页，适合人物类评选',
  JSON_OBJECT(
    'intent', '人气之星评选',
    'reason', '模板：候选人展示 + 多选投票',
    'theme', JSON_OBJECT(
      'primary', '#831843', 'accent', '#EC4899', 'bg', '#FFF1F2', 'text', '#4C0519',
      'radius', 24, 'fontScale', 1, 'spacing', 1, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '本季人气之星',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '人气之星')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '本季人气之星', 'subtitle', '最多可投 3 位')),
          JSON_OBJECT('type', 'countdown', 'props', JSON_OBJECT('title', '投票倒计时')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '去投票'))
        )),
      JSON_OBJECT('name', '投票', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '选出你心中的前三')),
          JSON_OBJECT('type', 'checkbox', 'props', JSON_OBJECT('label', '候选人', 'required', true,
            'options', JSON_ARRAY(
              JSON_OBJECT('id', 'c1', 'label', '李同学 · 支教队长'),
              JSON_OBJECT('id', 'c2', 'label', '王同学 · 社团负责人'),
              JSON_OBJECT('id', 'c3', 'label', '张同学 · 实验室骨干'),
              JSON_OBJECT('id', 'c4', 'label', '陈同学 · 志愿者之星')
            ))),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '确认投票',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'studentNo', 'label', '学号（用于去重）', 'type', 'text', 'required', true)
            )))
        )),
      JSON_OBJECT('name', '投票成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '投票成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '感谢参与，结果将在两周内公布。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

-- ---------------------------------------------------------------- 签到类
INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000107',
  'checkin-event',
  '活动签到',
  'checkin',
  '活动信息 + 签到码 + 签到表单，适合线下活动的现场签到',
  JSON_OBJECT(
    'intent', '活动签到',
    'reason', '模板：线下活动签到，含二维码',
    'theme', JSON_OBJECT(
      'primary', '#065F46', 'accent', '#10B981', 'bg', '#ECFDF5', 'text', '#064E3B',
      'radius', 14, 'fontScale', 1, 'spacing', 1, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '社团开放日签到',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '开放日签到')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '社团开放日', 'subtitle', '签到即可领取纪念徽章')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '时间', 'text', '周六 14:00 - 17:00')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '地点', 'text', '学生活动中心一层')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '立即签到'))
        )),
      JSON_OBJECT('name', '签到', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '签到登记')),
          JSON_OBJECT('type', 'qrcode', 'props', JSON_OBJECT('caption', '现场扫码也可签到')),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '完成签到',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'name', 'label', '姓名', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'studentNo', 'label', '学号', 'type', 'text', 'required', true)
            )))
        )),
      JSON_OBJECT('name', '签到成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '签到成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '凭此页面到前台领取纪念徽章。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

-- 验证：
--   SELECT code, name, category FROM templates ORDER BY category, code;
-- 期望 7 行：recruitment 2 / registration 2 / vote 2 / checkin 1
