# 数据库迁移顺序（唯一权威）

> 这份文件回答一个问题：**一个新库 / 一个旧库，到底该按什么顺序跑哪些脚本。**
> 根目录 `README.md` 和其它文档里的说法如果和这里冲突，以这里为准。

## 为什么需要这份文件

1. **编号有重号**：仓库里同时存在 `003_cover_fields.sql` 和 `003_seed_fix.sql`，
   也有 `004_more_templates.sql` 和 `004_projects_cover_columns.sql`。
   文件名排序无法唯一确定执行顺序。
2. **`001_init.sql` 不是终点**：它只建了 13 张表，`007`（心理关怀隐私）和 `009`（生涯与转介）
   建的表**不在里面**。新库跑完 `001` 仍然缺表。
3. **两个 cover 迁移定义不一致**：见下方「封面四列」一节，这是重号的真实代价。

## 一、新库（从零开始）

按顺序全部执行，**一个都不要跳过**。所有脚本都是幂等的，重复执行安全。

```bash
# 00 由 root 执行（建库 + 建 campus_app 账号）
mysql -u root -p --default-character-set=utf8mb4 < docs/sql/00_create_database.sql

# 以下全部由 campus_app 执行
for f in 001_init 002_seed 003_cover_fields 003_seed_fix 004_more_templates \
         004_projects_cover_columns 005_demo_data 006_feedback \
         007_wellbeing_privacy 008_wellbeing_templates 009_career_and_referral \
         010_poster_generation 011_participant_traces 012_insight_agent \
         013_hide_general_templates 014_demo_wellbeing_activities \
         015_cleanup_demo_projects; do
  mysql -u campus_app -p --default-character-set=utf8mb4 campus_diy < "docs/sql/$f.sql"
done
```

跑完自查，应该是 **19 张表**：

```sql
SHOW TABLES;
-- 期望：activity_consents activity_events activity_feedback ai_jobs ai_proposals
--       auth_sessions export_jobs feedback_reports form_submissions insight_runs
--       page_versions participant_traces project_assets projects referral_requests
--       templates tool_records user_notifications users
```

## 二、旧库（已经建好，只想补缺的）

同样按上面的顺序**全部重跑一遍**即可 —— 每个脚本都会先查 `information_schema`
再决定要不要执行，已经存在的列、表、索引会跳过。

唯一例外是 `002_seed.sql` / `003_seed_fix.sql` / `005_demo_data.sql` 这三个**演示数据**
脚本：它们会写入演示用户和演示项目。线上环境不要跑，本地/演示环境可以重跑
（用固定 UUID + `ON DUPLICATE KEY UPDATE`，不会重复插入）。

## 三、编号重号的处理决定

**不重命名文件**，理由：

- `README.md:29-30`、`frontend-backend-handoff.md:168`、`server/e2e-test.ps1:375`
  三处引用了这些文件名，改名会断引用。
- 更关键的是：**已经跑过 `003_cover_fields.sql` 的老库，改名后会把新名字当成没跑过的迁移**，
  于是重跑一次。虽然现在幂等不会炸，但这是没必要的风险。
- 真正的问题是「排序不确定」，而排序由本文件解决，不需要动文件名。

## 四、封面四列（重号的真实代价）

`projects` 表的 `activity_type` / `cover_tone` / `cover_code` / `cover_title`，
仓库里有 **三处不同定义**：

| 来源 | `activity_type` 定义 | 可空 |
| --- | --- | --- |
| `001_init.sql:84-87` | `VARCHAR(40) NOT NULL DEFAULT '校园活动'` | NOT NULL |
| `004_projects_cover_columns.sql:21` | `VARCHAR(40) NOT NULL DEFAULT '校园活动'` | NOT NULL |
| `003_cover_fields.sql:27` | `VARCHAR(64)` | NULL |
| **当前线上库实际** | `varchar(64)` | **NULL** |

也就是说：**按 `001_init.sql` 新建的库，和当前线上库，表结构不一样。**

- 当前代码不受影响：`ProjectEntity` 里这几个字段都是 `String`，NULL 与空串都能读。
- 但新建环境会得到 `NOT NULL DEFAULT` 的版本，写入 NULL 会失败；
  线上则是可空版本。这个差异目前没被任何测试覆盖。

**待决事项**（需要产品/后端一起定，不是单人能改的）：

- 选 A：把 `001_init.sql` 改成 `VARCHAR(64) NULL`，让新库与线上对齐（改动小，但放弃 NOT NULL 约束）。
- 选 B：写一个 `011_cover_columns_not_null.sql`，先回填存量 NULL 值再改 `NOT NULL`，
  让线上对齐 `001_init.sql`（改动大，但约束更强）。

在决定之前，`003_cover_fields.sql` 已改为**幂等**（原来四条裸 `ALTER TABLE ... ADD COLUMN`
重跑必报 `Duplicate column name` 中断），并显式跳过已存在的列，不会再把线上库改成第三种状态。

## 五、编号速查

| 文件 | 类型 | 内容 |
| --- | --- | --- |
| `00_create_database.sql` | DDL | 建库 + 建 `campus_app` 账号（root 执行） |
| `001_init.sql` | DDL | 13 张基础表 |
| `002_seed.sql` | DML | 演示用户 / 模板 |
| `003_cover_fields.sql` | DDL | `projects` 补封面四列（宽松版，可空） |
| `003_seed_fix.sql` | DML | 修 002 的坏数据（BCrypt 占位、组件属性名、空页面） |
| `004_more_templates.sql` | DML | 再补 6 套模板（UUID 105~110；**不要用 101~104，那是 003_seed_fix 的号段**） |
| `004_projects_cover_columns.sql` | DDL | 封面四列（严格版，NOT NULL）—— 与 `003_cover_fields` 二选一 |
| `005_demo_data.sql` | DML | 演示项目 |
| `006_feedback.sql` | DDL | `feedback_reports`（老库必补） |
| `007_wellbeing_privacy.sql` | DDL | `activity_consents` / `activity_feedback` / `user_notifications` + 隐私字段 |
| `008_wellbeing_templates.sql` | DML | 心理关怀模板 |
| `009_career_and_referral.sql` | DDL | `referral_requests` + 模板 `activity_kind` + 提交归属 `user_id` |
| `010_poster_generation.sql` | DDL | `ai_jobs` 的 `kind` 约束加入 `poster_generate` |
| `011_participant_traces.sql` | DDL | 新增 `participant_traces`：参与端动态参与轨迹，主键 (user_id, project_id) |
| `012_insight_agent.sql` | DDL | 新增 `tool_records`（能量牌局/三栏落库）与 `insight_runs`（回看调用记录，兼 24h 缓存） |
| `013_hide_general_templates.sql` | DML | 停用通用模板，模板库只保留心理活动 / 生涯场景 |
| `014_demo_wellbeing_activities.sql` | DML | 四场内容完整的心理活动（已发布 + 配套版本），避免新库打开广场是空白页 |
| `015_cleanup_demo_projects.sql` | DML | 软删四场通用演示活动、修复版本链接、给游园会补 8 条演示提交 |
