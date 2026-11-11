# 智能体服务（agent）

FastAPI + LangChain + LangGraph。对话、工具调用、知识库检索（RAG）、海报理解（多模态）、MCP 协议、AI 护栏，六块都在这里。

**当前状态：单元测试 136 条全过，离线自检 8/8，真模型与真视觉模型都实测过**（deepseek-chat 出方案、qwen3.8-max 读海报）。

```bash
python selftest.py          # 不花钱，8 步验证
python -m pytest tests -q   # 136 passed
```

## 能力地图（答辩按这张表讲）

| 能力 | 怎么实现的 | 接口 | 证据 |
|---|---|---|---|
| 大模型集成 | OpenAI 兼容 + 5 家供应商预设；结构化输出失败自动修一次 | `POST /chat` | `app/services/llm_client.py` |
| Agent 与工作流 | LangGraph 手写 ReAct 图，工具节点自己解析 tool_calls，4 轮上限 | `POST /chat` | `agent_service.py` |
| **RAG** | 15 篇语料 / 76 块（含 7 篇心理社团领域语料）；哈希向量 + BM25 混合检索；场景加权；阈值过滤 + 检索侧停用词 | `POST /kb/search` | `services/rag/` |
| **多模态** | 视觉模型读海报 → 结构化字段 + 每个字段的图上原文 | `POST /poster/extract` | `services/vision.py` |
| **MCP** | 自实现 JSON-RPC（零依赖）；stdio + HTTP 双传输，同一 dispatcher | `GET /mcp/manifest` | `app/mcp/` |
| AI 安全 | 注入拦截 / 输出消毒 / 单 IP 限流 / 全量审计 / **危机关怀拦截** | `GET /audit/logs` | `core/guard.py` |
| 产品工具 | 出页面方案、校验方案、查报名数据（不编数字） | 工具调用 | `services/tools/planner.py` |
| **PageDocument** | 出口就把方案补成完整文档（id / slug / theme / meta / 样式 / 按钮跳转），落库即可 apply | `POST /plan` | `services/page_document.py` |

## 启动

```powershell
cd agent
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
# ⚠️ 只有第一次安装才需要下面这行。已有 .env 时再执行它，会把填好的 key 全部覆盖掉。
if (-not (Test-Path .env)) { copy .env.example .env }
# 然后打开 .env 填 LLM_API_KEY（海报解析再填 VISION_*，AI 出图再填 IMAGE_*）
python selftest.py          # 不花钱
.\start.ps1                 # 启动前会自检 .env，缺 key 会直接告诉你缺哪几行
```

> 踩过的坑：照着老文档无脑执行 `copy .env.example .env`，`.env` 会被打成空模板，
> 服务照样启动、端口照样监听，只是每个 AI 接口都 503。现在 `start.ps1` 会先自检，
> 而且这句 `copy` 已经被 `if (-not (Test-Path .env))` 包住了。

真调一次：`python selftest.py --live`

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/chat` | 一问一答。返回 `reply / tool_calls / rag_hits / flags / latency_ms` |
| POST | `/chat/stream` | SSE 流式，每行 `data: {"delta": "..."}`，末尾 `data: [DONE]` |
| POST | `/plan` | **直接产出一份完整的 PageDocument**（不调模型、可重复、能直接落库 apply） |
| GET | `/chat/threads` | 最近的会话 id |
| GET | `/chat/{thread_id}/history` | 某会话历史 |
| DELETE | `/chat/{thread_id}` | 清空会话 |
| POST | `/poster/extract` | **海报 → 结构化活动信息**（三种传法，见下） |
| POST | `/kb/search` | 检索知识库，看命中了哪几块、各自打分 |
| GET | `/kb/stats` | 几个文档 / 几块 / 几个场景 / 维度 |
| POST | `/kb/rebuild` | 改完语料重建索引（幂等） |
| GET | `/mcp/manifest` | MCP 能力清单（工具 + inputSchema） |
| POST | `/mcp/rpc` | MCP JSON-RPC 入口（与 stdio 同一实现） |
| GET | `/audit/logs` | 最近的 AI 调用审计 + 汇总 |
| GET | `/health` | 探活 + 能力开关（vision/rag/guard 是否就绪） |

错误码：`422` 入参/图片不合法；`429` 被限流；`502` 模型异常；`503` 没配 key 或没配视觉模型（**前端据此降级**）。

## PageDocument 怎么补（这一节解释「为什么不用后端兜底」）

后端 `POST /ai/projects/{id}/generate` 会转调这里的 `/chat`，把 `reply` 里的 JSON
原样存进 `ai_proposals`，用户点「采用」时才过 schema 校验。所以：

**如果智能体只吐 DocPlan（页面和组件都没有 id，也没有 slug / theme / meta），
用户点「采用」就会 422 —— 前端显示得好好的，一点就炸。**

现在在**出口**就补齐（`services/page_document.py`）：

| 补什么 | 从哪来 | 不补会怎样 |
|---|---|---|
| `id` / `slug` / `title` | 有就留，没有就生成；slug 逐条照抄后端 `Ids.slugify` | 缺 slug 发布链接算不出来 |
| `theme` | 缺了用前端 DEFAULT_THEME | 页面变白底黑字的裸 HTML |
| `meta` | 缺了给六个空字段 | `capacity` 读不到，报名上限失效 |
| `page.id` / `block.id` | 有就留（**幂等**），没有才生成 | schema 的 required，缺了直接 422 |
| `block.props` | 按 13 类受控组件逐个补默认值 | 编辑器属性面板读到 `undefined` |
| `block.style.align` | 空串不在 enum 里 → 强制 `left` | **这一处就能把整份文档卡成 422** |
| 按钮 `pageId` | 按 `targetRole` 找到真实页面 id | 前端预览能点，落库后点了没反应 |
| `hidden` / `createdAt` / `published` | 补默认值 | 前端渲染时字段缺失 |

两条硬性质：

1. **幂等**：一份已经完整的文档再过一遍，id 一个都不变。否则重新生成会换掉 page id，
   按钮跳转和已发出的二维码全部失联。
2. **自检**：产出前按 `page-document.schema.json` 自己校一遍，不合法就不出门
   （`/plan` 直接 422 并列出哪几个字段不对；`/chat` 则原样返回模型的文本，不做破坏性替换）。

顺带修掉的一个跨端 bug：`SYSTEM_EXTRA_MAX_CHARS` 原来是 300，
而后端规划提示词有 435 字，被截掉的正是尾巴上 `block.type 只能是：nav, … countdown`
那段白名单 —— 等于模型从来没收到过受控组件清单。现在上限是 1000，
`tests/test_page_document.py` 里有一条回归测试盯着它。

## RAG 怎么工作

```
knowledge/*.md  →  ［按 ## 小节切块，块间留 80 字重叠］
                →  ［向量：中文双字+三字哈希到 512 维，L2 归一化］
                →  ［落库 kb_chunks，带 scene/kind 标签］
                ↘
user 问题  →  ［识别场景：招新/报名/投票/打卡］
           →  ［两路打分：0.65×余弦 + 0.35×BM25(饱和)］
           →  ［用内容分过滤（默认 ≥0.20）］
           →  ［加场景分：同场景 +0.10，通用规范 +0.02］
           →  Top4 注入 system 消息，带编号和出处
```

四个设计决定，被问到就这么答：

1. **为什么不用向量数据库**：语料 34 块，全表扫一遍是微秒级；多一个服务就多一个会挂的地方。数据涨到几千块再换 pgvector，接口不用改。
2. **为什么哈希向量而不是 embedding API**：离线可跑、可复现、零成本，评委现场没网也能演示。想换真 embedding 只改 `EMBEDDING_BACKEND=api`。
3. **为什么还要 BM25**：纯向量对「必填项最多几个」这种精确词面问题不敏感；反过来纯 BM25 不懂近义（纳新≈招新）。两路融合才稳。
4. **为什么先过滤再加场景分**：顺序反了的话，一句无关的话也能靠「通用规范 +0.02」混过及格线——这是我实测试出来的坑，有 `tests/test_rag.py::test_unrelated_query_below_threshold` 守着。

**加语料**：往 `knowledge/` 放一个带 front-matter 的 md，然后 `POST /kb/rebuild`。

```markdown
---
title: 夜跑活动注意事项
scene: signup
kind: guide
tags: 夜跑,安全
---

## 安全须写清的三件事
……
```

`scene` 取 `recruit / signup / vote / checkin / common`。**别放 `data/` 下，那个目录在 .gitignore 里。**

## 多模态怎么工作

主模型可以是 deepseek（便宜、会写方案），但**读图必须另一个模型**，所以 `VISION_*` 是独立一套配置。

```powershell
# 传图片二进制（推荐）
curl.exe -X POST http://127.0.0.1:8000/poster/extract `
  -H "Content-Type: image/png" --data-binary "@poster.png"

# 传图片地址
curl.exe -X POST "http://127.0.0.1:8000/poster/extract?url=https://example.com/a.jpg"

# 前端现成的 data URL
curl.exe -X POST http://127.0.0.1:8000/poster/extract `
  -H "Content-Type: application/json" -d "{\"dataUrl\":\"data:image/png;base64,...\"}"
```

返回长这样（真跑出来的）：

```json
{
  "ok": true, "source": "vision", "model": "qwen3.8-max-0902", "latencyMs": 12131,
  "info": {
    "title": "2026 秋季招新", "org": "光影摄影社",
    "startAt": "11月8日 周六 19:00", "place": "三教 402",
    "capacity": 60, "fee": "免费", "contact": "社长 13800138000",
    "evidence": { "place": "地点 三教 402", "capacity": "名额 限 60 人" },
    "unclear": ["deadline", "signupFields"], "confidence": 0.95
  },
  "warnings": []
}
```

三道防线，保证「不瞎编」：

1. 提示词里写死「没写的字段给空 + 列进 unclear，不许推测」；
2. 返回带 `evidence`：每个值都附它在海报上的原文，前端可以让用户逐项核对；
3. 代码兜底：`_post_check` 发现 title/时间/地点/主办**全空**就直接报错，不返回一张空洞的卡片。

`capacity` 只在海报明确写了数字才填，否则是 `null`——不接受"看起来像 60 人就填 60"。

## 工具（9 个）

| 分组 | 工具 | 干什么 | 为什么必须有 |
|---|---|---|---|
| 产品动作 | `list_block_types` | 列出 13 个受控组件及必填属性 | 模型不能自己造组件名，造了前端就是白块 |
| | `draft_page_plan` | 按场景生成多页方案（直接可渲染的 JSON） | 让 Agent 真的驱动产品动作，而不只是聊天 |
| | `validate_page_plan` | 校验组件名/必填属性/按钮跳转/表单字段/页面角色 | 提交前就把"渲染不了的东西"拦下来 |
| 多模态 | `read_poster_from_url` | 读海报 → 结构化字段 | 用户发个图链接就能直接建页 |
| 知识库 | `search_knowledge` | 检索本地规范语料 | 让模型有据可依，而不是凭感觉 |
| 数据 | `get_activity_stats` | 查报名人数（读 `activity_stats`，演示数据会如实标注） | 旧版查的是 agent 自己种的假表，会自信编数字——已删 |
| 基础 | `get_current_time` / `calculate` / `search_web` | 当前时间 / 四则运算 / 联网 | 老四样，留着 |

**加工具**：`tools/my_tool.py` 写 `@tool` 函数 → 在 `tools/__init__.py` 的 `ALL_TOOLS` 加一行 → 顺手加进 `TOOL_GROUPS`。docstring 是给模型看的说明书，必须写"什么时候用"；永远返回字符串；永远别抛异常。

## MCP

协议 2024-11-05，自实现 `initialize / tools/list / tools/call / ping`，**不需要装 mcp SDK**。

- **stdio**：`python -m app.mcp.stdio_server`，给 Claude Desktop / Cursor 用
- **HTTP**：`GET /mcp/manifest` 看清单，`POST /mcp/rpc` 发 JSON-RPC

Claude Desktop 的 `claude_desktop_config.json`：

```json
{
  "mcpServers": {
    "campus-activity": {
      "command": "F:\\look\\new\\agent\\.venv\\Scripts\\python.exe",
      "args": ["-m", "app.mcp.stdio_server"],
      "cwd": "F:\\look\\new\\agent"
    }
  }
}
```

手动验一把：

```powershell
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | .\.venv\Scripts\python.exe -m app.mcp.stdio_server
```

> Windows 必读：stdout 默认是 GBK，而 MCP 要求 UTF-8。`stdio_server` 里已经 `reconfigure` 过了，
> 别把那几行删掉，删了中文工具结果会变乱码甚至抛 UnicodeEncodeError。

## 危机关怀怎么工作（心屿这一版最该被问到的）

心理社团的活动页，最坏的情况不是做得不好看，是有人在上面说了一句「不想活了」，而系统回他一个「要什么配色？」。

所以这一条**优先级高于其余所有规则**，实现上是「短路」：

```
用户这句话 → is_crisis() ──命中──► 直接返回固定文案，不调模型、不跑 RAG、不花 token
                        └─未命中──► 正常走 RAG + 模型
```

三个刻意的设计：

1. **不让模型写这段话。** 回复是 `crisis_reply()` 里写死的，号码来自 `.env` 的 `CRISIS_*`。
   模型大概率会补一句「会好起来的」——而我们保证不了，那正是最不该说的。
2. **不判断程度。** 这里只回答「这句话里有没有风险的形状」，严重程度是专业人员的事。
   规则故意偏向误报：多给一次资源，代价只是多说一句用不上的话。
3. **少误报。** 「累死了」「想死你了」「跳楼机」都有排除规则，否则会把正常用户吓一跳。

配套的两件事：

- 心理类场景（`wellbeing` / `group` / `treehole`）的**成功页会自动挂一张「求助入口」信息卡**
  （用现成的 `infoCard` + `contact` 图标，不新增组件类型，三端零改动）；
- 一次拦截会写进 `ai_call_logs`，`flags = crisis_intercepted`，可在 `GET /audit/logs` 里查。

> 上线前必须改 `.env` 里的 `CRISIS_HOTLINE` / `CRISIS_CENTER_HINT`：
> 每个学校的地点、预约方式、当地热线都不一样，写死一个假地址是答辩时最容易被问穿的。

## AI 安全（这一块最容易被追问）

| 位置 | 做了什么 | 命中后 |
|---|---|---|
| `system_extra` | 8 条注入正则（改人设/套提示词/越狱/角色前缀/探 key） | **整段丢弃**，记 `system_extra_blocked`，不回显 |
| **危机关怀** | 自伤/自杀/绝望表达 → 短路，不进模型 | 返回固定求助文案，记 `crisis_intercepted` |
| 用户输入 | 只标记不改写（改写会让用户觉得"我打的字没了"） | 记 flags，进审计 |
| 模型输出 | 剥 `<script>` / `javascript:` / `on*=`；截断；检测系统提示词泄漏 | 记 `output_*` flags |
| 接口 | 单 IP 令牌桶（默认 40 次/分），只卡花钱的接口 | 429 + `Retry-After` |
| 审计 | 每次调用落 `ai_call_logs`：谁、什么模型、几毫秒、拦了什么 | `GET /audit/logs` |
| 密钥 | 只在 `.env`；`/health` 只回 `key_configured: true/false` | — |
| CORS | 配 `*` 时自动关 `allow_credentials`（浏览器不接受这对组合）并告警 | 启动日志 |

`/chat` 的响应里带 `flags`，前端会把「资料 4 条 · 已拦截注入」显示在消息下方——
AI 建议要能解释，用户才敢点「应用」。

## 数据库

一套接口两种后端（`DB_BACKEND=sqlite|mysql`），业务代码只写 `?` 占位符。5 张表：

| 表 | 干什么 |
|---|---|
| `messages` | 会话历史 |
| `kb_chunks` | 知识库块 + 向量（JSON 文本）+ 场景标签 |
| `activity_stats` | 活动报名快照（后端汇总后同步进来；`is_demo=1` 会如实标注） |
| `ai_call_logs` | AI 调用审计 |
| `activities` | 历史遗留演示表，已不读不写，留着兼容旧库 |

改表要改两处：`SQLITE_DDL` 和 `MYSQL_DDL`，它们不是自动生成的。

## 前端怎么接

- `askAgent()` 现在会带回 `ragHits / flags`，编辑器右侧面板会显示「资料 N 条 · 工具名 · ms」。
- `extractPoster(dataUrl)` 打在 `/poster/extract`，创建弹窗的海报页签用它做"上传即预填"：
  **只覆盖有值的字段**，认不出的留空并列出来，503（没配视觉模型）时静默回退手动填写。
- `/chat/stream` 已就绪但前端还没用上，接了就是逐字打字机效果（UX 的「流式渲染」那 3 分）。

## 已知边界

1. 没上 checkpointer，所以没有「中断等人确认」。做「AI 提案 → 人工采纳」时再加。
2. 流式不返回 `tool_calls` 轨迹，只有纯文本。
3. 限流是进程内的，多实例部署要换 Redis。
4. 语料只有 8 篇 / 34 块，覆盖 4 个场景。要扩就扩 `knowledge/`。
5. 视觉模型按张计费，一次 1-10 秒；前端做了压缩（最长边 1400px、JPEG 0.82）再传。
6. sqlite 单机、并发写会锁；mysql 是为了联调，不是为了高并发。

## 出问题先跑自检

```powershell
python selftest.py
python -m pytest tests -q
```

| 现象 | 先看哪 |
|---|---|
| 503 `AI_NOT_CONFIGURED` | `.env` 的 `LLM_API_KEY` 填了没；重启了吗 |
| 503 `VISION_NOT_CONFIGURED` | `VISION_MODEL` 为空；或 key 既没填 `VISION_API_KEY` 也没填 `LLM_API_KEY` |
| 422 `IMAGE_INVALID` | 图不是 PNG/JPG/WEBP/GIF，或超过 `POSTER_MAX_BYTES` |
| 502 `VISION_FAILED` | 视觉模型名写错 / 账号没开通该模型 / 额度用尽 |
| `/kb/search` 返回空 | 语料没进库：`POST /kb/rebuild`，看 `docs` 是不是 0 |
| 检索总是命中「通用规范」 | 语料里 `scene` 标签写错了，或者问题确实很泛 |
| 模型不调工具 | 工具 docstring 太含糊，模型不知道什么时候用 |
| `'gbk' codec can't decode` | 终端先 `$env:PYTHONUTF8=1` |
| 端口被占 | `netstat -ano | findstr :8000` |
