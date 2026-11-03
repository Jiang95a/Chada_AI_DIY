# 心屿同行 · 心理社团后端需求与接口整合文档

> 版本：v1  
> 对应前端：Vue 3 `web/`  
> 适用范围：心理社团活动、匿名参与、知情同意、参与数据、活动反馈、候补与安全边界

## 1. 定位与边界

心屿同行是校园心理社团活动服务平台，不是心理诊疗、心理测评或 AI 心理咨询产品。

后端必须保证：

1. 不生成、不存储抑郁、焦虑等诊断结论。
2. 不对参与者进行个体心理风险评分。
3. 匿名活动不能暗中保存可识别身份的信息。
4. 组织者只能查看活动所需的必要信息。
5. 用户提交前能够明确知道信息用途、查看范围与保留时间。
6. 活动删除、活动结束或超过保留期限后，敏感数据应按策略清理。

## 2. 前端已经完成、等待后端落库的能力

| 前端能力 | 当前实现 | 后端必须补什么 |
|---|---|---|
| 隐私模式 | 编辑器本地保存匿名/可选匿名/实名 | 项目隐私配置表或项目字段 |
| 知情同意 | 公开页勾选后才允许提交 | 同意版本、时间、提交记录 |
| 信息保留时间 | 编辑器和公开页展示 | 到期清理任务与删除时间 |
| 是否允许导出 | 编辑器配置 | 导出接口强制权限校验 |
| 组织者信息 | 本地配置与公开页展示 | 组织者资料表与公开字段 |
| 加入日历 | 前端生成 `.ics` | 无需后端接口 |
| 活动后反馈 | 本机保存 | 匿名反馈表和聚合统计接口 |
| 匿名/实名展示 | 前端文案和标识 | 服务端字段过滤与权限校验 |

## 3. 数据库修改建议

### 3.1 `projects` 增加字段

```sql
ALTER TABLE projects
  ADD COLUMN privacy_mode VARCHAR(20) NOT NULL DEFAULT 'optional',
  ADD COLUMN consent_required TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN consent_version VARCHAR(20) NOT NULL DEFAULT 'v1',
  ADD COLUMN consent_text TEXT NULL,
  ADD COLUMN data_retention_days INT NOT NULL DEFAULT 30,
  ADD COLUMN export_allowed TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN organizer_profile_id CHAR(36) NULL,
  ADD COLUMN allow_waitlist TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN allow_leave_anytime TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN has_quiet_space TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN feedback_enabled TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN data_delete_after DATETIME NULL;
```

`privacy_mode` 只允许：

```text
real_name | optional | anonymous
```

`data_retention_days = 0` 表示长期保留，但必须要求组织者填写明确用途，并在管理端显示风险提醒。

### 3.2 新增 `organizer_profiles`

```sql
CREATE TABLE organizer_profiles (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  organization VARCHAR(120) NOT NULL,
  intro VARCHAR(500) NULL,
  avatar_url VARCHAR(500) NULL,
  contact_public VARCHAR(200) NULL,
  is_verified TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT fk_organizer_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

公开活动只返回允许公开展示的字段，不能返回用户邮箱、登录账号或内部权限信息。

### 3.3 新增 `activity_consents`

```sql
CREATE TABLE activity_consents (
  id CHAR(36) PRIMARY KEY,
  project_id CHAR(36) NOT NULL,
  submission_id CHAR(36) NULL,
  consent_version VARCHAR(20) NOT NULL,
  consent_text_snapshot TEXT NOT NULL,
  accepted_at DATETIME NOT NULL,
  client_request_id VARCHAR(100) NULL,
  ip_hash VARCHAR(128) NULL,
  UNIQUE KEY uk_consent_request (project_id, client_request_id),
  KEY idx_consent_project_time (project_id, accepted_at),
  CONSTRAINT fk_consent_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
```

必须保存用户提交时看到的同意文本快照，不能只保存最新文案。

### 3.4 `form_submissions` 增加字段

```sql
ALTER TABLE form_submissions
  ADD COLUMN privacy_mode_snapshot VARCHAR(20) NOT NULL DEFAULT 'optional',
  ADD COLUMN consent_id CHAR(36) NULL,
  ADD COLUMN anonymous_token VARCHAR(100) NULL,
  ADD COLUMN registration_status VARCHAR(20) NOT NULL DEFAULT 'confirmed',
  ADD COLUMN checkin_status VARCHAR(20) NOT NULL DEFAULT 'not_checked_in',
  ADD COLUMN checked_in_at DATETIME NULL,
  ADD COLUMN data_delete_after DATETIME NULL;
```

`registration_status`：

```text
confirmed | waitlisted | cancelled
```

`checkin_status`：

```text
not_checked_in | checked_in | no_show
```

匿名提交必须使用服务端生成的 `anonymous_token` 做取消、签到或反馈关联，不能把姓名、手机号、学号作为匿名记录的隐藏主键。

### 3.5 新增 `activity_feedback`

```sql
CREATE TABLE activity_feedback (
  id CHAR(36) PRIMARY KEY,
  project_id CHAR(36) NOT NULL,
  submission_id CHAR(36) NULL,
  experience_level VARCHAR(20) NOT NULL,
  suggestion VARCHAR(500) NULL,
  created_at DATETIME NOT NULL,
  delete_after DATETIME NULL,
  KEY idx_feedback_project_time (project_id, created_at),
  CONSTRAINT fk_feedback_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
```

`experience_level` 只允许：

```text
helpful | okay | not_fit
```

禁止把反馈转换成参与者个人情绪评分或风险标签。

### 3.6 新增 `activity_notifications`

```sql
CREATE TABLE activity_notifications (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  project_id CHAR(36) NULL,
  type VARCHAR(40) NOT NULL,
  title VARCHAR(160) NOT NULL,
  content VARCHAR(500) NOT NULL,
  target_path VARCHAR(300) NULL,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  KEY idx_notification_user_read (user_id, read_at, created_at)
);
```

建议类型：

```text
activity_reminder
registration_confirmed
waitlist_promoted
activity_changed
activity_cancelled
feedback_received
export_ready
```

## 4. 必须新增或修改的接口

### 4.1 获取活动隐私设置

```http
GET /api/v1/projects/{projectId}/privacy
Authorization: Bearer <accessToken>
```

返回：

```json
{
  "data": {
    "privacyMode": "optional",
    "consentRequired": true,
    "consentVersion": "v1",
    "consentText": "参与前请了解……",
    "retentionDays": 30,
    "exportAllowed": false,
    "organizerProfileId": "uuid",
    "allowWaitlist": true,
    "allowLeaveAnytime": true,
    "hasQuietSpace": true,
    "feedbackEnabled": true
  }
}
```

### 4.2 更新活动隐私设置

```http
PUT /api/v1/projects/{projectId}/privacy
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求体：

```json
{
  "privacyMode": "optional",
  "consentRequired": true,
  "consentText": "参与前请了解……",
  "retentionDays": 30,
  "exportAllowed": false,
  "allowWaitlist": true,
  "allowLeaveAnytime": true,
  "hasQuietSpace": true,
  "feedbackEnabled": true
}
```

### 4.3 公开活动接口增加隐私字段

```http
GET /api/v1/public/activities/{slug}
```

返回体在现有 `activity + document` 基础上增加：

```json
{
  "privacy": {
    "mode": "optional",
    "consentRequired": true,
    "consentVersion": "v1",
    "consentText": "参与前请了解……",
    "allowLeaveAnytime": true,
    "hasQuietSpace": true,
    "retentionDays": 30
  },
  "organizer": {
    "displayName": "心屿心理协会",
    "intro": "我们组织校园心理社团活动……",
    "avatarUrl": null,
    "verified": true
  }
}
```

### 4.4 公开提交

```http
POST /api/v1/public/activities/{slug}/submissions
```

建议请求体：

```json
{
  "pageId": "page-id",
  "formComponentId": "form-id",
  "answers": {
    "form-id:name": "昵称"
  },
  "idempotencyKey": "uuid",
  "privacyMode": "optional",
  "consent": {
    "required": true,
    "accepted": true,
    "version": "v1",
    "textSnapshot": "用户提交前看到的文本"
  }
}
```

服务端校验：

1. `consentRequired=true` 时，未同意必须返回 `422`。
2. `privacyMode=anonymous` 时，禁止接收姓名、手机号、学号、邮箱等身份字段。
3. 可选字段未填写时不能补默认值猜测。
4. 必须保存同意文本快照和提交时的隐私模式。
5. 达到容量时按 `allowWaitlist` 决定进入候补或返回 `409 ACTIVITY_FULL`。

### 4.5 取消报名

```http
POST /api/v1/public/activities/{slug}/submissions/{submissionId}/cancel
```

匿名活动也可以取消，但只能使用提交返回的匿名凭证或匿名 token，不能要求重新输入真实身份。

### 4.6 候补列表

```http
GET /api/v1/projects/{projectId}/waitlist?page=1&pageSize=20
Authorization: Bearer <accessToken>
```

候补转正接口：

```http
POST /api/v1/projects/{projectId}/waitlist/{submissionId}/promote
```

### 4.7 签到

```http
POST /api/v1/projects/{projectId}/submissions/{submissionId}/check-in
Authorization: Bearer <accessToken>
```

匿名活动只能显示匿名编号和签到状态，不得通过签到接口反推身份。

### 4.8 活动反馈

```http
POST /api/v1/public/activities/{slug}/feedback
```

请求体：

```json
{
  "submissionId": "optional-submission-id",
  "experienceLevel": "helpful",
  "suggestion": "希望下次增加安静休息区"
}
```

组织者查看聚合反馈：

```http
GET /api/v1/projects/{projectId}/feedback/summary
Authorization: Bearer <accessToken>
```

只返回聚合数据：

```json
{
  "total": 18,
  "helpful": 11,
  "okay": 5,
  "notFit": 2,
  "suggestions": [
    "希望增加安静休息区",
    "活动时间可以再长一点"
  ]
}
```

### 4.9 导出权限

```http
POST /api/v1/projects/{projectId}/exports
```

服务端必须检查：

1. `exportAllowed=true`。
2. 当前用户是项目所有者或有导出权限的管理员。
3. 匿名活动不导出姓名、手机号、学号等身份字段。
4. 导出内容遵守 `retentionDays` 与字段白名单。

### 4.10 通知中心

```http
GET /api/v1/notifications
PATCH /api/v1/notifications/{notificationId}/read
POST /api/v1/notifications/read-all
```

替换前端当前固定的通知数据。

## 5. 权限模型

建议至少区分：

| 角色 | 权限 |
|---|---|
| owner | 管理项目、隐私设置、导出、查看全部活动必要字段 |
| activity_manager | 编辑活动、查看聚合数据和必要报名字段 |
| checkin_staff | 只查看签到状态和匿名编号 |
| participant | 提交、取消、反馈、查看自己的参与状态 |
| public | 浏览公开活动、同意说明、报名 |

匿名活动不能给 `checkin_staff` 返回姓名或联系方式。

## 6. 数据清理与安全

1. 每天执行一次到期清理任务。
2. 过期报名信息转为不可恢复的匿名聚合数据。
3. 删除活动后，相关反馈、同意记录和导出文件必须同步处理。
4. 导出文件设置过期时间和下载次数限制。
5. 日志中不得记录完整手机号、邮箱、表单敏感答案。
6. 匿名 token 必须随机生成，不能由用户 ID 或时间推导。
7. 前端配置仅作为交互预设，服务端必须再次校验，不能信任前端传值。

## 7. AI 安全要求

AI 只用于活动组织辅助：

1. 不生成心理诊断。
2. 不推断参与者是否存在心理风险。
3. 不根据表单答案自动联系辅导员或危机干预人员。
4. AI 生成表单时默认只使用最小必要字段。
5. AI 文案检查应指出：
   - 强迫分享
   - 污名化表达
   - 匿名与实名冲突
   - 缺少退出、隐私和求助说明

## 8. 状态与生命周期

建议项目状态：

```text
draft
scheduled
registration_open
registration_closed
full
waitlist_open
in_progress
ended
archived
```

状态可以根据 `startAt`、`endAt`、`deadline`、`capacity` 和报名数量计算，但发布与结束状态仍应由服务端保存。

## 9. 验收标准

1. 匿名活动提交后，数据库不存在可直接关联身份的姓名、手机号、学号和邮箱。
2. 未勾选知情同意时，提交接口返回 `422`。
3. 组织者只能看到其权限范围内的字段。
4. 达到容量后能够进入候补或返回明确错误。
5. 活动结束或超过保留期限后，报名信息按规则清理。
6. 匿名活动导出不包含身份字段。
7. 活动反馈只返回聚合结果，不返回心理评分。
8. 公开活动接口不泄露草稿、内部用户信息和权限配置。
9. 用户取消报名后，候补人员可以按顺序递补。
10. 所有隐私设置变更都有审计记录。

## 10. 实施优先级

### P0

- 隐私字段
- 知情同意记录
- 匿名提交字段校验
- 导出权限
- 到期清理

### P1

- 候补队列
- 签到
- 活动反馈
- 真实通知
- 组织者资料

### P2

- 活动状态自动计算
- 反馈聚合分析
- 权限细分与审计报表

