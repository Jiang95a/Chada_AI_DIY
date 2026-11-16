/**
 * 动态参与的核心数据模型。
 *
 * 驱动关系：用户输入 → parseTrace 抽取标签 → 更新 sessionContext
 *          → 参与页读 sessionContext 重渲染面板与可选路径。
 *
 * 注意：ParticipantTrace 必须落后端（跨设备、跨活动读取）；
 * 前端本地只做降级缓存，不作为唯一真相。
 */

export type ActivityDomain = 'mental' | 'career'

export interface TraceTag {
  content: string
  /** auto：系统从输入里抽取；user：用户自己打上 */
  source: 'auto' | 'user'
  createdAt: number
}

export interface SessionContext {
  /** 当前展示给用户的一句提示 */
  currentHint: string
  /** 当前允许走的参与路径，随输入变化 */
  availablePaths: string[]
}

export interface ParticipantTrace {
  userId: string
  activityId: string
  domain: ActivityDomain
  semester: string
  /** 用户手动编辑的一句话总结 */
  summary: string
  openQuestion?: string
  change?: string
  promise?: string
  tags: TraceTag[]
  sessionContext: SessionContext
  /** 用户主动勾选「存进我的轨迹」的便签私有副本，公开墙不读这里 */
  notes?: ParticipantNote[]
  updatedAt: number
}

/** 匿名便签：默认不进档案，公开墙用 */
export interface ParticipantNote {
  activityId: string
  text: string
  mood?: string
  at: number
  /** 用户勾选「存进我的轨迹」时生成私有副本，原便签仍匿名 */
  candidateTraceId?: string
}

/** 解析器契约：一期前端关键词规则，三期换成 agent，调用方不变 */
export interface TraceParser {
  parse(input: { text: string; trace: ParticipantTrace }): Promise<Partial<ParticipantTrace>>
}

export const emptyTrace = (
  userId: string,
  activityId: string,
  domain: ActivityDomain = 'mental'
): ParticipantTrace => ({
  userId,
  activityId,
  domain,
  semester: '',
  summary: '',
  tags: [],
  sessionContext: { currentHint: '', availablePaths: [] },
  notes: [],
  updatedAt: Date.now(),
})
