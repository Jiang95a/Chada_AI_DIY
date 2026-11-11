import { apiRequest, ApiError, readData } from './http'
import type { Doc } from '@/types/editor'
import { normalizeDoc } from '@/utils/doc'

/**
 * 智能体能力统一经业务后端 /ai/**。
 * 浏览器不再直连 FastAPI 8000，鉴权、审计、超时和降级都由 8080 负责。
 */

export type AgentErrorCode = 'OFFLINE' | 'NOT_CONFIGURED' | 'FORMAT' | 'IMAGE' | 'FAILED'

export class AgentError extends Error {
  code: AgentErrorCode
  status?: number

  constructor(code: AgentErrorCode, message: string, status?: number) {
    super(message)
    this.name = 'AgentError'
    this.code = code
    this.status = status
  }
}

export interface AgentHealth {
  ok: boolean
  app?: string
  llm?: Record<string, unknown>
  tools?: string[]
}

export interface AgentToolCall {
  name: string
  result: string
}

export interface AgentReply {
  threadId: string
  reply: string
  latencyMs: number
  toolCalls: AgentToolCall[]
  /** 本次带进提示词的知识库资料条数（0 表示没命中，模型只靠常识回答） */
  ragHits: number
  /** 命中的护栏规则，例如 system_extra_blocked：说明这轮附加要求被拦了 */
  flags: string[]
}

function toAgentError(error: unknown): AgentError {
  if (error instanceof AgentError) return error
  if (error instanceof ApiError) {
    const code: AgentErrorCode =
      error.status === 0
        ? 'OFFLINE'
        : error.status === 503
          ? 'NOT_CONFIGURED'
          : error.status === 422
            ? 'IMAGE'
            : 'FAILED'
    return new AgentError(code, error.message, error.status)
  }
  return new AgentError('FAILED', error instanceof Error ? error.message : '智能体调用失败')
}

export async function probeAgent(timeoutMs = 1800): Promise<{
  ok: boolean
  health?: AgentHealth
  error?: string
}> {
  try {
    const data = readData<{ ok?: boolean }>(await apiRequest('/ai/agent/health', { method: 'GET' }, { timeoutMs }))
    const health: AgentHealth = { ok: Boolean(data.ok) }
    return { ok: health.ok, health }
  } catch (error) {
    const message = toAgentError(error).message
    return { ok: false, error: message }
  }
}

export async function askAgent(
  message: string,
  options: { threadId?: string; systemExtra?: string; timeoutMs?: number } = {}
): Promise<AgentReply> {
  const payload: Record<string, unknown> = { message }
  if (options.threadId) payload.thread_id = options.threadId
  if (options.systemExtra) payload.system_extra = options.systemExtra

  let raw: unknown
  try {
    raw = await apiRequest(
      '/ai/chat',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      { timeoutMs: options.timeoutMs ?? 60000 }
    )
  } catch (error) {
    throw toAgentError(error)
  }
  const data = readData<{
    thread_id?: string
    reply?: string
    latency_ms?: number
    tool_calls?: { name?: string; result?: string }[]
    rag_hits?: number
    flags?: string[]
  }>(raw)

  return {
    threadId: data.thread_id ?? '',
    reply: (data.reply ?? '').trim(),
    latencyMs: data.latency_ms ?? 0,
    toolCalls: (data.tool_calls ?? []).map((item) => ({
      name: item.name ?? '',
      result: item.result ?? '',
    })),
    ragHits: data.rag_hits ?? 0,
    flags: data.flags ?? [],
  }
}

/** 从模型回复里把第一个完整的 JSON 对象抠出来（允许前后有解释文字或 ``` 包裹） */
export function extractJson(text: string): unknown | null {
  if (!text) return null
  const cleaned = text.replace(/```json/gi, '```').replace(/```/g, '')
  const start = cleaned.indexOf('{')
  if (start < 0) return null

  let depth = 0
  let inString = false
  let escaped = false

  for (let index = start; index < cleaned.length; index += 1) {
    const char = cleaned[index]
    if (inString) {
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') inString = false
      continue
    }
    if (char === '"') inString = true
    else if (char === '{') depth += 1
    else if (char === '}') {
      depth -= 1
      if (depth === 0) {
        const slice = cleaned.slice(start, index + 1)
        try {
          return JSON.parse(slice) as unknown
        } catch {
          return null
        }
      }
    }
  }
  return null
}

export const PLAN_SYSTEM_EXTRA =
  '本次请求只输出 JSON 对象，不要任何解释、不要 markdown 代码块。JSON 必须符合调用方给出的结构。'

/* ------------------------------------------------------------------------- */
/* 多模态：海报 -> 结构化活动信息                                               */
/* ------------------------------------------------------------------------- */

export interface PosterInfo {
  title: string
  org: string
  startAt: string
  place: string
  deadline: string
  capacity: number | null
  fee: string
  contact: string
  intro: string
  signupFields: string[]
  /** 字段 -> 它在海报上的原文，用来让用户逐项核对，而不是盲信 AI */
  evidence: Record<string, string>
  /** 看得到但读不清的字段 */
  unclear: string[]
  confidence: number
}

export interface PosterExtractResult {
  info: PosterInfo
  warnings: string[]
  model: string
  latencyMs: number
}

export interface PosterGenerationResult {
  url: string
  prompt: string
  model: string
}

export interface AiProjectProposal {
  id: string
  jobId: string
  projectId: string
  baseRevision: number
  summary: string
  candidateDocument: Doc | null
  operations: unknown[]
  rationale: string[]
  risk: 'low' | 'medium' | 'high'
  status: 'proposed' | 'applied' | 'rejected' | 'stale'
}

export interface AiProposalApplyResult {
  revision: number
  document: Doc
  updatedAt?: string
}

function mapAiProposal(value: unknown): AiProjectProposal {
  const record = (value ?? {}) as Record<string, unknown>
  const candidate = normalizeDoc(record.candidateDocument)
  const rationale = Array.isArray(record.rationale)
    ? record.rationale.map(String)
    : typeof record.rationale === 'string'
      ? [record.rationale]
      : []
  return {
    id: String(record.id ?? ''),
    jobId: String(record.jobId ?? ''),
    projectId: String(record.projectId ?? ''),
    baseRevision: Number(record.baseRevision) || 0,
    summary: String(record.summary ?? ''),
    candidateDocument: candidate,
    operations: Array.isArray(record.operations) ? record.operations : [],
    rationale,
    risk: record.risk === 'low' || record.risk === 'high' ? record.risk : 'medium',
    status:
      record.status === 'applied' || record.status === 'rejected' || record.status === 'stale'
        ? record.status
        : 'proposed',
  }
}

/**
 * 把一张海报（data URL）发给智能体的视觉能力，换回结构化字段。
 *
 * 三种失败要分开处理，不能糊成一句"失败了"：
 *   503 NOT_CONFIGURED —— 没配视觉模型：静默退回手动填写，别骚扰用户
 *   422 IMAGE          —— 图不行：让用户换一张
 *   502 FAILED         —— 模型今天不干活：提示稍后重试
 *
 * 后端保证：认不出的字段是空字符串，绝不编。前端只负责把空着的留给用户。
 */
export async function extractPoster(dataUrl: string, timeoutMs = 90000): Promise<PosterExtractResult> {
  const remoteUrl = /^https?:\/\//i.test(dataUrl)
  let body: any
  try {
    const raw = await apiRequest(
      `/ai/poster/extract${remoteUrl ? `?url=${encodeURIComponent(dataUrl)}` : ''}`,
      {
        method: 'POST',
        body: remoteUrl ? undefined : JSON.stringify({ dataUrl }),
      },
      { timeoutMs }
    )
    body = readData<any>(raw)
  } catch (error) {
    throw toAgentError(error)
  }

  const info = (body?.info ?? {}) as PosterInfo
  return {
    info: {
      title: info.title ?? '',
      org: info.org ?? '',
      startAt: info.startAt ?? '',
      place: info.place ?? '',
      deadline: info.deadline ?? '',
      capacity: typeof info.capacity === 'number' ? info.capacity : null,
      fee: info.fee ?? '',
      contact: info.contact ?? '',
      intro: info.intro ?? '',
      signupFields: Array.isArray(info.signupFields) ? info.signupFields : [],
      evidence: info.evidence && typeof info.evidence === 'object' ? info.evidence : {},
      unclear: Array.isArray(info.unclear) ? info.unclear : [],
      confidence: typeof info.confidence === 'number' ? info.confidence : 0,
    },
    warnings: Array.isArray(body?.warnings) ? body.warnings : [],
    model: String(body?.model ?? ''),
    latencyMs: Number(body?.latencyMs ?? 0),
  }
}

/**
 * 生成活动海报底图。
 *
 * 前端只向后端提交文案和版式，不直接持有图像模型 Key。后端未配置图像模型时
 * 应返回 503，页面继续保留“上传已有海报”和本地排版能力。
 */
export async function generatePosterArtwork(
  prompt: string,
  options: { aspectRatio?: string; style?: string; timeoutMs?: number } = {}
): Promise<PosterGenerationResult> {
  let raw: unknown
  try {
    raw = await apiRequest(
      '/ai/poster/generate',
      {
        method: 'POST',
        body: JSON.stringify({
          prompt,
          aspectRatio: options.aspectRatio ?? '3:4',
          style: options.style ?? 'warm-editorial',
        }),
      },
      { timeoutMs: options.timeoutMs ?? 120000 }
    )
  } catch (error) {
    throw toAgentError(error)
  }

  const data = readData<{ url?: string; dataUrl?: string; prompt?: string; model?: string }>(raw)
  const url = data.url || data.dataUrl || ''
  if (!url) throw new AgentError('FORMAT', 'AI 没有返回可用的海报图片')
  return {
    url,
    prompt: data.prompt ?? prompt,
    model: data.model ?? '',
  }
}

/** 在已有项目中创建正式 AI 提案；不会直接修改服务端草稿。 */
export async function generateProjectAiProposal(
  projectId: string,
  prompt: string,
  baseRevision: number,
  timeoutMs = 120000
): Promise<AiProjectProposal> {
  try {
    const raw = await apiRequest(
      `/ai/projects/${encodeURIComponent(projectId)}/generate`,
      {
        method: 'POST',
        body: JSON.stringify({
          prompt,
          baseRevision,
          clientRequestId: crypto.randomUUID(),
        }),
      },
      { timeoutMs }
    )
    return mapAiProposal(readData<unknown>(raw))
  } catch (error) {
    throw toAgentError(error)
  }
}

export async function getProjectAiProposal(proposalId: string): Promise<AiProjectProposal> {
  try {
    const raw = await apiRequest(`/ai/proposals/${encodeURIComponent(proposalId)}`)
    return mapAiProposal(readData<unknown>(raw))
  } catch (error) {
    throw toAgentError(error)
  }
}

export async function applyProjectAiProposal(
  proposalId: string,
  baseRevision: number
): Promise<AiProposalApplyResult> {
  let raw: unknown
  try {
    raw = await apiRequest(`/ai/proposals/${encodeURIComponent(proposalId)}/apply`, {
      method: 'POST',
      body: JSON.stringify({ baseRevision }),
    })
  } catch (error) {
    throw toAgentError(error)
  }
  const data = readData<Record<string, unknown>>(raw)
  const document = normalizeDoc(data.document)
  if (!document) throw new AgentError('FORMAT', '后端采用提案后没有返回有效的 PageDocument')
  return {
    revision: Number(data.revision) || baseRevision + 1,
    document,
    updatedAt: typeof data.updatedAt === 'string' ? data.updatedAt : undefined,
  }
}
