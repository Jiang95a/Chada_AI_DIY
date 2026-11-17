import { apiRequest, readData } from './http'

/**
 * 工具记录（能量牌局 / 三栏）的后端接口。
 *
 * <p>三种调用方的期望不同，函数签名就按这个来设计：
 * <ul>
 *   <li>保存时**不能**因为未登录而失败：调用方一律 catch，本机记录先落地；</li>
 *   <li>读取时允许失败：跨设备读取拿不到就继续用本机那几条；</li>
 *   <li>删除时失败要能感知：用户点了删除，结果没删掉是最糟的体验之一。</li>
 * </ul>
 */

export interface ToolRecord {
  id: string
  type: 'energy' | 'thought'
  title: string
  summary: string
  tags: string[]
  createdAtMs: number
}

export interface ToolRecordInput {
  type: 'energy' | 'thought'
  title: string
  summary?: string
  tags?: string[]
  createdAtMs?: number
}

/** 新建。未登录会抛 401——调用方应 catch 并依赖本机记录。 */
export async function createToolRecord(input: ToolRecordInput): Promise<ToolRecord> {
  return readData<ToolRecord>(
    await apiRequest('/tools/records', {
      method: 'POST',
      body: JSON.stringify({
        type: input.type,
        title: input.title,
        summary: input.summary ?? '',
        tags: input.tags ?? [],
        createdAtMs: input.createdAtMs ?? Date.now(),
      }),
    })
  )
}

/** 最近 N 天。失败返回空数组而不是抛错——读不到就退回本机数据。 */
export async function listToolRecords(days = 30): Promise<ToolRecord[]> {
  try {
    const data = readData<ToolRecord[]>(
      await apiRequest(`/tools/records?days=${encodeURIComponent(String(days))}`, {}, { timeoutMs: 12000 })
    )
    return Array.isArray(data) ? data : []
  } catch {
    return []
  }
}

/** 删除。失败会抛出，调用方应提示用户。 */
export async function deleteToolRecord(id: string): Promise<void> {
  await apiRequest(`/tools/records/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
