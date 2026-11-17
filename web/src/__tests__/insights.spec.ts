import { describe, expect, it, beforeEach } from 'vitest'
import { mergeLocalToolRecords } from '@/services/insights'
import type { InsightFacts } from '@/types/insight'

function facts(): InsightFacts {
  return {
    periodStart: '2026-10-19',
    periodEnd: '2026-11-17',
    generatedAt: '2026-11-17T00:00:00.000Z',
    activityCount: 2,
    formCount: 1,
    traceCount: 2,
    eventCount: 1,
    activities: [],
    events: [
      {
        source: 'activity',
        activityId: 'photo-club',
        activityTitle: '摄影社招新',
        at: 1000,
        kind: 'input',
        tags: ['压力'],
        summary: '最近压力大',
        change: null,
      },
    ],
    forms: [],
    tagCounts: { 压力: 1 },
  }
}

describe('insight facts 本地工具合并', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('把本机工具记录合并成 tool 事件，不抬高活动数', () => {
    localStorage.setItem(
      'diy-campus:tools-trace:v1',
      JSON.stringify([
        { type: '能量牌局', title: '今天先放下：社团活动', meta: '怕别人失望', time: '10:00', at: 2000, tags: ['怕别人失望'] },
        { type: '想法捕捉', title: '他们觉得我没能力', meta: '感受：胸口紧', time: '10:05', at: 3000 },
      ])
    )

    const merged = mergeLocalToolRecords(facts())
    expect(merged).not.toBeNull()
    expect(merged?.activityCount).toBe(2)
    expect(merged?.toolRecordCount).toBe(2)
    expect(merged?.eventCount).toBe(3)
    expect(merged?.events.map((event) => event.source)).toEqual(['activity', 'tool', 'tool'])
    expect(merged?.tagCounts['怕别人失望']).toBe(1)
  })

  it('没有本机工具记录时保持后端事实不变', () => {
    const merged = mergeLocalToolRecords(facts())
    expect(merged?.toolRecordCount).toBe(0)
    expect(merged?.eventCount).toBe(1)
  })
})
