import type { InsightAnalyzeResponse, InsightEvent, InsightFacts } from '@/types/insight'
import { apiRequest, readData } from './http'

const TOOL_TRACE_KEY = 'diy-campus:tools-trace:v1'

interface LocalToolRecord {
  type: string
  title: string
  meta: string
  time: string
  at?: number
  tags?: string[]
}

function readLocalToolRecords(): LocalToolRecord[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(TOOL_TRACE_KEY) || '[]') as unknown
    return Array.isArray(parsed) ? (parsed as LocalToolRecord[]).slice(0, 50) : []
  } catch {
    return []
  }
}

/**
 * 把本机旧工具记录合并进后端 facts。
 *
 * <p><b>注意两件事</b>：
 * <ol>
 *   <li>工具记录现在已经有后端表（tool_records），后端 facts 里本来就带 `toolRecordCount`
 *       和 `source: "tool"` 的事件；这里只补**本机遗留**的那些，不能覆盖服务端的数字，
 *       否则「换设备后能看到记录」这件事在数字上会被抹掉。</li>
 *   <li>合并只发生在前端，activityCount 永远不被抬高——工具记录不是参加了一场活动。</li>
 * </ol>
 */
export function mergeLocalToolRecords(facts: InsightFacts | null): InsightFacts | null {
  if (!facts) return null
  const serverCount = typeof facts.toolRecordCount === 'number' ? facts.toolRecordCount : 0
  const records = readLocalToolRecords()
  if (!records.length) {
    return { ...facts, toolRecordCount: serverCount }
  }

  const now = Date.now()
  const toolEvents: InsightEvent[] = records.map((record) => ({
    source: 'tool',
    activityId: `tool:${record.type}`,
    activityTitle: record.title,
    at: typeof record.at === 'number' && record.at > 0 ? record.at : now,
    kind: record.type === '能量牌局' ? 'energy' : 'thought',
    tags: Array.isArray(record.tags) ? record.tags : [],
    summary: record.meta,
    change: null,
  }))

  const tagCounts = { ...facts.tagCounts }
  for (const event of toolEvents) {
    for (const tag of event.tags) {
      tagCounts[tag] = (tagCounts[tag] ?? 0) + 1
    }
  }

  return {
    ...facts,
    eventCount: facts.eventCount + toolEvents.length,
    toolRecordCount: serverCount + toolEvents.length,
    events: [...facts.events, ...toolEvents].sort((a, b) => a.at - b.at),
    tagCounts,
  }
}

/** 聚合接口失败（未登录、时间线不可用）时返回 null，页面继续用本地轨迹。 */
export async function fetchInsightFacts(days = 30): Promise<InsightFacts | null> {
  try {
    const payload = await apiRequest<unknown>(
      `/insights/facts?days=${encodeURIComponent(String(days))}`,
      {},
      { timeoutMs: 12000 }
    )
    const data = readData<InsightFacts>(payload)
    if (!data || typeof data.periodStart !== 'string') return null
    return mergeLocalToolRecords(data)
  } catch {
    return null
  }
}

/**
 * 让 AI 回看这段时间。
 *
 * <p><b>注意它和别的接口不一样</b>：数据不足 / 危机 / 模型失败都算正常结局，
 * 服务端一律 200，用返回体的 `status` 区分。所以这里不把那些当异常抛出去，
 * 只有网络层失败（连不上、401、超时）才会 reject——调用方据此区分
 * 「有结论但结论是危机」和「根本没连上」。
 *
 * <p>超时给到 70 秒：服务端可能重试一次模型调用，单次上限 60 秒。
 */
export async function analyzeInsight(
  days = 30,
  options: { allowQuote?: boolean; timeoutMs?: number } = {}
): Promise<InsightAnalyzeResponse> {
  const payload = await apiRequest<unknown>(
    '/insights/analyze',
    {
      method: 'POST',
      body: JSON.stringify({
        days,
        allowQuote: options.allowQuote ?? true,
        locale: 'zh-CN',
      }),
    },
    { timeoutMs: options.timeoutMs ?? 70000 }
  )
  return readData<InsightAnalyzeResponse>(payload)
}
