import { ApiError, apiRequest, readData } from './http'
import type { ParticipantTrace } from '@/types/trace'

/**
 * Trace 的后端接口（一期先按这个契约接，后端未就绪时静默降级）。
 *
 * GET  /traces?activityId=xxx   → { data: ParticipantTrace | { trace: ParticipantTrace } | null }
 * PUT  /traces/:activityId      → { data: ParticipantTrace }
 *
 * 注意：后端没有这两个接口时返回 404/500/网络错误都当作「暂不可用」，
 * 不影响前端本地记录，页面会显示「只存在这台设备」。
 */

const UNSUPPORTED = new Set([0, 404, 405, 500, 501, 502, 503])

function unwrapTrace(payload: unknown): ParticipantTrace | null {
  const data = readData<ParticipantTrace | { trace?: ParticipantTrace } | null>(payload)
  if (!data) return null
  if (typeof data === 'object' && 'trace' in data) return data.trace ?? null
  return data as ParticipantTrace
}

export interface RemoteTraceSummary {
  activityId: string
  activityTitle: string
  domain: string
  semester: string
  summary: string
  tagCount: number
  updatedAt: number
}

export async function fetchRemoteTraceList(limit = 50): Promise<RemoteTraceSummary[]> {
  try {
    const payload = await apiRequest<unknown>(`/traces/list?limit=${limit}`, {}, { timeoutMs: 12000 })
    const data = readData<RemoteTraceSummary[]>(payload)
    return Array.isArray(data) ? data : []
  } catch {
    return []
  }
}

export async function deleteRemoteTrace(activityId: string): Promise<void> {
  try {
    await apiRequest(`/traces/${encodeURIComponent(activityId)}`, { method: 'DELETE' }, { timeoutMs: 12000 })
  } catch {
    // 删除失败不回滚本地删除：用户先看到“已删除”，下次同步会再对一次。
  }
}

export async function fetchRemoteTrace(activityId: string): Promise<ParticipantTrace | null> {
  try {
    const payload = await apiRequest<unknown>(
      `/traces?activityId=${encodeURIComponent(activityId)}`,
      {},
      { timeoutMs: 12000 }
    )
    return unwrapTrace(payload)
  } catch (error) {
    if (error instanceof ApiError && UNSUPPORTED.has(error.status)) return null
    return null
  }
}

export async function saveRemoteTrace(trace: ParticipantTrace): Promise<boolean> {
  try {
    await apiRequest(
      `/traces/${encodeURIComponent(trace.activityId)}`,
      {
        method: 'PUT',
        body: JSON.stringify(trace),
      },
      { timeoutMs: 12000 }
    )
    return true
  } catch (error) {
    if (error instanceof ApiError && UNSUPPORTED.has(error.status)) return false
    return false
  }
}
