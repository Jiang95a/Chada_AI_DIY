"""生成《青搭·校园活动 AI DIY 工坊》6 分钟演示演讲稿 DOCX。"""
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn

OUT = r"D:\Desktop\new\new\docs\演示演讲稿-青搭AI DIY工坊.docx"

doc = Document()

# ---- 页面设置 ----
sec = doc.sections[0]
sec.top_margin = Cm(1.8)
sec.bottom_margin = Cm(1.8)
sec.left_margin = Cm(2.0)
sec.right_margin = Cm(2.0)

# ---- 默认字体 ----
style = doc.styles["Normal"]
style.font.name = "微软雅黑"
style.font.size = Pt(11)
style.element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")


def set_font(run, name="微软雅黑", size=11, bold=False, color=None):
    run.font.name = name
    run.font.size = Pt(size)
    run.bold = bold
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    if color:
        run.font.color.rgb = RGBColor(*color)


def title(text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(text)
    set_font(r, size=20, bold=True, color=(0x1F, 0x29, 0x37))
    p.paragraph_format.space_after = Pt(4)
    return p


def subtitle(text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(text)
    set_font(r, size=10.5, color=(0x6B, 0x72, 0x80))
    p.paragraph_format.space_after = Pt(14)
    return p


def h1(text):
    p = doc.add_paragraph()
    r = p.add_run(text)
    set_font(r, size=14, bold=True, color=(0x1D, 0x4E, 0xD8))
    p.paragraph_format.space_before = Pt(14)
    p.paragraph_format.space_after = Pt(6)
    return p


def h2(text):
    p = doc.add_paragraph()
    r = p.add_run(text)
    set_font(r, size=11.5, bold=True, color=(0x1F, 0x29, 0x37))
    p.paragraph_format.space_before = Pt(9)
    p.paragraph_format.space_after = Pt(3)
    return p


def op(text):
    """操作提示（灰底说明，不念出口）"""
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.3)
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("▸ 操作：")
    set_font(r, size=10, bold=True, color=(0xB4, 0x53, 0x09))
    r2 = p.add_run(text)
    set_font(r2, size=10, color=(0x92, 0x40, 0x0E))
    return p


def say(text, emphasis=False):
    """要念出口的台词"""
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.3)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.line_spacing = 1.35
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    r = p.add_run(text)
    if emphasis:
        set_font(r, size=11.5, bold=True, color=(0x0F, 0x17, 0x2A))
    else:
        set_font(r, size=11)
    return p


def bullet(text, level=0):
    p = doc.add_paragraph(style="List Bullet" if level == 0 else "List Bullet 2")
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.25
    r = p.add_run(text)
    set_font(r, size=10.5)
    return p


def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    hdr = t.rows[0].cells
    for i, htext in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        r = p.add_run(htext)
        set_font(r, size=10, bold=True, color=(0xFF, 0xFF, 0xFF))
        shd = hdr[i]._tc.get_or_add_tcPr()
        el = shd.makeelement(qn("w:shd"), {qn("w:fill"): "1D4ED8"})
        shd.append(el)
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            r = p.add_run(val)
            set_font(r, size=9.5)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    return t


# ============ 正文 ============
title("青搭 · 校园活动 AI DIY 工坊")
subtitle("现场演示演讲稿（正文约 2500 字，含操作停顿约 11–12 分钟）")

h1("开场前 30 秒：自检清单")
op("打开 http://127.0.0.1:5173/ ，确认右上角能正常显示「登录 / 注册」按钮。")
bullet("三个服务都起来了：5173（前端）、8080（后端）、8000（智能体）")
bullet("先注册好一个账号，并提前建好 1 个项目（防止现场等 AI 太久）")
bullet("浏览器缩放调到 110% 左右，评委在后排也能看清")
bullet("手机热点备份：如果 WiFi 断，AI 会走本地兜底，演示不中断")
bullet("关闭微信、QQ 弹窗通知，全屏浏览器（F11）")

say("提示：带「▸ 操作」的行不用念，是给你的动作提示；其余正文直接照读。")

doc.add_page_break()

# ---------- 第一段 ----------
h1("【00:00 – 01:05】开场：我们做的是什么")

op("停留在首页 http://127.0.0.1:5173/ ，不要点任何东西。")

say(
    "各位老师、同学好，我们做的是「青搭 · 校园活动 AI DIY 工坊」。",
    emphasis=True,
)
say(
    "一句话概括：你输入一句话描述活动，AI 帮你生成整套多页面的活动站，"
    "你接着自己改，然后一键发布，收报名、看数据。"
)
say(
    "请注意我们说的是「移动端 Web」，也就是网页，不是小程序。"
    "同学用手机浏览器打开链接、扫码就能访问，不需要任何安装。"
)

op("鼠标划过首页大标题「让校园活动，更快被看见。也更容易参与。」")

say(
    "这是我们的主张。校园活动最大的问题不是办不起来，是办起来没人知道。"
    "一个社团想招新，会做页面的人不多，会做页面的工具又太重。"
)

# ---------- 第二段 ----------
h1("【01:05 – 02:40】特色一：AI 生成的是「整套多页面活动站」")

op("点「进入工作台」→ 左侧点「AI 生成」抽屉 → 在输入框里粘贴下面这句话。")

say("我先演示最核心的能力。我在需求框里这样说——")
say(
    "「摄影社 2026 秋季招新，11 月 8 日晚 7 点在三教 402 面试，招 60 人」。",
    emphasis=True,
)
op("点「生成页面」，等待（通常 5–15 秒）。等待时继续讲下面这段。")

say(
    "等待的时候我先说明它和别人的区别。市面上绝大多数 AI 建站工具，"
    "生成的是一张页面。但一场真实的校园活动，需要的不止一页。"
)
say(
    "同学扫码进来，第一件要知道这是什么活动，第二件要知道时间来不来得了，"
    "第三件要考虑报不报，第四件报完要确认收到了。这是四件事，不是一件事。"
)

op("生成完成后，鼠标依次划过左侧页面列表的四个页面。")

say(
    "所以青搭生成的是四个页面，而且每一页有明确的角色："
    "首页、内容页、表单页、成功页。",
    emphasis=True,
)
say(
    "这四个角色不是起个名字而已。首页上的「我要报名」按钮，会自动指向表单页；"
    "表单提交成功后，自动跳到成功页。整条报名链路是连起来的。"
)

# ---------- 第三段 ----------
h1("【02:40 – 04:15】特色二：AI 只填组件，不写 HTML —— 13 种受控组件")

op("点左侧「组件」标签页，展示 13 种组件列表（布局 / 内容 / 互动 / 表单四组）。")

say(
    "这里要讲一个特别容易被忽略、但我们认为是关键的设计。",
    emphasis=True,
)
say(
    "AI 生成页面的时候，它输出的不是 HTML 代码，而是一份 JSON，"
    "里面写的是「我要一个 Banner，标题是什么，副标题是什么」。"
)
say(
    "整个系统能用的一共只有 13 种组件：导航栏、Banner、分割线、"
    "标题、文本、图片、信息卡片、按钮、二维码、倒计时、表单、单选框、多选框。"
)

op("随便点一个组件，右侧出现属性面板。")

say("AI 能做的，只是往这 13 个组件的属性里填内容。")
say(
    "这带来三个直接好处。第一，安全。AI 想往页面里塞一段脚本、塞一个外部链接，"
    "它做不到，因为它根本没有这个组件。XSS 这类攻击从源头就没了。",
)
say(
    "第二，稳。生成出来的东西一定能渲染，不会出现「AI 给了段代码，但样式崩了、白屏了」。"
)
say(
    "第三，可控。每个组件都有属性面板，不想要哪个直接拖掉，"
    "AI 做错的地方你能改回来。",
)
op("点左侧「页面」标签页，回到页面视图。")

# ---------- 第四段 ----------
h1("【04:15 – 05:55】特色三：AI 会「给建议」，一键采纳、可以撤销")

op("在画布上点中首页的「我要报名」按钮，让右侧 AI 面板出现「可应用的修改」。")

say(
    "这是我觉得最值得讲的一个功能。AI 不只是生成，它还会给建议。",
    emphasis=True,
)
op("鼠标指向 AI 面板里的建议条目，依次指出：标题、理由、改前改后对比。")

say(
    "我在画布上选中这个按钮，AI 面板立刻给出针对它的建议。"
    "你能看到三样东西：建议的标题、它这么建议的理由、以及改前改后的对比。"
)
say(
    "比如它会说：「按钮文案换成明确动作」，理由是——"
    "「提交、点击这类词不告诉人点完会发生什么，改写后点击率会更高」；"
    "改前是「提交」，改后是「立即报名」。"
)
say("我点一下「应用」，改动立刻生效。", emphasis=True)
op("点「应用」按钮，页面上按钮文字变化，右下角弹出 toast：「已应用这句建议，可撤销」。")

say(
    "而且注意，这里弹出了一条「已应用，可撤销」。"
    "如果我不满意，按 Ctrl + Z 就回来了。"
)
say(
    "这件事的技术含量在于：AI 不是把整个页面重写一遍，"
    "它只是输出一条「改哪个组件的哪个属性、改成什么值」，"
    "然后我们做一次局部更新。所以你采纳一条建议，"
    "绝不会动到你之前手改的其他内容。",
    emphasis=True,
)
say(
    "打个比方：它不是把房子推倒重建，是只换你指定的那扇窗。"
)

# ---------- 第五段 ----------
h1("【05:55 – 07:10】特色四：发布前质量检查，把经验变成规则")

op("点顶部工具条「质量检查」按钮，弹出检查结果。")

say(
    "发布之前还有一个环节：质量检查。我们把做校园活动的实际经验，"
    "写成了二十多条自动检查规则。",
)
say(
    "它检查的东西很具体：主色上的白字对比度够不够，低于 3 比 1 会提醒你；"
    "正文和背景的对比度低于 4.5 比 1 也会提醒；"
    "表单字段超过 6 个会提醒你手机上填着累；"
    "有单选项却没有提交按钮，直接报错，因为参与者填完没法交。",
)

say(
    "分三级：必须修、建议改、可以看看。"
    "每一条都能点「定位」，直接跳到出问题的那一页那个组件上。",
    emphasis=True,
)
say(
    "这一条的意义是：我们的用户是不懂设计的社团同学，"
    "这些规则就是替一个设计师在帮他把关。",
)

# ---------- 第六段 ----------
h1("【07:10 – 08:35】特色五：发布 —— 链接和二维码，改内容链接不变")

op("点顶部的「发布」按钮，弹出发布面板。点「发布并生成二维码」。")

say("好，现在正式发布。发布面板上有这么一句话：「生成链接与二维码，内容更新后链接不变」。")
say(
    "这一点很重要。很多工具你改了内容重新发布，链接就换了，"
    "之前发到群里的二维码全部失效。我们的做法是：发布时生成一个固定的公开地址，"
    "之后你改文案、换配色、加页面，链接一直是同一个。",
    emphasis=True,
)

op("发布会显示链接和二维码。可以用手机扫一下，或点「复制」。")

say(
    "你会得到两样东西：一个公开链接，和一个二维码。"
    "直接把二维码印在招新海报上，或者发到班级群。"
)
say(
    "而且就算你不填链接地址也不会出问题，后端会按活动标题自动生成一个，"
    "并且写回项目里，下次发布还沿用同一个。",
)
say(
    "另外，如果你改到一半想撤下来，有「撤下发布」，链接立刻失效。"
)

# ---------- 第七段 ----------
h1("【08:35 – 10:05】特色六：发布之后才刚开始 —— 真实数据")

op("点左侧「项目数据」进入数据看板（或从工作台点项目的「数据」）。")

say(
    "很多建站工具的场景到「发布」就结束了。我们的场景是发布之后才刚开始。",
    emphasis=True,
)
op("鼠标依次指出：浏览数、报名提交、有效提交、填写转化率、访问趋势图。")

say(
    "这是这个项目的数据看板。上面四个数字：页面浏览数、报名提交数、"
    "有效提交数、填写转化率。下面是一张按天汇总的趋势图。",
)
say(
    "重点说这些数字的来源：全部来自真实数据库，不是写死的演示数据。"
    "有人打开你的页面，就记一次浏览；有人开始填表单，记一次开始填写；"
    "提交成功，记一次有效提交。转化率就是「有效提交除以开始填写」。",
    emphasis=True,
)
say(
    "这个比值对办活动的人特别有用。比如一百个人打开、只有五个人开始填，"
    "说明页面劝退，可能是必填项太多。二十个人开始填、十五个提交成功，"
    "说明表单本身没问题。它直接告诉你该改哪儿。",
)

op("点「提交记录」标签页，展示逐条报名记录，再点「生成 CSV 文件」。")

say(
    "提交记录是逐条的，带提交时间和来源页面。"
    "最后点「生成 CSV」，导出成一个表格文件，"
    "包含提交时间、来源页面、表单状态和完整答案，二十四小时内可下载。"
    "导出来直接就能交给社团负责人。",
)

# ---------- 第八段 ----------
h1("【10:05 – 11:20】特色七：AI 有知识库，断网也不崩")

op("可以在 AI 面板输入框里点快捷提示词「报名表单该收哪些字段」，或者切回截图讲。")

say(
    "最后讲两件事，是我认为这个项目最「扎实」的地方。",
    emphasis=True,
)
say(
    "第一，AI 不是随口说。它脑子里有一个本地知识库，我们放了 8 篇文档、"
    "34 个知识块，专门讲校园活动：招新怎么招、报名表收什么、投票怎么防刷、"
    "文案怎么写、合规注意什么。模型回答之前会先检索这些内容。",
)
say(
    "所以它给你的建议是有出处的，比如它会告诉你「每多一个必填项，"
    "提交率大概掉 8% 到 15%」，然后建议你必填只留姓名、手机号和意向方向。"
    "这不是模型编的，是我们放进知识库的经验。",
)
say(
    "第二，断网也不崩。如果 AI 服务不可用，或者模型接口挂了，"
    "前端会自动走本地兜底，你还能生成一个基本结构、还能手动搭。"
    "整个系统不会因为「AI 挂了」就整体报废。",
    emphasis=True,
)

# ---------- 结尾 ----------
h1("【11:20 – 11:50】收尾")

op("回到首页，或者停在发布完成的二维码页面上。")

say(
    "回到最开始那句话：让校园活动，更快被看见，也更容易参与。",
    emphasis=True,
)
say(
    "刚才这台电脑上，从一句话到一个能扫码报名、能看数据的活动页，"
    "实际动手时间不到十分钟。换成以前，这件事要找一个会做页面的同学，等两三天。",
)
say("以上是我们的演示，谢谢各位。", emphasis=True)

doc.add_page_break()

# ============ 附录 ============
h1("附录 A：应急预案（出问题时照这个说）")

table(
    ["现场情况", "你该做什么", "你该说什么"],
    [
        [
            "AI 生成超过 20 秒没反应",
            "点「忽略」或直接用模板创建",
            "「现场网络有点慢，我先用模板带大家看后面的流程，AI 生成的能力刚才已经演示过了。」",
        ],
        [
            "AI 报错 / 提示服务不可用",
            "继续点「用模板创建」，往下走",
            "「正好演示我们的降级设计——AI 不可用时，本地兜底和手动搭建照样能用。」",
        ],
        [
            "二维码扫不开",
            "改用「复制」把链接发到手机",
            "「局域网环境手机可能访问不到，我用链接直接打开给大家看。」",
        ],
        [
            "数据看板是空的",
            "提前用手机或另一台设备访问一次公开页",
            "「这是一个新项目还没有访问，我先打开一次页面制造一条真实数据。」",
        ],
        [
            "页面白屏 / 报错",
            "按 F5 刷新；还不行就切到提前录好的录屏",
            "「现场环境有点问题，我用录制好的演示继续。」",
        ],
        [
            "整体时间不够了",
            "砍掉「质量检查」和「数据看板」两段",
            "直接跳到结尾",
        ],
    ],
    widths=[4.5, 5.0, 7.0],
)

h1("附录 B：可能被问到的问题（准备好答案）")

h2("Q1：和小程序、和第三方模板站有什么区别？")
say(
    "答：不用装、不用注册平台，是网页；模板站是「选一个长得像的」，"
    "我们是「说清楚你要什么，生成符合你活动的结构」，而且生成完能改。",
)

h2("Q2：AI 生成的东西能直接用吗？")
say(
    "答：不能直接发，必须过一遍质量检查。我们明确设计成「AI 起步、人来收尾」，"
    "检查规则会拦住空白页面、没有提交入口的表单、对比度不足的配色。",
)

h2("Q3：两个人同时编辑会打架吗？")
say(
    "答：不会丢数据。后端有版本号乐观锁，你拿着旧版本号保存会返回 409 冲突，"
    "而不是默默覆盖别人的修改。",
)

h2("Q4：AI 会不会瞎编内容？")
say(
    "答：会限定范围。它只能填 13 种受控组件的属性，回答建议时先检索本地知识库。"
    "而且它生成的事实字段（时间、地点、主办方）会留成「待补充」，让你自己填。",
)

h2("Q5：你们用了什么技术？")
say(
    "答：前端 Vue 3 加 TypeScript，后端 Spring Boot 加 MySQL，"
    "智能体是 FastAPI 加 LangGraph 接 DeepSeek，"
    "三端通过同一份 PageDocument 的 JSON Schema 契约协作。",
)

h1("附录 C：技术数据（评委问「有多少东西」时用）")
table(
    ["指标", "数值"],
    [
        ["后端接口", "43 个 REST 接口，10 个 Controller"],
        ["后端代码", "57 个 Java 文件 / 4328 行"],
        ["前端代码", "69 个 Vue + TS 文件 / 16579 行"],
        ["智能体代码", "3293 行 Python，9 个工具，12 个路由"],
        ["受控组件", "13 种（布局 3 / 内容 4 / 互动 3 / 表单 3）"],
        ["活动模板", "7 套（招新 / 报名 / 投票 / 打卡 / 比赛 / 志愿 / 签到）"],
        ["数据库", "12 张表，MySQL 8"],
        ["知识库", "8 篇文档 / 34 个知识块"],
        ["接口测试", "85 项端到端测试全部通过"],
        ["前端测试", "16 项单元测试全部通过"],
        ["AI 实测响应", "约 6 秒，自动调用 3 个工具，命中 4 条知识库"],
    ],
    widths=[4.5, 12.0],
)

h1("附录 D：念稿节奏提示")
bullet("正文约 2500 字，正常语速 200 字/分约 12.5 分钟；含点击、等待、停顿，实测约 11–12 分钟")
bullet("每段开头的时间是累计进度点，看一眼超没超，超了就砍该段后面的一两句，别赶")
bullet("读到加粗的地方，稍微放慢、加重，那是评委要记的点")
bullet("「▸ 操作」的行绝对不要念出来，只用来提示你的手")
bullet("卡壳了就喝口水，说一句「我们继续下一部分」，没人会记得")

doc.save(OUT)
print("saved:", OUT)
