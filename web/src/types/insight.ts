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
 * 后端 /insights/facts 的返回，加上前端本地工具记录。
 * 后端只负责活动 / 表单 / 轨迹事实；工具记录仍是本机数据，合并时不伪造到服务器。
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
