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
