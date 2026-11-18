# 心屿同行 · 校园心理社团活动平台

> 传智杯 AI Web 组参赛作品 · `web/` + `server/` + `agent/` 三端单体仓库

**心屿同行**是给校园心理社团用的一站式活动平台。它把一件原本要三步走、还容易出错的事，压成了一步：

**一句话说清想办什么 → 自动生成能报名、能分享、能统计的活动页 → 参与者在里面匿名留下自己的心情 → 活动结束后，AI 陪他们回看这段时间。**

---

## 0. 评委快速体验

按 [`5. 本地启动`](#5-本地启动) 跑完迁移与三端后，打开 <http://localhost:5173>：

| 场景 | 怎么看 |
|---|---|
| 什么都不用登录 | 首页、心理活动广场（4 场活动）、活动详情与报名、心晴工具箱、心理资源 |
| 登录看运营侧 | `demo@example.com` / `Campus2026a` |
| 登录后看什么 | 运营概览、模板库（8 套）、编辑器、发布、数据与导出、我的轨迹与 AI 回看 |

> 演示账号与演示数据由 `docs/sql/002_seed.sql`、`003_seed_fix.sql`、`014_demo_wellbeing_activities.sql` 写入，
> 不需要自己注册或建活动，跑完迁移即可看到完整界面。

---

## 1. 为什么做这个

心理社团办一场活动，现在要过这几关：

| 环节 | 现实困难 |
|---|---|
| 做宣传页 | 社团没有设计，用问卷工具做的报名页又丑又只会收数据 |
| 收报名 | 心理类活动一旦收集真名、院系、困扰描述，就会被追问「这些数据存哪、谁能看」 |
| 活动结束 | 参与者写了一张便签就走了，活动结束即散场，个人层面没有任何沉淀 |

**心屿同行对应给三个答案**：

1. **AI 生成活动页** —— 描述需求或上传一张海报，直接产出可运行的活动页（不是图片，是真页面）。
2. **心理关怀默认安全** —— 匿名参与、知情同意、提交后可撤回；有危机信号立刻转到人工求助资源，不交给模型判断。
3. **AI 回看** —— 只根据用户自己留下的记录，生成一段带证据的回看，而不是一句「你要自信」。

---

## 2. 核心能力

### 2.1 活动页生成与编辑
- 多页面 `PageDocument` 结构，**13 类受控组件**（nav / banner / heading / text / image / infoCard / button / form / radio / checkbox / qrcode / divider / countdown），不接受任意 HTML。
- 属性可视化修改、实时画布预览、撤销重做、草稿乐观锁、发布版本、二维码与静态页导出。
- 后端 `PageDocumentValidator` 按 JSON Schema 校验，`DocumentRepair` 修补历史脏数据。

### 2.2 参与端
- 公开活动页免登录浏览；报名支持匿名模式与必填项策略。
- **参与轨迹**：参与者在活动里逐步留下摘要、标签与「改主意」的瞬间，可跨设备同步。
- **心晴工具箱**：能量牌局、三栏记录，两种轻量的情绪梳理工具，登录后落库。

### 2.3 回看智能体（本仓库最新能力）
`POST /api/v1/insights/analyze`

它不是聊天机器人，是一个**有严格边界的报告生成器**：

| 约束 | 实现方式 |
|---|---|
| 不诊断、不分级、不预测 | 输出过滤 + 诊断词/绝对化表述黑名单 |
| 每条结论必须有证据 | 无 evidence 的 finding 直接丢弃；日期与活动名必须在 facts 中存在 |
| 数据不足不硬编 | 0 条 → `insufficient_data`，**不调模型** |
| 危机优先 | 危机词走**正则规则**，命中即 `crisis`，**不调模型**，只给求助资源 |
| 失败不返回半成品 | 校验不过重试一次，仍失败返回 `analysis_error` |
| 可复现 | 记录 `insightId / model / promptVersion / 输入哈希` |
| 省钱 | 同一用户 + 同一 facts 哈希 24 小时缓存 |

四种结局共用 200 响应，靠 `status` 区分（`ok` / `insufficient_data` / `crisis` / `analysis_error`）——因为「危机」必须被展示，不能变成一个错误提示。

### 2.4 运营与数据
活动概览、提交统计与关键词、参与轨迹时间线、AI 海报生成任务、CSV 导出、用户反馈与通知。

---

## 3. 技术架构

```
浏览器 (Vue 3)
   │  HTTP / JSON，JWT
   ▼
server/  Spring Boot 3.4 · MyBatis · MySQL 8      :8080   context-path /api/v1
   │  ├── 鉴权 / 项目 / 草稿版本 / 报名 / 统计 / 导出
   │  ├── 回看智能体：闸门 → 提示词 → 模型 → 校验 → 缓存
   │  └── 直连 OpenAI 兼容 /chat/completions（AI_* 环境变量）
   │
   └──► agent/  FastAPI · LangChain / LangGraph    :8000
             ReAct 智能体 + RAG 知识库 + 工具调用 + 多模态海报解析 + 护栏审计
```

| 目录 | 是什么 | 技术栈 | 端口 |
|---|---|---|---|
| `web/` | 前端：编辑器、运营台、公共活动页 | Vue 3 + Vite + TypeScript + Pinia | 5173 |
| `server/` | 业务 API、JWT、草稿版本、报名、统计、**回看智能体** | Spring Boot 3 + MyBatis + MySQL 8 | 8080 |
| `agent/` | 智能体服务 | FastAPI + LangChain / LangGraph | 8000 |
| `docs/` | 三端共用：方案、契约、SQL、环境变量模板 | — | — |

**关键设计**：模型永远拿不到数据库连接，也拿不到表单 `answers`。回看只消费服务端聚合出的 `facts`（活动名、日期、标签、用户自己写的摘要），表单答案是「交给组织者的内容」，不是「成长档案」，两者不混。

---

## 4. 目录导航

- [`团队综合开发文档`](./docs/团队综合开发文档.md) — 总体方案
- [`回看智能体需求文档`](./docs/回看智能体-需求文档.md) — 回看的行为规范与输入输出契约
- [`编码智能体施工需求`](./docs/编码智能体-施工需求.md) — 本轮改造的任务书
- [`OpenAPI 契约`](./docs/api/openapi.yaml) — **改接口先改它**
- [`迁移顺序（权威）`](./docs/sql/README.md) — **新库/旧库到底按什么顺序跑，看这份**
- [`MySQL 建库脚本`](./docs/sql/00_create_database.sql) — 建库 + 建专用账号（root 执行）
- [`环境变量模板`](./docs/.env.example) — 复制成本地 `.env`
- [`前端开发流程`](./docs/frontend-development-flow.md)
- [`智能体说明`](./agent/README.md)

---

## 5. 本地启动

顺序：**agent → server → web**（前端最依赖别人，最后起，报错最清楚）。

### 5.0 数据库

**直接按 [`docs/sql/README.md`](./docs/sql/README.md) 的顺序跑完全部脚本**，那是唯一权威顺序；跳着跑会缺表。

```bash
# 00 由 root 执行
mysql -u root -p --default-character-set=utf8mb4 < docs/sql/00_create_database.sql
# 其余 16 个由 campus_app 执行，顺序见 docs/sql/README.md
```

自查：

```sql
SHOW TABLES;                    -- 应为 19 张
SELECT COUNT(*) FROM templates; -- 应为 18 套（启用 8 套：4 心理 + 4 生涯）
SELECT COUNT(*) FROM projects WHERE status='published' AND deleted_at IS NULL;  -- 应为 4 场心理活动
SELECT COUNT(*) FROM form_submissions;  -- 应为 8 条（游园会的演示报名）
```

健康检查 <http://127.0.0.1:8080/api/v1/health>，`database.tables` 应为 `19`。

### 5.1 演示账号（评委可直接使用）

跑完迁移后，库里已经有一批演示数据，**不需要自己注册**就能看到完整界面：

| 账号 | 密码 | 说明 |
|---|---|---|
| `demo@example.com` | `Campus2026a` | 演示账号，拥有全部演示活动的管理权限 |

用这个账号登录后可以看到：运营概览（8 个项目）、模板库（8 套心理与生涯模板）、
编辑器、发布弹窗、数据页与导出、以及「我的轨迹」里的 AI 回看入口。

**不登录也能看的**：首页、心理活动广场（4 场心理活动）、活动详情页与报名流程、
心晴工具箱、心理资源。

演示活动一览（均由 `docs/sql/014_demo_wellbeing_activities.sql` 写入）：

| 活动 | 类型 | 看点 |
|---|---|---|
| 考试周心晴游园会 | 心晴活动 | 4 页、信息卡、倒计时、匿名报名表单、危机资源页 |
| 心理知识问答 | 心理科普 | 5 道题、匿名作答、开放期倒计时 |
| 情绪小测 · 你最近还好吗 | 心理科普 | 6 道题、独立的一套文案与主题色 |
| 校园心理互助小组 | 心理活动 | 小组活动、保密说明、报名人数限制 |

> 想让 AI 功能（生成活动页 / 读海报 / 回看）可用，还需要配置模型 key，
> 见 5.2 与 5.5。**不配 key 时这些入口会明确提示不可用并降级**，不会报 500。

### 5.2 智能体

```powershell
cd agent
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
# ⚠️ 只有第一次安装才需要。已有 .env 时再执行会把填好的 key 全部覆盖掉。
if (-not (Test-Path .env)) { copy .env.example .env }
# 打开 .env 填 LLM_API_KEY
.\start.ps1
```

自检（不花钱，验证环境是否就绪）：

```powershell
python selftest.py            # 只看环境、数据库、工具、图
python selftest.py --live     # 额外真调一次模型
```

文档 <http://127.0.0.1:8000/docs>

### 5.3 后端

```powershell
cd server
.\dev.ps1
```

`dev.ps1` 会依次解析 JDK 21、Maven（没有就用仓库自带的 `mvnw`）、再以 `dev` profile 启动。
用 IDEA 的话直接跑 `ServerApplication`，但要在 Run Configuration 里把
**Active profiles 设成 `dev`** —— 否则会报「缺少 JWT_SECRET」，这是刻意的，
仓库里不允许存在可直接签 token 的默认密钥。

### 5.4 前端

```powershell
cd web
corepack pnpm install
corepack pnpm dev
```

需要 Node `^22.18.0` 或 `>=24.12.0`，包管理器用 **pnpm**（版本见 `web/package.json` 的 `packageManager`）。

### 5.5 回看智能体要配的环境变量

只从环境变量读，**不写前端、不进 git、不打日志**：

```text
AI_PROVIDER=      # 仅用于日志与复现标识，如 openai / deepseek / qwen
AI_BASE_URL=      # OpenAI 兼容 base url，如 https://api.deepseek.com/v1
AI_API_KEY=
AI_MODEL=         # 如 deepseek-chat
AI_TIMEOUT_MS=60000
```

留空 = 未配置：`/insights/analyze` 会返回 `analysis_error` 并说明没配模型，
**不会假装成功，也不会返回编造的结论**。

---

## 6. 测试与质量

| 层 | 命令 | 现状 |
|---|---|---|
| 前端 | `pnpm typecheck` / `pnpm test:unit --run` / `pnpm build` | 类型检查通过，**75 条测试全过**，构建成功（按视图分包） |
| 后端 | `mvn -B test` | 全过（含回看校验 17 条：无证据、假日期、假活动名、诊断词、截断 JSON、allowQuote 关闭等） |
| 智能体 | `python -m pytest -q` | 全过 |

GitHub Actions（`.github/workflows/ci.yml`）三个 job 独立跑：**后端编译与测试 / 前端类型检查、测试与构建 / 智能体测试**。
CI 刻意不起 MySQL——当前后端测试都是纯单元测试，启动数据库会把 20 秒拖成十几分钟。

---

## 7. AI 技术落地点（给评委的一页）

| 能力 | 落在哪 | 一句话说明 |
|---|---|---|
| 大模型生成 | `agent/` | 一句话需求 → 完整 `PageDocument`，受控组件 + JSON Schema 校验，模型输出不可能变成任意 HTML |
| ReAct 工具调用 | `agent/` LangGraph | agent ⇄ tools 循环，9 个工具（出方案 / 校验 / 查知识库 / 读海报等），最多 4 轮 |
| RAG 混合检索 | `agent/` | 本地 15 篇知识文档 / 76 个切片，向量 + BM25 加权混合，带场景加成与停用词剥离 |
| 多模态 | `agent/` | 上传海报 → 视觉模型抽字段（认不出的留空，不编） |
| 护栏与审计 | `agent/` | 危机词正则前置 + 输出复核 + 全链路审计日志 |
| MCP | `agent/` | 自实现 JSON-RPC，暴露工具清单给外部调用 |
| **受限生成 + 证据约束** | `server/` 回看智能体 | 规则闸门先于模型；每条结论必须带可核验证据；不合格重试一次后报错而不是降级 |

最后一行是这一轮的重点：**让模型在明确的边界里说话，比让它说得更像人更难，也更有用。**

---

## 8. 约定

1. **只改自己的目录。** 跨目录改动先在群里说一声。
2. **密钥只放各自的 `.env`**（已被 `.gitignore` 忽略），绝不提交、绝不截图外发。
3. **接口契约以 `docs/api/openapi.yaml` 为准。** 改了就当天通知三边，当天联调。
4. **提交前 `git status`**，看见 `.env` / `.venv` / `node_modules` / `*.db` 就停手。
5. **迁移脚本只增不改。** 改过的脚本要让已经跑过的人也能安全重跑（幂等），
   `004_more_templates.sql` 曾经因为 UUID 撞车把三套模板改名，教训写在文件头。

---

## 9. 仓库地址

```powershell
git clone https://github.com/Jiang95a/Chada_AI_DIY.git
```

> 仓库已从 `Jiang95a/new` 改名为 **`Jiang95a/Chada_AI_DIY`**。旧地址 GitHub 仍会重定向，
> 但提交给评委、写进技术文档、贴到答辩 PPT 的链接**一定要用新地址**。
> 本地如果还指向旧地址：
>
> ```powershell
> git remote set-url origin https://github.com/Jiang95a/Chada_AI_DIY.git
> ```
