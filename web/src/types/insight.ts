export interface InsightEvent {
  source: 'activity' | 'form' | 'tool'
  activityId: string
  activityTitle: string
  at: number
  kind: string
  tags: string[]
  summary?: string | null
  change?: string | null
}

export interface InsightActivity {
  activityId: string
  activityTitle: string
  domain: string
  lastAt: number
  eventCount: number
  tags: string[]
  summary: string
}

export interface InsightForm {
  activityId: string
  activityTitle: string
  submittedAt: string | null
  status: string
}

/**
 * 后端 /insights/facts 的返回。
 *
 * 注：工具记录（能量牌局 / 三栏）现在**已经落库**，由后端一并返回，
 * 所以 `toolRecordCount` 是服务端给的数字，前端不再自己造。
 * 本机 localStorage 里的旧记录仍然要合并进来（老用户换设备前的数据），
 * 合并规则见 `services/insights.ts` 的 mergeLocalToolRecords。
 */
export interface InsightFacts {
  periodStart: string
  periodEnd: string
  generatedAt: string
  activityCount: number
  formCount: number
  traceCount: number
  eventCount: number
  toolRecordCount?: number
  activities: InsightActivity[]
  events: InsightEvent[]
  forms: InsightForm[]
  tagCounts: Record<string, number>
}

/* ------------------------------------------------------------------------- */
/* 回看分析（POST /insights/analyze）                                          */
/* ------------------------------------------------------------------------- */

export interface InsightEvidence {
  at: string
  activityTitle: string
  quote?: string | null
}

export interface InsightFinding {
  text: string
  evidence: InsightEvidence[]
}

export interface InsightBody {
  title: string
  findings: InsightFinding[]
  change: string
  nextStep: string
  question: string
  confidence: 'low' | 'medium' | 'high'
  visualPreset: string
  limits: string
}

export interface InsightResource {
  label: string
  value: string
}

/**
 * 回看分析的四种结局。
 *
 * `status` 决定渲染哪一屏：ok 显示回看正文；insufficient_data 显示引导；
 * crisis **只显示求助资源**、不显示任何分析；analysis_error 显示重试。
 * 服务端对这四种都返回 200，用状态码区分会让"危机"变成错误提示。
 */
export type InsightAnalyzeStatus = 'ok' | 'insufficient_data' | 'crisis' | 'analysis_error'

export interface InsightAnalyzeResponse {
  status: InsightAnalyzeStatus
  insightId?: string
  generatedAt?: string
  model?: string
  promptVersion?: string
  insight?: InsightBody
  message?: string
  resources?: InsightResource[]
}

