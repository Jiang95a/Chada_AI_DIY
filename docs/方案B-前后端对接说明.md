# 方案 B · 前后端对接说明

> 面向：前端同学
> 由：后端同学整理
> 日期：2026-11-09
> 关联文档：`frontend-project-document.md`（前端项目文档）· `docs/前端对接清单-心理平台.md`（v1 对接清单）

---

## 〇、这份文档解决什么问题

前端发来的《心屿同行 · 前端整体项目文档》把**产品要什么**说得很清楚——角色、流程、隐私边界、视觉方向、10 条待调整项都有。但它对**接口怎么调**几乎没提：§11 服务层只列了模块名，没有字段、没有路由。

后端这边方案 B 已经做完（compile 已通过），但前端**一行都还没接**。我搜过 `web/src`，`referral` / `portfolio` / `/me/` 三个关键词零命中。

所以这份文档只做一件事：**把方案 B 的三个新能力，按你们已有的对接清单格式讲清楚**，让你们知道要改哪里、怎么改。

产品方向的部分我不重复，请以你们那份文档为准。这里只在必要处引用它的章节号。

---

## 一、先说结论

### 1.1 方案 B 新增了什么

| # | 能力 | 后端状态 | 前端状态 | 价值 |
|---|---|---|---|---|
| ① | **转介工单** `/referrals` | 已完成 | **未接** | 匿名活动里唯一能把「我需要被联系」这件事送出去的出口 |
| ② | **个人成长档案** `/me/portfolio` | 已完成 | **未接** | 学生自己看「我参加过什么」，是留存的关键 |
| ③ | **生涯模板** `/templates?kind=career` | 已完成 | **未接** | 模板页从 11 条变 15 条，心理 + 生涯双线 |

### 1.2 但有一个前提必须说清楚

**方案 B 的代码已完成验证**：三个脚本全绿，详见第九节。

对照你们文档 §19.1「已经完成」清单里那些功能所依赖的接口，它们的基线是：

```
server/e2e-test.ps1   PASS 92 / FAIL 0    ← 11-03 交付（commit ff8733b）
server/wb-test.ps1    PASS 42 / FAIL 0    ← 同上
```

方案 B（转介 / 成长档案 / 生涯模板）是那之后做的，**现已全部跑过并保持基线不变**（实测见第九节）：

- 你们可以按本文档写前端代码，接口签名是定死的
- 方案 B 已通过验证，可以作为「已验证」对外说明
- 其中 `TemplateDto.kind` 字段名的一处实现偏差已在本轮修复（见 §4.3），前端**无需额外映射**

### 1.3 你们要改的地方，按优先级排

| 优先级 | 改什么 | 工作量 | 依赖 |
|---|---|---|---|
| **P0** | 公开活动页加「希望有人联系我」入口 | 半天 | ① |
| **P1** | 组织者加「转介待办」页（带 24h 标红） | 1 天 | ① |
| **P2** | 个人中心加「我的成长档案」 | 1 天 | ② |
| **P3** | 模板页支持 career 分类 | 2 小时 | ③ |
| P4 | 模板页展示时区分心理 / 生涯标签 | 2 小时 | ③ |

---

## 二、能力一：转介工单

### 2.1 为什么做这个

你们文档 §3「明确不做清单」第 6 条和 §12 都在强调：**不承接线上心理咨询，危机情况只引导至学校心理中心**。

这带来一个真实的产品缺口：

> 一个学生参加了匿名团辅，结束后想说「我想找人说说话」，但活动是全匿名的，组织者拿到的是一堆不记名的回答。**这个人怎么被找到？**

如果放开让组织者看名单，匿名就失效了；如果什么都不做，匿名就等于「没人管」。

转介工单就是这个缺口的解法：**参与者主动勾选「希望有人联系我」，后端只存 3 个字段**（要不要被联系、怎么联系、希望什么时间），**绝不接收 `answers`**。

组织者拿到的是一条待办：「有一场**心晴团辅**，有人在**周五下午**希望能被联系，联系方式是他自己填的」。**他看得到该做什么，看不到是谁说了什么。**

顺带说一句：这个模块真正的价值在**「超过 24 小时没标记已联系就标红」**——它证明「匿名」不等于「没人管」。

### 2.2 接口

**参与者提交（无需登录）**

```
POST /public/activities/{slug}/referrals
body: {
  wantsContact: boolean       // 必填，必须为 true，否则 400
  contact?: string            // 最多 40 字，自己决定填什么
  preferredTime?: string      // 最多 60 字，例如「周五下午」
}
→ {
  referralId: string          // 只给编号，不给任何能反查身份的东西
  status: 'pending'
  createdAt: string
  message: string
}
```

**组织者待办列表（需登录，只能看自己活动下的）**

```
GET /referrals?status=pending&limit=50
→ {
  items: [{
    id: string
    activityTitle: string
    publicSlug: string
    contact: string | null
    preferredTime: string | null
    status: 'pending' | 'contacted' | 'closed'
    createdAt: string
    handledAt: string | null
    pendingHours: number      // ← 前端用它标红：> 24 就是超时
  }]
}
```

**组织者标记状态**

```
POST /referrals/{id}/status
body: { status: 'contacted' | 'closed' }
→ { id: string, status: string, handledAt: string }
```

> ⚠️ **状态机只有三态**：`pending → contacted → closed`。**没有「已了解内容」这种中间态**——那种状态会诱发窥探。传其他值一律 400。
>
> ⚠️ **`GET /referrals` 不返回 `userId`**，也不返回 `answers`。这是设计如此，不是漏了字段。

### 2.3 前端怎么改

**第一步**：`web/src/services/public.ts` 加一个函数

```ts
export async function requestReferral(
  slug: string,
  input: { wantsContact: boolean; contact?: string; preferredTime?: string }
) {
  return readData<{ referralId: string; status: string; createdAt: string; message: string }>(
    await apiRequest(
      `/public/activities/${encodeURIComponent(slug)}/referrals`,
      { method: 'POST', body: JSON.stringify(input) },
      { auth: false }
    )
  )
}
```

**第二步**：`PublicActivityView.vue` 在反馈区附近加一个入口

建议形态：一个低调的勾选项 + 可选输入框，**不要做成一键求助的大按钮**（会放大情绪，也不符合你们 §18「克制」的调性）。

```ts
// 提交转介
const wantsContact = ref(false)
const contact = ref('')
const preferredTime = ref('')

async function submitReferral(): Promise<void> {
  if (!wantsContact.value) return
  try {
    const r = await requestReferral(slug.value, {
      wantsContact: true,
      contact: contact.value.trim() || undefined,
      preferredTime: preferredTime.value.trim() || undefined,
    })
    toast.pushToast(r.message, 'success')
    wantsContact.value = false
  } catch {
    toast.pushToast('提交失败，请稍后再试', 'error')
  }
}
```

**第三步**：新建 `web/src/services/referrals.ts`

```ts
export interface ReferralTodo {
  id: string
  activityTitle: string
  publicSlug: string
  contact: string | null
  preferredTime: string | null
  status: 'pending' | 'contacted' | 'closed'
  createdAt: string
  handledAt: string | null
  pendingHours: number
}

export async function listReferrals(params?: { status?: string; limit?: number }) {
  const q = new URLSearchParams()
  if (params?.status) q.set('status', params.status)
  if (params?.limit) q.set('limit', String(params.limit))
  return readData<{ items: ReferralTodo[] }>(
    await apiRequest(`/referrals${q.toString() ? `?${q}` : ''}`)
  )
}

export async function updateReferralStatus(id: string, status: 'contacted' | 'closed') {
  return readData<{ id: string; status: string; handledAt: string }>(
    await apiRequest(`/referrals/${id}/status`, {
      method: 'POST',
      body: JSON.stringify({ status }),
    })
  )
}
```

**第四步**：组织者端加一个待办页（可以挂在 `WorkspaceShell` 侧边栏，或并入运营概览）

标红的判断很简单：

```ts
const overdue = (r: ReferralTodo) => r.status === 'pending' && r.pendingHours > 24
```

### 2.4 需要你们确认的一件事

你们文档 §12.4 写的是「组织者不能查看可定位个人的原始敏感信息」，而 `GET /referrals` 会给组织者看**单条工单**。

这**不矛盾**（工单是参与者主动提交的求助，不是「参与数据」），但评审时容易被当成前后不一致。建议在你们文档 §12 里补一句区分：

> 转介工单是参与者**主动**提交的联系意愿，不属于 §12.4 所说的「可定位个人的原始信息」；组织者看到的只有参与者自己填的联系方式和时间，看不到其活动回答内容。

---

## 三、能力二：个人成长档案

### 3.1 为什么做这个

你们文档 §1 把核心目标定在「让心理社团成员能快速发起活动」，但**学生侧**的留存逻辑一直是空白的——学生参加完一场团辅，什么都不会留下。

成长档案给的是：**「我这学期参加过 3 场心理活动、2 场生涯活动」**。它同时也是生涯场景（简历、保研材料）的天然出口。

**隐私设计**：只返回**本人**参与的已发布活动；匿名参与的只出现 `role = 'anonymous'`，**不暴露任何回答内容**。

### 3.2 接口

```
GET /me/portfolio            （需登录，只有自己那一份，没有「看别人」的接口）
→ {
  userId: string
  displayName: string
  totalActivities: number
  wellbeingCount: number      // 心理类活动数
  careerCount: number         // 生涯类活动数
  byMonth: Array<{ ... }>     // 按月聚合，用于画时间线
  items: [{
    projectId: string
    title: string
    activityType: string
    category: string
    slug: string
    publicUrl: string
    role: 'organizer' | 'participant' | 'anonymous'
    participationPath: string
    firstJoinedAt: string
    lastJoinedAt: string
    joinedCount: number
    privacyMode: 'real_name' | 'optional' | 'anonymous'
    consentRecorded: boolean
  }]
}
```

### 3.3 前端怎么改

**第一步**：新建 `web/src/services/portfolio.ts`

```ts
export interface PortfolioItem {
  projectId: string
  title: string
  activityType: string
  category: string
  slug: string
  publicUrl: string
  role: 'organizer' | 'participant' | 'anonymous'
  participationPath: string
  firstJoinedAt: string
  lastJoinedAt: string
  joinedCount: number
  privacyMode: 'real_name' | 'optional' | 'anonymous'
  consentRecorded: boolean
}

export interface Portfolio {
  userId: string
  displayName: string
  totalActivities: number
  wellbeingCount: number
  careerCount: number
  byMonth: Array<Record<string, unknown>>
  items: PortfolioItem[]
}

export async function getPortfolio() {
  return readData<Portfolio>(await apiRequest('/me/portfolio'))
}
```

**第二步**：在 `web/src/views/ProfileView.vue`（`/profile` 路由已存在）里加一块「我的参与轨迹」

建议：顶部三个数字卡片（总场次 / 心理 / 生涯），下面是按月分组的活动时间线。**匿名项显示成「匿名参加」**，不要显示具体活动里的任何回答内容。

---

## 四、能力三：生涯模板

### 4.1 为什么做这个

你们的对接清单 v1 里提到过一条：`web/src/services/templates.ts` 里那条正则 `/心理|心晴|关怀|情绪|正念|匿名|团体/` 之前**一条都匹配不到**，模板页是空的。后来补了 4 条心理模板（wellbeing）。

方案 B 又加了 4 条**生涯模板**（career），数据库里现在是：

```
career     4 条    ← 方案 B 新增
general    7 条
wellbeing  4 条
```

### 4.2 接口变化

`GET /templates` 多了一个 `kind` 参数，`TemplateDto` 多了一个 `kind` 字段：

```
GET /templates                    → 全部 15 条
GET /templates?kind=wellbeing     → 4 条心理
GET /templates?kind=career        → 4 条生涯
GET /templates?kind=general       → 7 条通用
GET /templates?category=xxx       → 原有分类过滤，仍然可用
```

`TemplateDto`：

```ts
{
  id: string
  code: string
  name: string
  category: string
  description: string
  coverUrl: string | null
  kind: 'wellbeing' | 'career' | 'general'   // ← 新增，默认 'general'
}
```

### 4.3 前端怎么改

`web/src/services/templates.ts` 里 `Template` 类型加 `kind` 字段即可。如果那正则过滤还在，建议**改成用 `kind` 过滤**，别再靠中文正则硬猜——这正是当初模板页为空的原因。

> **已修复（2026-11-09）**：后端 `TemplateDto` 早期的 JSON 字段名误写成了 `activityKind`（跟数据库列名 `activity_kind` 走了），而前端 `TemplateApiRecord` 读的是 `template.kind`。结果是远程模板的 `kind` 恒为 `undefined`，全被兜底成 `'general'`，`loadTemplateCatalog('career')` 的远程部分永远过滤为空——生涯分栏实际失效。
>
> 现已把 `Dtos.java` 的 record 分量改名为 `kind`（数据库列名不动），JSON 现在返回 `"kind": "career"`。前端无需改动即可生效。
>
> 另：`/templates` **需要登录**（不在 `AuthInterceptor.PUBLIC_PREFIXES` 白名单里），未登录会 401。前端 `apiRequest` 默认 `auth: true`，登录态下正常。

模板面板（`TemplatePanel.vue` / `CreateModal.vue`）加两个 tab：

```text
[ 心理活动 ]  [ 生涯发展 ]  [ 全部 ]
     ↓ kind=wellbeing / career / null
```

> 💡 **一个小建议**：你们文档 §13.3 列的活动类型是「团辅 / 科普讲座 / 线上分享会 / 心晴游园会 / 朋辈交流 / 心理沙龙 / 其他」，**没有生涯类**。如果用 career 模板创建活动，`activityType` 会落到「其他（心理主题）」，语义上有点别扭。看你们想不想把生涯单独列一类。

---

## 五、和你们 §19.2 待调整清单的对应关系

你们列了 10 条待调整。我这边的判断是——**其中 3 条会影响方案 B 的接入方式**，其余和本文档无关：

| 你们的待办 | 与方案 B 的关系 |
|---|---|
| ① 公共首页改为近期活动列表优先 | 无关 |
| ② 发起活动改为分页向导 | **有影响**：第一屏建议加「活动类型：心理 / 生涯」，与模板 `kind` 对齐 |
| ③ 审核状态页面 | 无关 |
| ④ 角色与可见范围控制 | **有影响**：转介待办页只有负责人 / 干事可见，依赖角色 |
| ⑤ 聚合复盘数据页 | 无关（但转介统计可以并入这页） |
| ⑥ 海报生成放进创建流程 | 无关 |
| ⑦ 隐藏或降级一期心理交互工具 | 无关 |
| ⑧ 补齐真实心理资源和危机联系方式 | **有影响**：转介工单的兜底文案应该链到这里 |
| ⑨ 弱化打卡和挑战类模板 | 无关（career 模板不是打卡类） |
| ⑩ 组织者端视觉从通用后台改为运营台 | **有影响**：转介待办是运营台天然的「待办」模块 |

**建议**：在你们 §19.2 里补第 11 条——「接入方案 B 新接口：转介工单 `/referrals`、个人成长档案 `/me/portfolio`、生涯模板 `/templates?kind=career`」。

---

## 六、数据库：接入前必须确认

方案 B 依赖 `docs/sql/009_career_and_referral.sql`。我这边已经查过**本地库已执行**：

```
activity_consents      activity_events        activity_feedback
ai_jobs                ai_proposals           auth_sessions
export_jobs            feedback_reports       form_submissions
page_versions          project_assets         projects
referral_requests      templates              user_notifications
users
```

16 张表齐全，`templates.activity_kind` 已存在且数据正确（career 4 / wellbeing 4 / general 7）。

**如果你们的环境是另一套库**，先执行：

```bash
mysql -u campus_app -p --default-character-set=utf8mb4 campus_diy < docs/sql/009_career_and_referral.sql
```

---

## 七、几个对齐点（需要你们回复）

1. **转介入口的形态**：勾选项 + 可选输入框（我建议）还是单独一个页面？前者转化率高但不够醒目，后者更郑重但要多跳一次。
2. **成长档案的入口位置**：放 `/profile` 现有页面里，还是单独开 `/portfolio` 路由？
3. **生涯模板要不要独立 activityType**：见 §4.3 的提醒。
4. **转介超时提醒**：只在前端标红，还是要同时推一条站内通知给组织者？后者我已经有通知能力，可以做，但要你们确认文案。

---

## 八、顺带修正的一处源码注释

`ReferralController.java` 的类注释里写的是 `PATCH /referrals/{id}`，但实际实现是 `POST /referrals/{id}/status`。**请以前者之外的后者为准**——即：

```
POST /referrals/{id}/status
```

我这边会同步改掉那条注释。

---

## 九、后端测试基线（2026-11-09 实测）

```text
server/e2e-test.ps1     PASS 92 / FAIL 0    ← 与基线一致，无回归
server/wb-test.ps1      PASS 42 / FAIL 0    ← 与基线一致，无回归
server/schemeb-test.ps1 PASS 32 / FAIL 0    ← 本轮新增，专测方案 B 三个接口
```

方案 B 状态：**编译通过，三个脚本全绿**。

`schemeb-test.ps1` 覆盖：

| 分组 | 覆盖点 |
|---|---|
| `/templates` | 未登录 401；`kind=career/wellbeing/general` 分别 4/4/7 条且三者相加=15；每条 `kind` 正确；单条详情 |
| `/me/portfolio` | 未登录 401；新用户 `totalActivities=0`；`items`/`byMonth`/`wellbeingCount`/`careerCount`/`displayName` 字段齐全且 `displayName` 非空 |
| `/referrals` | 未登录 401；`items` 数组；新用户为空；`?status=` / `?limit=` 过滤；不存在工单 404 |
| `/public/.../referrals` | 匿名可提交，返回 `referralId` + `status=pending`；`wantsContact=false` 缺联系方式 → 400 |

注：`/templates`、`/me/portfolio`、`/referrals` 都需登录（`AuthInterceptor` 白名单只有 `/health`、`/auth/*`、`/public/`、`/files/`）。

