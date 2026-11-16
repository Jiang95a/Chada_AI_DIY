# 前端接后端：对接清单

> 只列清单和差异，**不改任何前端代码**。
> 后端现状：`server/`（Spring Boot 3，端口 8080，context path `/api/v1`），E2E `PASS 77 / FAIL 0`。
> 前端现状：`web/`（Vue3 + Pinia + Vue Router），**目前 0 个接口连到 8080**。

---

## 零、一句话结论

前端现在是一个**完全本地化的演示壳**：
数据存在 `localStorage`，提交表单只弹一个 toast（代码里写着「当前为本地前端演示」），
唯一的外网请求是 `agent` 服务（8000）。**接后端 = 新增一层 service + 改两个 store 的读写点。**

---

## 一、阻断级问题（不解决就连不上，必须先定）

### 1.1 前端没有登录体系

```text
检索 web/src 全部 .vue/.ts：login / accessToken / Authorization / Bearer  → 0 命中
```

后端除 `/public/activities*` 和 `/files/**` 外，**全部接口需要 `Authorization: Bearer <jwt>`**。

> **要定的事**：登录页谁做、token 存哪（`localStorage` / `sessionStorage`）、
> 401 时怎么处理（跳登录页 + 清 token）、refresh token 怎么续期。

### 1.2 PageDocument 契约三方不一致（最危险）

同一个名字 `PageDocument`，现在有**三种互不兼容的结构**：

| 来源 | 顶层必填 | 页面内组件数组 | 组件字段 |
|---|---|---|---|
| **前端实际** `web/src/types/editor.ts` | `id,title,slug,theme,pages,meta,createdAt,updatedAt,published` | `blocks` | `type` 直接写在 block 上 |
| **后端实际校验** `server/.../schema/page-document.schema.json` | `title,pages` | `blocks` | 同上 |
| **OpenAPI 文档** `docs/api/openapi.yaml` | `schemaVersion,title,homePageId,theme,pages` | `components` | `type` 包在组件里，另有 `path` |

**后端跑 E2E 用的是中间那份（与前端一致），OpenAPI 那份是过时草稿。**

> ⚠️ **风险**：谁照着 OpenAPI 写前端（或让 Apifox 自动生成模型），发出去的文档会被后端 **422 拒绝**。
> **要定的事**：以 `server/src/main/resources/schema/page-document.schema.json` 为唯一真源，
> 把 `openapi.yaml` 里的 `PageDocument / Page / PageComponent` 三个 schema **重写**成与之一致。

### 1.3 前端 `web/src/data/` 目录缺失

19 个文件 `import from '@/data/...'`（`templates.ts`、`blockCatalog.ts`），目录不存在，Vite 直接白屏。

> **这不是后端问题**，是作者本地文件没提交。`web/` 负责人补齐。

---

## 二、接口映射表（前端函数 → 后端接口）

### 2.1 数据来源总览

| 前端模块 | 现在数据从哪来 | 应该换成 |
|---|---|---|
| `stores/projects.ts` | `localStorage` + 4 条硬编码种子 `createSeedProjects()` | `GET /projects`、`POST /projects` … |
| `stores/editor.ts` | 同上（从 projects store 取） | `GET/PUT/PATCH /projects/{id}/draft` |
| `views/PublicActivityView.vue` | `projectStore.getBySlug()` 读本地 | `GET /public/activities/{slug}` |
| 提交表单 | **没有请求，只弹 toast** | `POST /public/activities/{slug}/submissions` |
| 导出 | `services/exporters.ts`（纯前端拼 CSV） | `POST /projects/{id}/exports` + `GET /exports/{id}/download` |
| 统计 | **前端没有** | `GET /projects/{id}/analytics/summary`、`/analytics/events` |
| AI | `services/agent.ts` → `127.0.0.1:8000` | 保持不变（agent 独立） |

### 2.2 逐条映射

| # | 前端调用点 | 后端接口 | 备注 |
|---|---|---|---|
| 1 | （待新增）登录 | `POST /auth/register`<br>`POST /auth/login` | 后端已有，前端全缺 |
| 2 | （待新增）会话 | `GET /auth/me`<br>`POST /auth/refresh`<br>`POST /auth/logout` | refresh 一次性，旧的立即失效 |
| 3 | `NewProjectView` 模板选择 | `GET /templates` | **需登录**，返回 7 条 |
| 4 | `projectStore.projects` | `GET /projects?q&status&activityType&sort&page&pageSize` | 后端支持 4 个筛选参数 |
| 5 | `projectStore.createProject()` | `POST /projects` | 请求体含 4 个封面字段，可省，后端按模板推导 |
| 6 | `projectStore.getById()` | `GET /projects/{id}` | |
| 7 | `projectStore.updateProject()` | `PUT /projects/{id}` | `null` 表示保持原值，不是清空 |
| 8 | `projectStore.duplicateProject()` | `POST /projects/{id}/duplicate` | |
| 9 | `projectStore.removeProject()` | `DELETE /projects/{id}` | 软删除 |
| 10 | `editorStore` 保存 | `PUT /projects/{id}/draft` | 整体覆盖，需带 `baseRevision` |
| 11 | （建议用这个）编辑器自动保存 | **`PATCH /projects/{id}/draft`** | 只传改动部分，数组按 id 合并 |
| 12 | 版本历史（前端暂无 UI） | `GET/POST /projects/{id}/versions` | |
| 13 | `projectStore.publishProject()` | `POST /projects/{id}/publish` | 返回 `publicSlug` / `publicUrl` |
| 14 | `projectStore.unpublishProject()` | `POST /projects/{id}/unpublish` | |
| 15 | `PublicActivityView` 读活动 | `GET /public/activities/{slug}` | 匿名，已发布 |
| 16 | （前端暂无）活动发现页 | **`GET /public/activities`** | 匿名，字段白名单，**不含 draftDocument** |
| 17 | `PublicActivityView` 提交表单 | **`POST /public/activities/{slug}/submissions`** | 匿名，需 `idempotencyKey` |
| 18 | （前端暂无）提交列表 | `GET /projects/{id}/submissions` | |
| 19 | （前端暂无）数据看板 | **`GET /projects/{id}/analytics/summary`** | `byDay` / `byEventType` / `conversionRate` |
| 20 | （前端暂无）事件明细 | **`GET /projects/{id}/analytics/events`** | 分页 + `eventType` 过滤 |
| 21 | 图片上传 | **`POST /assets`** | 返回**绝对 URL**，可直接塞 `<img src>` |
| 22 | 图片读取 | `GET /files/**` | 匿名，已修复带斜杠的 key |
| 23 | `services/exporters.ts` | `POST /projects/{id}/exports`<br>`GET /exports/{id}/download` | 导出是异步任务，先建再轮询 |
| 24 | `services/agent.ts` | **不改**，仍连 `127.0.0.1:8000` | |

---

## 三、字段级差异（前后端对不上名的地方）

### 3.1 项目列表项

| 前端 `ProjectRecord` | 后端 `ProjectDto` | 说明 |
|---|---|---|
| `id: string` | `id: string` | 后端是 UUID |
| `type` | **`activityType`** | ⚠️ **名字不同** |
| `status` | `status` | 值域 `draft/published/unpublished/archived`，一致 |
| `coverTone` / `coverCode` / `coverTitle` | 同名 | ✅ 已对齐 |
| `submissionCount` | `submissionCount` | |
| `document` | **`draftDocument`** | ⚠️ **名字不同**，且后端列表里也带整份文档（体积大） |
| （无） | `publicSlug` / `publicUrl` / `publishedVersionId` | 前端从 `document.published` 拿 |
| （无） | `draftRevision` | **乐观锁必需**，前端要存 |

### 3.2 草稿保存

| 前端 | 后端 |
|---|---|
| `projectStore.saveDocument(id, doc)` 同步改内存 | `PUT /projects/{id}/draft` body 是 `{baseRevision, document}` |
| 没有版本号概念 | **`draftRevision` 必须回传**，不一致返回 **409** |

> **要定的事**：前端保存失败（409）时的 UI——是弹「已被修改，是否覆盖」，还是静默重新拉取。

### 3.3 表单提交 answers 的 key

| 前端（`BlockView.vue:110`） | 后端 |
|---|---|
| form 字段：`${block.id}:${field.id}` | 后端按同样规则取必填项 |
| radio / checkbox：**直接用 `block.id`** | 后端也认这种扁平 key |
| 无 idempotencyKey | **必须**传，同一 key 重复提交返回同一个 `submissionId` |

> **已验证**：E2E 里 `{"b2:f1":"张三","b2:f2":"2026001"}` 这套 key 后端能正确校验必填项并入库。

---

## 四、需要**前端新增**的模块（清单）

```text
web/src/services/                         ← 全部新增
├── http.ts          统一 fetch：baseURL、Bearer 注入、401 跳登录、409 提示、错误码翻译
├── auth.ts          register / login / refresh / logout / me
├── projects.ts      list / get / create / update / remove / duplicate
├── draft.ts         get / put / patch
├── publish.ts       publish / unpublish / versions
├── public.ts        activities / activitiesBySlug / submit
├── analytics.ts     summary / events
├── assets.ts        upload
└── exports.ts       create / poll / download

web/src/stores/auth.ts                    ← 新增：token + 当前用户
web/src/stores/projects.ts                ← 改：localStorage → 接口
web/src/stores/editor.ts                  ← 改：草稿从接口拉，保存走 PUT/PATCH
web/src/views/LoginView.vue               ← 新增
web/src/router/index.ts                   ← 改：加 /login + 全局路由守卫
web/.env.development                      ← 新增：VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1
```

**注意**：现有 `services/agent.ts:16` 已经用了 `VITE_AGENT_BASE_URL` 这套约定，
新的 `VITE_API_BASE_URL` 建议沿用同样写法，别硬编码。

---

## 五、联调前必须确认的顺序

1. **定 PageDocument 规范**（见 1.2）—— 三方对齐后再动代码
2. **定登录方案**（见 1.1）—— 没有 token 连 `/projects` 都调不通
3. `web/` 负责人 **补上 `web/src/data/`** —— 否则前端根本起不来
4. **后端启动**：`java -jar server-0.1.0.jar`（8080），`GET /api/v1/health` 应返回 `database.ok=true`
5. **数据库迁移**：按 [`docs/sql/README.md`](./sql/README.md) 的顺序跑完 12 个脚本。
   注意 `003_cover_fields.sql`、`004_more_templates.sql` 只是其中两个，**光跑这两个会缺表**
6. 前端加 `.env.development` 后 `pnpm dev`
7. 按 **登录 → 建项目 → 存草稿 → 发布 → 公开页提交 → 看统计** 的顺序联调

---

## 六、后端侧已就绪、无需前端担心的部分

- ✅ CORS 已配置（`WebMvcConfig`，允许 `localhost:5173`）
- ✅ 统一错误格式：`{error:{code,message,details}}`，`requestId` 可用于追日志
- ✅ 乐观锁：`baseRevision` / `draftRevision` 冲突返回 409
- ✅ 幂等提交：`idempotencyKey` 重复返回同一结果
- ✅ 文件返回绝对 URL，跨端口不会 404
- ✅ AI 未启动时 `POST /ai/chat` 返回 **503 `AGENT_UNAVAILABLE`**，前端要能降级

---

## 七、待决策清单（给团队拍板）

| # | 事项 | 选项 | 建议 |
|---|---|---|---|
| 1 | PageDocument 以谁为准 | 前端 `editor.ts` / 后端 schema / OpenAPI | **后端 schema**（已有校验，前端已一致，只需改 OpenAPI 文档） |
| 2 | 登录方式 | 邮箱密码 / 学号 / 第三方 | 邮箱密码（后端已实现） |
| 3 | token 存哪 | localStorage / sessionStorage / httpOnly cookie | localStorage + refresh（后端已实现） |
| 4 | 列表是否返回整份文档 | 返回 / 不返回 | **不返回**，列表只要摘要，进编辑器再拉详情 |
| 5 | 草稿保存用 PUT 还是 PATCH | 整体 / 局部 | **PATCH 为主**（省流量、不易误删），PUT 保留做兼容 |
| 6 | 前端 `type` 还是后端 `activityType` | 改名 / 映射 | 前端加映射函数，不动老代码 |
