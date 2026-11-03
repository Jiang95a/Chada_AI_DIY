-- ============================================================================
-- 008_wellbeing_templates.sql
-- 补齐「心屿」心理社团平台需要的 4 个心理类模板。
--
--   mysql -u campus_app -p --default-character-set=utf8mb4 campus_diy < docs/sql/008_wellbeing_templates.sql
--
-- 背景：
--   前端 web/src/data/templates.ts 已加入 4 个心理模板，
--   但后端 templates 表只有 7 条老的招募/报名/投票/签到模板，
--   web/src/services/templates.ts 的过滤正则 /心理|心晴|关怀|情绪|正念|匿名|团体/
--   在 GET /templates 的结果里匹配不到任何东西，前端模板页是空的。
--
-- 约束（与 004 一致）：
--   1) templates.category 的 CHECK 只认 recruitment / registration / vote / checkin 四类，
--      所以这里复用这四类，靠 name/description 里的心理语义让前端过滤命中。
--   2) pages/blocks 刻意不写 id —— 由后端 ProjectService.fillIds() 实例化时补齐。
--   3) 固定 UUID + ON DUPLICATE KEY UPDATE，可重复执行。
--   4) block.type 只用这 13 种之一：
--      nav banner heading text image infoCard button form radio checkbox qrcode divider countdown
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 心理社团招新
INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000201',
  'wellbeing-club-recruit',
  '心理社团招新',
  'recruitment',
  '心屿心理社团秋季招新：社团介绍 + 我们做什么 + 报名表 + 报名成功页，报名全程可匿名',
  JSON_OBJECT(
    'intent', '心理社团招新',
    'reason', '模板：心理社团招新的四页结构，含匿名报名提示',
    'theme', JSON_OBJECT(
      'primary', '#5B7C99', 'accent', '#A8C5DA', 'bg', '#F6F9FC', 'text', '#1F2D3D',
      'radius', 16, 'fontScale', 1, 'spacing', 1.1, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '心屿心理社团 · 2026 秋季招新',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '心屿心理社团')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '在这里，你可以慢一点', 'subtitle', '2026 秋季招新 · 不设门槛 · 可匿名报名')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '我们是谁', 'text', '由心理中心指导的学生社团，做朋辈陪伴、心理科普和校园心晴活动')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '招新时间', 'text', '即日起至 11 月底，滚动面试')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '招新地点', 'text', '大学生活动中心 3 楼心理中心')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '我要报名'))
        )),
      JSON_OBJECT('name', '我们做什么', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '四个方向，任选其一')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '朋辈倾听', 'text', '接受基础培训后，为同学提供一对一的倾听与陪伴')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '心理科普', 'text', '写推文、做短视频，把心理学讲成人话')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '活动策划', 'text', '策划心晴市集、正念体验、团体辅导等校园活动')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '技术支持', 'text', '维护心屿平台，开发心理自助工具')),
          JSON_OBJECT('type', 'divider', 'props', JSON_OBJECT()),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '所有方向都提供岗前培训，不需要任何心理学基础。'))
        )),
      JSON_OBJECT('name', '报名表', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '填写报名信息')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '你可以选择匿名填写，组织者只会看到一个随机编号。')),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '提交报名',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'nickname', 'label', '昵称或姓名', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'college', 'label', '学院', 'type', 'text', 'required', false),
              JSON_OBJECT('id', 'direction', 'label', '想加入的方向', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '朋辈倾听', 'value', 'listen'),
                  JSON_OBJECT('label', '心理科普', 'value', 'popular'),
                  JSON_OBJECT('label', '活动策划', 'value', 'plan'),
                  JSON_OBJECT('label', '技术支持', 'value', 'tech')
                )),
              JSON_OBJECT('id', 'motivation', 'label', '想说的话（可选）', 'type', 'textarea', 'required', false)
            )))
        )),
      JSON_OBJECT('name', '报名成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '收到啦')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '我们会在三天内通过站内通知联系你，面试地点在心理中心 3 楼。')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '如果你现在正遇到困难，也可以直接到心理中心预约咨询，不必等到面试。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

-- ---------------------------------------------------------------- 心理知识问答
INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000202',
  'wellbeing-quiz',
  '心理知识问答',
  'vote',
  '心理知识小测验：科普页 + 单选题 + 答对提示，适合心理健康周、朋辈培训随堂测试',
  JSON_OBJECT(
    'intent', '心理知识问答',
    'reason', '模板：心理科普单选题结构，可直接用于心理健康周活动',
    'theme', JSON_OBJECT(
      'primary', '#7C6BA8', 'accent', '#C4B5E8', 'bg', '#FAF7FF', 'text', '#231F3D',
      'radius', 18, 'fontScale', 1, 'spacing', 1.1, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '心灵小知识 · 你能答对几题',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '心理知识问答')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '了解自己，是照顾好自己的一部分', 'subtitle', '5 道题 · 约 2 分钟 · 匿名作答')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '活动说明', 'text', '本问卷仅用于心理健康科普及自我察觉，不是诊断工具')),
          JSON_OBJECT('type', 'countdown', 'props', JSON_OBJECT('title', '距活动结束', 'endsAt', '2026-12-31T23:59:59')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '开始答题'))
        )),
      JSON_OBJECT('name', '科普', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '开始之前')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '心理困扰不是「想太多」，也不是意志力问题。它和感冒发烧一样，是可被理解和处理的状态。')),
          JSON_OBJECT('type', 'divider', 'props', JSON_OBJECT()),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '如果你连续两周情绪低落、睡眠或食欲明显改变，建议到心理中心聊一聊。')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '紧急情况可拨打学校心理援助热线，或直接前往心理中心。'))
        )),
      JSON_OBJECT('name', '答题', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '答题')),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '提交答案',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'q1', 'label', '长期睡眠不足最常见的影响是？', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '注意力与情绪调节能力下降', 'value', 'a'),
                  JSON_OBJECT('label', '身高增加', 'value', 'b'),
                  JSON_OBJECT('label', '没有影响', 'value', 'c')
                )),
              JSON_OBJECT('id', 'q2', 'label', '朋友向你倾诉低落情绪时，比较合适的回应是？', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '先听，再问对方需要什么', 'value', 'a'),
                  JSON_OBJECT('label', '立刻给出解决方案', 'value', 'b'),
                  JSON_OBJECT('label', '说「这没什么大不了的」', 'value', 'c')
                )),
              JSON_OBJECT('id', 'q3', 'label', '以下哪项属于自我关怀的做法？', 'type', 'checkbox', 'required', false,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '允许自己有做不到的事', 'value', 'a'),
                  JSON_OBJECT('label', '保持规律作息', 'value', 'b'),
                  JSON_OBJECT('label', '把自己的情绪全部压下去', 'value', 'c')
                )),
              JSON_OBJECT('id', 'note', 'label', '想对自己说的一句话（可选）', 'type', 'textarea', 'required', false)
            )))
        )),
      JSON_OBJECT('name', '完成', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '谢谢你认真作答')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '心理知识只是起点，真正的照顾发生在每一天的小事里。')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '想聊聊的话，小程序「心屿」首页有免费的自助工具与预约入口。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

-- ---------------------------------------------------------------- 心晴活动报名
INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000203',
  'wellbeing-event',
  '心晴活动报名',
  'registration',
  '心晴市集 / 正念体验 / 团体辅导类活动报名：活动介绍 + 须知 + 报名表 + 成功页，含安静区与匿名说明',
  JSON_OBJECT(
    'intent', '心晴活动报名',
    'reason', '模板：心理中心活动报名，含隐私须知与安静区提示',
    'theme', JSON_OBJECT(
      'primary', '#E08A5B', 'accent', '#F5C9A8', 'bg', '#FFF9F4', 'text', '#33241B',
      'radius', 16, 'fontScale', 1, 'spacing', 1.1, 'buttonStyle', 'solid', 'font', 'sans'
    ),
    'title', '心晴市集 · 秋日正念体验',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '心晴活动')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '在心晴市集，给自己一个小时', 'subtitle', '正念体验 · 情绪手作 · 一对一倾听')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '时间', 'text', '11 月 15 日 14:00 - 17:00')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '地点', 'text', '大学生活动中心一层中庭')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '人数', 'text', '限 80 人，报满即止')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '报名参加'))
        )),
      JSON_OBJECT('name', '参与须知', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '参加之前')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '活动全程不需要发言，也不要求你分享任何个人经历。')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '安静区', 'text', '现场设有不参与互动的安静角，觉得累了可以随时过去坐一会儿')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '匿名', 'text', '报名可以选择匿名，现场也只使用你填写的昵称')),
          JSON_OBJECT('type', 'divider', 'props', JSON_OBJECT()),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '如果你正在经历较重的情绪困扰，建议先到心理中心预约个体咨询，再参加团体活动。'))
        )),
      JSON_OBJECT('name', '报名表', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '填写报名信息')),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '提交报名',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'nickname', 'label', '昵称', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'contact', 'label', '联系方式（邮箱或手机）', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'slot', 'label', '想参加的时段', 'type', 'select', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '14:00 - 15:00 正念体验', 'value', 'p1'),
                  JSON_OBJECT('label', '15:00 - 16:00 情绪手作', 'value', 'p2'),
                  JSON_OBJECT('label', '16:00 - 17:00 一对一倾听', 'value', 'p3')
                )),
              JSON_OBJECT('id', 'note', 'label', '需要安静区吗', 'type', 'radio', 'required', false,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '需要', 'value', 'yes'),
                  JSON_OBJECT('label', '不需要', 'value', 'no')
                ))
            )))
        )),
      JSON_OBJECT('name', '报名成功', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '报名成功')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '活动前一天会通过你留下的联系方式提醒你。')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '如果临时来不了，也不要有压力，下次还有机会。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

-- ---------------------------------------------------------------- 自我关怀打卡
INSERT INTO templates (id, code, name, category, description, document)
VALUES (
  '00000000-0000-4000-8000-000000000204',
  'wellbeing-selfcare-checkin',
  '自我关怀打卡',
  'checkin',
  '21 天自我关怀打卡：每日一个小行动 + 打卡表单 + 鼓励页，适合心理中心日常陪伴运营',
  JSON_OBJECT(
    'intent', '自我关怀打卡',
    'reason', '模板：21 天自我关怀习惯养成，每日打卡',
    'theme', JSON_OBJECT(
      'primary', '#4C8577', 'accent', '#A8D5C4', 'bg', '#F4FBF8', 'text', '#17302A',
      'radius', 20, 'fontScale', 1, 'spacing', 1.15, 'buttonStyle', 'soft', 'font', 'sans'
    ),
    'title', '21 天自我关怀打卡',
    'pages', JSON_ARRAY(
      JSON_OBJECT('name', '首页', 'role', 'home', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'nav', 'props', JSON_OBJECT('title', '自我关怀打卡')),
          JSON_OBJECT('type', 'banner', 'props', JSON_OBJECT('title', '每天做一件对自己好的小事', 'subtitle', '21 天 · 每天 1 分钟 · 匿名参与')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '打卡规则', 'text', '每天完成一个小行动并记录一句感受，中断一天不惩罚，随时可以重新开始')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '隐私', 'text', '打卡内容只有你自己能看到，组织者只统计参与人数')),
          JSON_OBJECT('type', 'button', 'props', JSON_OBJECT('text', '今天的打卡'))
        )),
      JSON_OBJECT('name', '今天做什么', 'role', 'content', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '随机一个小行动')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '第 1 件', 'text', '今天给自己倒一杯热水，慢慢喝完')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '第 2 件', 'text', '出门走 10 分钟，不看手机')),
          JSON_OBJECT('type', 'infoCard', 'props', JSON_OBJECT('title', '第 3 件', 'text', '睡前写下一件今天做得还不错的小事')),
          JSON_OBJECT('type', 'divider', 'props', JSON_OBJECT()),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '不用全部做到，选一件就够了。'))
        )),
      JSON_OBJECT('name', '打卡', 'role', 'form', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '记录今天')),
          JSON_OBJECT('type', 'form', 'props', JSON_OBJECT('submitText', '完成打卡',
            'fields', JSON_ARRAY(
              JSON_OBJECT('id', 'nickname', 'label', '昵称（可随机）', 'type', 'text', 'required', true),
              JSON_OBJECT('id', 'mood', 'label', '今天的情绪', 'type', 'radio', 'required', true,
                'options', JSON_ARRAY(
                  JSON_OBJECT('label', '还不错', 'value', 'good'),
                  JSON_OBJECT('label', '一般', 'value', 'ok'),
                  JSON_OBJECT('label', '有点低落', 'value', 'low')
                )),
              JSON_OBJECT('id', 'action', 'label', '今天做了什么', 'type', 'textarea', 'required', true),
              JSON_OBJECT('id', 'feeling', 'label', '一句话感受（可选）', 'type', 'textarea', 'required', false)
            )))
        )),
      JSON_OBJECT('name', '打卡完成', 'role', 'success', 'blocks',
        JSON_ARRAY(
          JSON_OBJECT('type', 'heading', 'props', JSON_OBJECT('text', '今天也照顾到自己了')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '不需要每天都做得很好，能回来继续就算成功。')),
          JSON_OBJECT('type', 'text', 'props', JSON_OBJECT('text', '如果状态持续不好，心理中心的咨询是免费的，随时可以预约。'))
        ))
    )
  )
)
ON DUPLICATE KEY UPDATE document = VALUES(document), description = VALUES(description), name = VALUES(name);

-- 验证：
--   SELECT code, name, category FROM templates ORDER BY category, code;
