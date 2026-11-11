# 心屿同行前端

校园心理社团活动组织与内容制作平台。前端使用 Vue 3，覆盖用户前台、运营后台和创作工具三个区域。

当前版本已接入 Spring Boot 与 MySQL：

- 登录、活动项目、发布、参与数据和转介使用真实接口。
- 后端不可用时，部分演示数据和本地草稿可以降级使用。
- AI 对话、海报识别统一经业务后端转发，浏览器不直连智能体服务。

## 技术栈

- Vue 3.5 + TypeScript
- Vite 8
- Vue Router 5
- Pinia
- Vitest + Vue Test Utils
- CSS 变量设计令牌，无第三方 UI 框架

Node 版本要求：

```text
^22.18.0 || >=24.12.0
```

## 启动

```powershell
cd web
corepack pnpm install
corepack pnpm dev
```

默认地址：

```text
http://127.0.0.1:5173
```

后端默认地址：

```text
http://127.0.0.1:8080/api/v1
```

如需修改，复制 `web/.env.example` 为本地环境文件并设置：

```dotenv
VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1
```

VS Code 用户也可以直接运行任务：

```text
全栈: 启动前后端
前端: 启动（本地依赖）
后端: 启动
```

## 页面与路由

路由按三种使用场景拆分。

### 用户前台

源码：`src/router/public.routes.ts`

| 路由 | 页面 |
| --- | --- |
| `/` | 首页 |
| `/campus` | 心理活动 |
| `/tools` | 心晴工具 |
| `/resources` | 心理资源 |
| `/about` | 关于心屿 |
| `/login` | 登录 |
| `/p/:slug` | 公开活动 H5 |

### 运营后台

源码：`src/router/operations.routes.ts`

| 路由 | 页面 |
| --- | --- |
| `/projects` | 运营概览与活动列表 |
| `/published` | 参与数据 |
| `/referrals` | 转介待办 |
| `/profile` | 个人资料 |
| `/settings` | 偏好设置 |
| `/help` | 帮助与反馈 |
| `/projects/:projectId/analytics` | 单活动分析 |
| `/projects/:projectId/submissions` | 参与记录 |
| `/projects/:projectId/exports` | 导出 |

`/activities` 已合并到 `/projects`，旧地址会重定向。

### 创作工具

源码：`src/router/studio.routes.ts`

| 路由 | 页面 |
| --- | --- |
| `/projects/new` | 页面工坊创建入口 |
| `/templates` | 模板库 |
| `/ai` | AI 助手 |
| `/editor/:projectId` | PageDocument 编辑器 |

## 主要功能

### 活动运营

- 运营概览统计与活动筛选。
- 活动编辑、预览、复制、删除和状态管理。
- 参与人数、匿名比例、反馈与转介汇总。
- 负责人、干事和心理委员相关操作入口。

### 页面工坊

- 13 种受控组件：导航、Banner、标题、文本、图片、信息卡片、按钮、表单、单选、多选、二维码、分割线、倒计时。
- 多页面 `PageDocument`：首页、内容页、表单页、成功页。
- 拖拽添加和排序、属性编辑、撤销重做、自动保存。
- 手机画布预览、页面发布、二维码和 Canvas 分享海报。

### AI 与海报

- AI 对话统一经 `POST /api/v1/ai/chat`。
- 已有海报识别经 `POST /api/v1/ai/poster/extract`。
- 页面工坊支持“上传已有海报”和“AI 生成海报底图”两种来源。
- AI 海报生成的前端接口为 `POST /api/v1/ai/poster/generate`，后端约定见：

  `docs/poster-generation-api.md`

- AI 只处理活动公开文案与视觉，不接收报名者隐私和匿名反馈原文。

### 设计主题

全局令牌位于 `src/assets/main.css`。

当前提供四套本机主题：

| 主题 | 气质 |
| --- | --- |
| 鼠尾草绿 | 默认，克制安静 |
| 燕麦米黄 | 暖色浅底 |
| 雾青 | 冷色、清爽 |
| 雾蓝石板 | 冷色、适合数据页面 |

主题状态由 `src/composables/usePreferences.ts` 管理，并应用到：

```html
<html data-theme="sage">
```

选择结果保存在浏览器本地存储，不影响其他成员。

## 目录结构

```text
web/
├─ src/
│  ├─ assets/                全局 CSS、设计令牌和本地活动封面
│  ├─ components/
│  │  ├─ editor/             PageDocument 编辑器组件
│  │  ├─ modals/             创建、预览、发布等弹窗
│  │  ├─ public/             公共端导航和工具组件
│  │  ├─ studio/             运营/创作工作台外框
│  │  └─ ui/                 图标、弹窗、二维码、Toast
│  ├─ composables/           偏好、隐私、Toast、工作空间
│  ├─ data/                  组件目录、模板、占位图
│  ├─ layouts/               公共端布局
│  ├─ router/                前台、运营、工具三组路由
│  ├─ services/              API、AI、海报、数据、导出
│  ├─ stores/                auth、projects、editor
│  ├─ types/                 PageDocument 等类型
│  ├─ utils/                 文档、主题、ID 工具
│  └─ views/                 页面视图
├─ public/
├─ package.json
├─ vite.config.ts
└─ vitest.config.ts
```

## PageDocument

编辑器使用受控组件结构，不允许 AI 直接生成可执行 HTML、CSS 或 JavaScript。

```ts
interface Doc {
  id: string
  title: string
  slug: string
  theme: Theme
  pages: Page[]
  meta: DocMeta
  createdAt: number
  updatedAt: number
}
```

安全边界：

- 只接受 `src/data/blockCatalog.ts` 白名单中的组件。
- 用户和 AI 文本使用 Vue 插值，不使用 `v-html` 渲染。
- 模板、AI 方案和删除操作支持撤销。
- 时间类数据按当前日期动态生成，不写死年份。

## 本地持久化

前端使用以下本地数据作为离线降级：

- 项目演示数据：`diy-campus:projects:v1`
- 个人偏好：`diy-campus:preferences:v1:*`
- 个人标签：`diy-campus:profile-tags`
- 工具页本地便签与情绪记录

真实用户、活动和参与数据仍以业务后端与 MySQL 为准。

## 测试与构建

```powershell
corepack pnpm typecheck
corepack pnpm test:unit
corepack pnpm build
```

当前测试覆盖：

- 公共端、运营端和编辑器路由冒烟测试
- 项目操作、参与数据与发布数据
- AI 对话、海报识别与海报生成接口约定
- PageDocument、隐私与知情同意
- 自定义模板和工作空间导航

## 后端接口

常用前端服务：

| 服务 | 文件 |
| --- | --- |
| HTTP 与 JWT | `src/services/http.ts` |
| 登录注册 | `src/services/auth.ts` |
| 项目与草稿 | `src/services/projects.ts`、`draft.ts` |
| AI 与海报 | `src/services/agent.ts` |
| 公开活动 | `src/services/public.ts` |
| 参与数据 | `src/services/submissions.ts` |
| 通知 | `src/services/notifications.ts` |
| 转介 | `src/services/referrals.ts` |
| 导出 | `src/services/exports.ts` |

浏览器只访问 `VITE_API_BASE_URL`，不直连 `8000` 端口。

## 开发约定

- 颜色、间距、圆角和字号优先使用 `src/assets/main.css` 的设计令牌。
- 新增页面时先判断属于前台、运营还是创作工具，并放入对应路由模块。
- 新组件优先复用 `.app-button`、`.app-card`、`.field` 等公共样式。
- 卡片避免同时堆叠边框、阴影和悬停位移。
- 图片优先使用真实活动照片；没有照片时使用本地多色占位资产。
- 涉及参与者的照片必须取得本人同意。
- 不把模型密钥写入前端，环境变量只保留 API 基础地址。
