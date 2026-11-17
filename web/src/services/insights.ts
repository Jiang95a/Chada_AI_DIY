import type { InsightEvent, InsightFacts } from '@/types/insight'
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
 * 把本机工具记录合并进后端 facts。
 *
 * <p>工具记录还没有后端表，所以不假装它是服务器数据；
 * 合并只发生在前端，`toolRecordCount` 单独计数，activityCount 不会被它抬高。
 */
export function mergeLocalToolRecords(facts: InsightFacts | null): InsightFacts | null {
  if (!facts) return null
  const records = readLocalToolRecords()
  if (!records.length) {
    return { ...facts, toolRecordCount: 0 }
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
    toolRecordCount: toolEvents.length,
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
