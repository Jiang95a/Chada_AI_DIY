import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { getAccessToken } from '@/services/http'
import { fetchRemoteTrace, saveRemoteTrace } from '@/services/traces'
import { ruleParser } from '@/services/traceParser'
import {
  emptyTrace,
  type ActivityDomain,
  type ParticipantNote,
  type ParticipantTrace,
  type TraceEvent,
} from '@/types/trace'

/**
 * 个人轨迹（ParticipantTrace）。
 *
 * 原则：
 * - 先写本地，保证断网、未登录也能用；
 * - 有登录态时再读/写后端，失败不打扰用户，状态显示「只在本机」；
 * - 后端没就绪时只尝试一次，之后本次会话不再请求，避免每输入一句就报错。
 */

const STORAGE_PREFIX = 'diy-campus:trace:v1'
const DEVICE_KEY = 'diy-campus:device-id'

export type SyncState = 'idle' | 'local' | 'syncing' | 'synced' | 'offline'

const syncTimers = new Map<string, number>()

function deviceIdentity(): string {
  try {
    let id = localStorage.getItem(DEVICE_KEY)
    if (!id) {
      id = `device-${crypto.randomUUID()}`
      localStorage.setItem(DEVICE_KEY, id)
    }
    return id
  } catch {
    return 'device-unknown'
  }
}

function normalizeTrace(
  raw: unknown,
  userId: string,
  activityId: string,
  domain: ActivityDomain
): ParticipantTrace | null {
  if (!raw || typeof raw !== 'object') return null
  const value = raw as Partial<ParticipantTrace>
  const tags = Array.isArray(value.tags)
    ? value.tags
        .filter((tag) => tag && typeof tag.content === 'string')
        .map((tag) => ({
          content: tag.content,
          source: tag.source === 'user' ? ('user' as const) : ('auto' as const),
          createdAt: typeof tag.createdAt === 'number' ? tag.createdAt : Date.now(),
        }))
    : []
  const sessionContext =
    value.sessionContext &&
    typeof value.sessionContext.currentHint === 'string' &&
    Array.isArray(value.sessionContext.availablePaths)
      ? {
          currentHint: value.sessionContext.currentHint,
          availablePaths: value.sessionContext.availablePaths.filter(
            (path): path is string => typeof path === 'string'
          ),
        }
      : { currentHint: '', availablePaths: [] }

  const notes: ParticipantNote[] = Array.isArray(value.notes)
    ? value.notes
        .filter((note) => note && typeof note.text === 'string')
        .map((note) => ({
          activityId,
          text: note.text.slice(0, 120),
          mood: typeof note.mood === 'string' ? note.mood : undefined,
          at: typeof note.at === 'number' ? note.at : Date.now(),
          candidateTraceId: typeof note.candidateTraceId === 'string' ? note.candidateTraceId : undefined,
        }))
        .slice(-10)
    : []

  const history: TraceEvent[] = Array.isArray(value.history)
    ? value.history
        .filter((event) => event && typeof event.at === 'number')
        .map((event) => ({
          at: event.at,
          kind: event.kind === 'note' || event.kind === 'summary' ? event.kind : ('input' as const),
          activityId,
          tags: Array.isArray(event.tags) ? event.tags.filter((tag) => typeof tag === 'string') : [],
          hint: typeof event.hint === 'string' ? event.hint : undefined,
          summary: typeof event.summary === 'string' ? event.summary : undefined,
          change: typeof event.change === 'string' ? event.change : undefined,
        }))
        .slice(-60)
    : []

  return {
    ...emptyTrace(userId, activityId, domain),
    ...value,
    userId,
    activityId,
    domain: value.domain === 'career' ? 'career' : domain,
    semester: typeof value.semester === 'string' ? value.semester : '',
    summary: typeof value.summary === 'string' ? value.summary : '',
    tags,
    sessionContext,
    notes,
    history,
    updatedAt: typeof value.updatedAt === 'number' ? value.updatedAt : Date.now(),
  }
}

export const useTraceStore = defineStore('trace', () => {
  const traces = ref<Record<string, ParticipantTrace>>({})
  const status = ref<Record<string, SyncState>>({})
  const loaded = ref<Record<string, boolean>>({})
  const backendDisabled = ref(false)

  const auth = useAuthStore()
  const userId = computed(() => auth.user?.id || deviceIdentity())

  function keyFor(activityId: string): string {
    return `${STORAGE_PREFIX}:${userId.value}:${activityId}`
  }

  function readLocal(activityId: string): ParticipantTrace | null {
    try {
      const raw = localStorage.getItem(keyFor(activityId))
      if (!raw) return null
      return normalizeTrace(JSON.parse(raw), userId.value, activityId, 'mental')
    } catch {
      return null
    }
  }

  function persist(trace: ParticipantTrace): void {
    try {
      localStorage.setItem(keyFor(trace.activityId), JSON.stringify(trace))
    } catch {
      /* 存不下不影响当前页面使用 */
    }
  }

  function patchTrace(activityId: string, patch: Partial<ParticipantTrace>): ParticipantTrace {
    const current = traces.value[activityId] ?? emptyTrace(userId.value, activityId, 'mental')
    const next: ParticipantTrace = {
      ...current,
      ...patch,
      userId: userId.value,
      activityId,
      tags: patch.tags ?? current.tags,
      sessionContext: patch.sessionContext ?? current.sessionContext,
      updatedAt: Date.now(),
    }
    traces.value = { ...traces.value, [activityId]: next }
    status.value = { ...status.value, [activityId]: 'local' }
    persist(next)
    queueSync(activityId)
    return next
  }

  function queueSync(activityId: string): void {
    if (backendDisabled.value || !getAccessToken()) return
    const existing = syncTimers.get(activityId)
    if (existing) window.clearTimeout(existing)
    const timer = window.setTimeout(() => {
      syncTimers.delete(activityId)
      void sync(activityId)
    }, 1400)
    syncTimers.set(activityId, timer)
  }

  async function sync(activityId: string): Promise<void> {
    const trace = traces.value[activityId]
    if (!trace || backendDisabled.value) return
    status.value = { ...status.value, [activityId]: 'syncing' }
    const ok = await saveRemoteTrace(trace)
    if (ok) {
      status.value = { ...status.value, [activityId]: 'synced' }
    } else {
      backendDisabled.value = true
      status.value = { ...status.value, [activityId]: 'offline' }
    }
  }

  async function load(activityId: string, domain: ActivityDomain = 'mental'): Promise<ParticipantTrace> {
    if (loaded.value[activityId] && traces.value[activityId]) return traces.value[activityId]

    const local = readLocal(activityId)
    const base = local ?? emptyTrace(userId.value, activityId, domain)
    traces.value = { ...traces.value, [activityId]: base }
    status.value = { ...status.value, [activityId]: local ? 'local' : 'idle' }
    loaded.value = { ...loaded.value, [activityId]: true }

    if (getAccessToken() && !backendDisabled.value) {
      const remote = await fetchRemoteTrace(activityId)
      if (remote) {
        const normalized = normalizeTrace(remote, userId.value, activityId, domain)
        if (normalized && normalized.updatedAt >= base.updatedAt) {
          traces.value = { ...traces.value, [activityId]: normalized }
          persist(normalized)
          status.value = { ...status.value, [activityId]: 'synced' }
        } else {
          status.value = { ...status.value, [activityId]: 'synced' }
        }
      } else {
        status.value = { ...status.value, [activityId]: local ? 'local' : 'offline' }
      }
    }

    return traces.value[activityId] ?? base
  }

  async function applyText(
    activityId: string,
    domain: ActivityDomain,
    text: string
  ): Promise<ParticipantTrace> {
    const trace = await load(activityId, domain)
    const parsed = await ruleParser.parse({ text: text.trim(), trace })
    const { matchedTags, ...patch } = parsed
    const at = Date.now()
    const history = [
      ...(trace.history ?? []),
      {
        at,
        kind: 'input' as const,
        activityId,
        tags: matchedTags ?? [],
        hint: patch.sessionContext?.currentHint,
        summary: patch.summary,
        change: patch.change,
      },
    ].slice(-60)
    return patchTrace(activityId, { ...patch, history })
  }

  /** 便签里勾选「存进我的轨迹」时调用：只在个人档案里留一个私有副本，不动公开墙。 */
  async function absorbNote(
    activityId: string,
    domain: ActivityDomain,
    note: { text: string; mood?: string }
  ): Promise<ParticipantTrace> {
    const trace = await load(activityId, domain)
    const tag = {
      content: '写下过一句话',
      source: 'user' as const,
      createdAt: Date.now(),
    }
    const tags = trace.tags.some((item) => item.content === tag.content) ? trace.tags : [...trace.tags, tag].slice(-12)
    const at = Date.now()
    const copy: ParticipantNote = {
      activityId,
      text: note.text.trim().slice(0, 120),
      mood: note.mood,
      at,
      candidateTraceId: `note_${at}`,
    }
    const notes = [...(trace.notes ?? []), copy].slice(-10)
    const summary = trace.summary || '你在这里留下了一句话。'
    const history = [
      ...(trace.history ?? []),
      {
        at,
        kind: 'note' as const,
        activityId,
        tags: ['写下过一句话'],
        summary,
      },
    ].slice(-60)
    return patchTrace(activityId, { tags, notes, history, summary, updatedAt: at })
  }

  function updateSummary(activityId: string, summary: string): void {
    const value = summary.trim().slice(0, 60)
    const trace = traces.value[activityId]
    const history = [
      ...(trace?.history ?? []),
      { at: Date.now(), kind: 'summary' as const, activityId, tags: [] as string[], summary: value },
    ].slice(-60)
    patchTrace(activityId, { summary: value, history })
  }

  function removeTag(activityId: string, content: string): void {
    const trace = traces.value[activityId]
    if (!trace) return
    patchTrace(activityId, { tags: trace.tags.filter((tag) => tag.content !== content) })
  }

  /** 同一台设备上，另一个活动留下的最后一条记录（跨活动接续的前端降级版）。 */
  function previousTrace(activityId: string): ParticipantTrace | null {
    try {
      const prefix = `${STORAGE_PREFIX}:${userId.value}:`
      let latest: ParticipantTrace | null = null
      for (let index = 0; index < localStorage.length; index += 1) {
        const key = localStorage.key(index)
        if (!key || !key.startsWith(prefix)) continue
        const otherId = key.slice(prefix.length)
        if (otherId === activityId) continue
        const raw = localStorage.getItem(key)
        if (!raw) continue
        const trace = normalizeTrace(JSON.parse(raw), userId.value, otherId, 'mental')
        if (!trace || (!trace.summary && !trace.tags.length && !trace.history?.length)) continue
        if (!latest || trace.updatedAt > latest.updatedAt) latest = trace
      }
      return latest
    } catch {
      return null
    }
  }

  function get(activityId: string): ParticipantTrace | undefined {
    return traces.value[activityId]
  }

  /** 本机所有活动的轨迹（跨活动回看 / 我的轨迹页）。 */
  function list(): ParticipantTrace[] {
    const map = new Map<string, ParticipantTrace>()
    for (const [activityId, trace] of Object.entries(traces.value)) {
      if (trace.summary || trace.tags.length || trace.history?.length) map.set(activityId, trace)
    }
    try {
      const prefix = `${STORAGE_PREFIX}:${userId.value}:`
      for (let index = 0; index < localStorage.length; index += 1) {
        const key = localStorage.key(index)
        if (!key || !key.startsWith(prefix)) continue
        const activityId = key.slice(prefix.length)
        if (map.has(activityId)) continue
        const raw = localStorage.getItem(key)
        if (!raw) continue
        const trace = normalizeTrace(JSON.parse(raw), userId.value, activityId, 'mental')
        if (trace && (trace.summary || trace.tags.length)) map.set(activityId, trace)
      }
    } catch {
      /* 本机存储不可用时只返回内存里的 */
    }
    return [...map.values()].sort((a, b) => b.updatedAt - a.updatedAt)
  }

  function remove(activityId: string): void {
    const next = { ...traces.value }
    delete next[activityId]
    traces.value = next
    try {
      localStorage.removeItem(keyFor(activityId))
    } catch {
      /* 忽略 */
    }
  }

  function clearAll(): void {
    traces.value = {}
    try {
      const prefix = `${STORAGE_PREFIX}:${userId.value}:`
      const keys: string[] = []
      for (let index = 0; index < localStorage.length; index += 1) {
        const key = localStorage.key(index)
        if (key && key.startsWith(prefix)) keys.push(key)
      }
      keys.forEach((key) => localStorage.removeItem(key))
    } catch {
      /* 忽略 */
    }
  }

  return {
    traces,
    status,
    userId,
    load,
    applyText,
    absorbNote,
    updateSummary,
    removeTag,
    previousTrace,
    get,
    list,
    remove,
    clearAll,
  }
})
