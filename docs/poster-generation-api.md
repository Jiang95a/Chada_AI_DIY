# AI 海报生成接口约定

前端页面工坊已经提供“上传已有图片”和“AI 生成海报”两种入口。上传、识别和图片资产上传已有接口；AI 生成底图需要后端与智能体补一个接口。

> **实现状态**：接口已实现并验证（`server` 的 `PosterService` + `agent` 的 `/poster/generate`）。当前环境没有配置图像模型，实际返回 503，前端应引导用户改用上传。配置 `agent/.env` 里的 `IMAGE_MODEL` 与 `IMAGE_API_KEY` 后重启 agent 即可真实出图，无需改代码。

## 请求

```http
POST /api/v1/ai/poster/generate
Authorization: Bearer <JWT>
Content-Type: application/json
```

```json
{
  "prompt": "考试周心晴游园会，傍晚校园草坪，暖米色背景与低饱和绿色植物，留出活动信息区域",
  "aspectRatio": "3:4",
  "style": "warm-editorial-campus",
  "clientRequestId": "可选，同一 prompt 重试时带上可避免重复付费",
  "projectId": "可选，不传则用当前用户最近的活动项目"
}
```

`aspectRatio` 支持 `3:4`（默认）/`4:3`/`1:1`/`16:9`/`9:16`，非法值归一到 `3:4`。
`style` 支持 `warm-editorial-campus`（默认）/`minimal-calm`/`photo-soft`，非法值归一到默认值。

## 成功响应

```json
{
  "data": {
    "jobId": "任务 ID",
    "url": "http://127.0.0.1:8080/api/v1/files/assets/xxx/poster-yyy.png",
    "prompt": "实际使用的提示词",
    "model": "图像模型名称",
    "aspectRatio": "3:4",
    "latencyMs": 8400,
    "cached": false
  }
}
```

`url` 是本服务 `/files/` 下的地址（该前缀在鉴权白名单里，浏览器可直接加载，不需要带 token）。
上游给的是临时地址时，agent 会先下载转存，避免过几天变裂图。

## 错误

| 状态码 | code | 含义 | 前端处理 |
| --- | --- | --- | --- |
| `401` | `UNAUTHORIZED` | 未登录或 JWT 失效 | 跳转登录 |
| `400` | `INVALID_INPUT` | 参数不合法（如 prompt 少于 4 字） | 提示补全描述 |
| `403` | `FORBIDDEN` | 指定的 `projectId` 不属于当前用户 | 不应出现，属调用方 bug |
| `503` | `IMAGE_GENERATION_UNAVAILABLE` | 未配置图像模型或智能体不可用 | 提示用户改用上传图片 |
| `422` | `IMAGE_PROMPT_INVALID` | 提示词不合规（含手机号、学号等号码） | 展示可理解的修改建议 |
| `502` | `IMAGE_GENERATION_FAILED` | 上游图像服务失败 | 提示稍后重试，不自动重复扣费 |

`422` 与 `400` 刻意分开：`400` 是「请求根本没法处理」，`422` 是「请求能处理但内容不接受」，
前端据此引导用户改提示词而不是刷新重来。

## 安全与隐私

- 图像模型密钥只能保存在后端，不能下发到浏览器。
- 默认只提交公开的活动视觉描述，不提交报名者信息、匿名反馈原文或个人敏感信息。
- 提示词会挡掉「身份证/学号/手机号/银行卡/密码」等字面词，以及手机号、9 位以上数字串（学号、银行卡号）。
- 同一 `prompt` 的重试应带幂等键，避免用户重复点击产生多张付费图片。
  幂等键落在 `ai_jobs.client_request_id`，配合唯一索引 `uq_ai_jobs_user_client`，
  同一用户同一 `clientRequestId` 命中已成功任务时直接返回 `cached: true` 的原图。
- 返回图片需要记录来源、模型、生成时间和任务 ID，方便运营排查：
  每次调用（含失败）都写一行 `ai_jobs`，`kind='poster_generate'`，失败时记 `error_code` 与 `error_message`。

