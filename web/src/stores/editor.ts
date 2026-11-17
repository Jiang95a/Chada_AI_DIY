import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import type { Block, BlockType, Doc, DocMeta, DocPlan, Issue, Page, Suggestion, Theme } from '@/types/editor'
import { auditDoc, summarize } from '@/services/quality'
import { planToDoc } from '@/services/planner'
import { cloneBlock, createBlock } from '@/data/blockCatalog'
import { createBlankDoc, createDemoDoc, createDocFromTemplate, TEMPLATES } from '@/data/templates'
import { normalizeDoc } from '@/utils/doc'
import { uid } from '@/utils/misc'

const STORAGE_KEY = 'diy-campus:doc:v1'
const MAX_HISTORY = 80

function isDocLike(value: unknown): value is Doc {
  if (!value || typeof value !== 'object') return false
  const record = value as Record<string, unknown>
  return typeof record.title === 'string' && Array.isArray(record.pages) && record.pages.length > 0
}

function restoreDoc(): Doc | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as { doc?: unknown }
    return normalizeDoc(parsed?.doc ?? parsed)
  } catch {
    return null
  }
}

export const useEditorStore = defineStore('editor', () => {
  const initial = restoreDoc()
  const doc = ref<Doc>(initial ?? createDemoDoc())
  const currentPageId = ref<string>(doc.value.pages[0]?.id ?? '')
  const selectedBlockId = ref<string | null>(null)
  const restoredFromStorage = ref(Boolean(initial))

  const past = ref<string[]>([])
  const future = ref<string[]>([])
  const savedAt = ref<number | null>(null)
  const saving = ref(false)
  const activeProjectId = ref<string | null>(null)

  let pendingSnapshot: string | null = null
  let historyTimer: number | undefined
  let saveTimer: number | undefined

  /* ----------------------------- 派生状态 ----------------------------- */

  const currentPage = computed<Page | undefined>(() => doc.value.pages.find((page) => page.id === currentPageId.value))

  const selectedBlock = computed<Block | undefined>(() =>
    currentPage.value?.blocks.find((block) => block.id === selectedBlockId.value)
  )

  const blockCount = computed(() => doc.value.pages.reduce((total, page) => total + page.blocks.length, 0))

  const canUndo = computed(() => past.value.length > 0 || Boolean(pendingSnapshot))
  const canRedo = computed(() => future.value.length > 0)

  const issues = computed<Issue[]>(() => auditDoc(doc.value))
  const issueSummary = computed(() => summarize(issues.value))

  const shareUrl = computed(() => doc.value.published?.url ?? `${window.location.origin}/p/${doc.value.slug}`)

  /* ----------------------------- 历史 / 自动保存 ----------------------------- */

  function serialize(): string {
    return JSON.stringify(doc.value)
  }

  function applySerialized(snapshot: string): void {
    try {
      const next = JSON.parse(snapshot) as Doc
      if (!isDocLike(next)) return
      doc.value = next
      if (!next.pages.some((page) => page.id === currentPageId.value)) {
        currentPageId.value = next.pages[0]?.id ?? ''
      }
      if (!next.pages.some((page) => page.blocks.some((block) => block.id === selectedBlockId.value))) {
        selectedBlockId.value = null
      }
    } catch {
      /* 快照坏了就当没这回事 */
    }
  }

  function scheduleSave(): void {
    saving.value = true
    if (saveTimer !== undefined) window.clearTimeout(saveTimer)
    saveTimer = window.setTimeout(() => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({ v: 1, doc: doc.value }))
        savedAt.value = Date.now()
      } catch {
        /* 存储满了也不该阻断编辑 */
      } finally {
        saving.value = false
      }
    }, 700)
  }

  function commit(snapshot: string): void {
    past.value = [...past.value, snapshot].slice(-MAX_HISTORY)
    future.value = []
  }

  /**
   * 每次修改前调用：
   * - immediate=true 表示一次独立操作（增删、拖拽、换主题），立刻入栈；
   * - 否则按 500ms 合并，避免每敲一个字都产生一个撤销点。
   */
  function mark(immediate = false): void {
    const snapshot = serialize()
    if (immediate) {
      if (pendingSnapshot) pendingSnapshot = null
      if (historyTimer !== undefined) window.clearTimeout(historyTimer)
      commit(snapshot)
    } else {
      if (!pendingSnapshot) pendingSnapshot = snapshot
      if (historyTimer !== undefined) window.clearTimeout(historyTimer)
      historyTimer = window.setTimeout(() => {
        if (pendingSnapshot) {
          commit(pendingSnapshot)
          pendingSnapshot = null
        }
      }, 500)
    }
    scheduleSave()
  }

  function flushPending(): void {
    if (!pendingSnapshot) return
    commit(pendingSnapshot)
    pendingSnapshot = null
    if (historyTimer !== undefined) window.clearTimeout(historyTimer)
  }

  function undo(): boolean {
    flushPending()
    const snapshot = past.value[past.value.length - 1]
    if (!snapshot) return false
    past.value = past.value.slice(0, -1)
    future.value = [...future.value, serialize()]
    applySerialized(snapshot)
    scheduleSave()
    return true
  }

  function redo(): boolean {
    const snapshot = future.value[future.value.length - 1]
    if (!snapshot) return false
    future.value = future.value.slice(0, -1)
    past.value = [...past.value, serialize()]
    applySerialized(snapshot)
    scheduleSave()
    return true
  }

  function saveNow(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ v: 1, doc: doc.value }))
      savedAt.value = Date.now()
      saving.value = false
    } catch {
      /* 忽略 */
    }
  }

  function loadDocument(projectId: string, next: Doc): void {
    const normalized = normalizeDoc(JSON.parse(JSON.stringify(next)) as unknown)
    if (!normalized) return
    activeProjectId.value = projectId
    doc.value = normalized
    currentPageId.value = normalized.pages.find((page) => page.role === 'home')?.id ?? normalized.pages[0]?.id ?? ''
    selectedBlockId.value = null
    past.value = []
    future.value = []
    pendingSnapshot = null
    scheduleSave()
  }

  /* ----------------------------- 选择 / 页面 ----------------------------- */

  function selectPage(pageId: string): void {
    if (!doc.value.pages.some((page) => page.id === pageId)) return
    currentPageId.value = pageId
    selectedBlockId.value = null
  }

  function selectBlock(blockId: string | null): void {
    selectedBlockId.value = blockId
  }

  function addPage(name?: string, role: Page['role'] = 'content'): Page {
    mark(true)
    const page: Page = {
      id: uid('page'),
      name: name?.trim() || `页面 ${doc.value.pages.length + 1}`,
      role,
      blocks: [createBlock('nav', { title: name?.trim() || '新页面', showBack: true })],
      bg: '',
    }
    doc.value.pages.push(page)
    doc.value.updatedAt = Date.now()
    currentPageId.value = page.id
    selectedBlockId.value = null
    return page
  }

  function removePage(pageId: string): boolean {
    if (doc.value.pages.length <= 1) return false
    const index = doc.value.pages.findIndex((page) => page.id === pageId)
    if (index < 0) return false
    mark(true)
    doc.value.pages.splice(index, 1)
    doc.value.updatedAt = Date.now()
    if (currentPageId.value === pageId) {
      currentPageId.value = doc.value.pages[Math.max(0, index - 1)]?.id ?? doc.value.pages[0]?.id ?? ''
      selectedBlockId.value = null
    }
    return true
  }

  function renamePage(pageId: string, name: string): void {
    const page = doc.value.pages.find((item) => item.id === pageId)
    if (!page || !name.trim()) return
    mark()
    page.name = name.trim().slice(0, 16)
    doc.value.updatedAt = Date.now()
  }

  function setPageRole(pageId: string, role: Page['role']): void {
    const page = doc.value.pages.find((item) => item.id === pageId)
    if (!page || page.role === role) return
    mark(true)
    page.role = role
    doc.value.updatedAt = Date.now()
  }

  function movePage(pageId: string, delta: number): void {
    const index = doc.value.pages.findIndex((page) => page.id === pageId)
    const target = index + delta
    if (index < 0 || target < 0 || target >= doc.value.pages.length) return
    mark(true)
    const [page] = doc.value.pages.splice(index, 1)
    if (page) doc.value.pages.splice(target, 0, page)
    doc.value.updatedAt = Date.now()
  }

  /** 复制页面，保留组件内容和样式，但重新生成页面 id / 组件 id，避免同页冲突。 */
  function duplicatePage(pageId: string): Page | null {
    const source = doc.value.pages.find((page) => page.id === pageId)
    if (!source) return null
    mark(true)
    const copy = JSON.parse(JSON.stringify(source)) as Page
    copy.id = uid('page')
    copy.name = `${source.name} 副本`
    copy.role = 'content'
    copy.blocks = copy.blocks.map((block) => ({ ...block, id: uid('blk') }))
    const index = doc.value.pages.findIndex((page) => page.id === pageId)
    doc.value.pages.splice(index + 1, 0, copy)
    doc.value.updatedAt = Date.now()
    currentPageId.value = copy.id
    selectedBlockId.value = null
    return copy
  }

  /* ----------------------------- 组件 ----------------------------- */

  function addBlock(type: BlockType, index?: number): Block {
    const page = currentPage.value
    if (!page) return createBlock(type)
    mark(true)
    const block = createBlock(type)
    const at = index === undefined ? page.blocks.length : Math.max(0, Math.min(index, page.blocks.length))
    page.blocks.splice(at, 0, block)
    doc.value.updatedAt = Date.now()
    selectedBlockId.value = block.id
    return block
  }

  function removeBlock(blockId: string): void {
    const page = currentPage.value
    if (!page) return
    const index = page.blocks.findIndex((block) => block.id === blockId)
    if (index < 0) return
    mark(true)
    page.blocks.splice(index, 1)
    doc.value.updatedAt = Date.now()
    if (selectedBlockId.value === blockId) selectedBlockId.value = null
  }

  function duplicateBlock(blockId: string): void {
    const page = currentPage.value
    if (!page) return
    const index = page.blocks.findIndex((block) => block.id === blockId)
    const source = page.blocks[index]
    if (!source) return
    mark(true)
    const copy = cloneBlock(source)
    page.blocks.splice(index + 1, 0, copy)
    doc.value.updatedAt = Date.now()
    selectedBlockId.value = copy.id
  }

  /** index 是「插入到第几个位置之前」的语义，拖拽时从后往前移要减一 */
  function moveBlock(blockId: string, index: number): void {
    const page = currentPage.value
    if (!page) return
    const from = page.blocks.findIndex((block) => block.id === blockId)
    if (from < 0) return
    let to = Math.max(0, Math.min(index, page.blocks.length))
    if (from < to) to -= 1
    if (to === from) return
    mark(true)
    const [block] = page.blocks.splice(from, 1)
    if (block) page.blocks.splice(to, 0, block)
    doc.value.updatedAt = Date.now()
  }

  function moveBlockBy(blockId: string, delta: number): void {
    const page = currentPage.value
    if (!page) return
    const from = page.blocks.findIndex((block) => block.id === blockId)
    const to = from + delta
    if (from < 0 || to < 0 || to >= page.blocks.length) return
    mark(true)
    const [block] = page.blocks.splice(from, 1)
    if (block) page.blocks.splice(to, 0, block)
    doc.value.updatedAt = Date.now()
  }

  function toggleHidden(blockId: string): void {
    const block = currentPage.value?.blocks.find((item) => item.id === blockId)
    if (!block) return
    mark(true)
    block.hidden = !block.hidden
    doc.value.updatedAt = Date.now()
  }

  function updateProps(blockId: string, patch: Record<string, any>, immediate = false): void {
    const block = currentPage.value?.blocks.find((item) => item.id === blockId)
    if (!block) return
    mark(immediate)
    block.props = { ...block.props, ...patch }
    doc.value.updatedAt = Date.now()
  }

  function updateBlockStyle(blockId: string, patch: Partial<Block['style']>, immediate = false): void {
    const block = currentPage.value?.blocks.find((item) => item.id === blockId)
    if (!block) return
    mark(immediate)
    block.style = { ...block.style, ...patch }
    doc.value.updatedAt = Date.now()
  }

  function updatePageBg(pageId: string, bg: string): void {
    const page = doc.value.pages.find((item) => item.id === pageId)
    if (!page) return
    mark()
    page.bg = bg
    doc.value.updatedAt = Date.now()
  }

  /* ----------------------------- 文档级 ----------------------------- */

  function updateTitle(title: string): void {
    mark()
    doc.value.title = title.slice(0, 40)
    doc.value.updatedAt = Date.now()
  }

  function updateMeta(patch: Partial<DocMeta>): void {
    mark()
    doc.value.meta = { ...doc.value.meta, ...patch }
    doc.value.updatedAt = Date.now()
  }

  function updateTheme(patch: Partial<Theme>, immediate = false): void {
    mark(immediate)
    doc.value.theme = { ...doc.value.theme, ...patch }
    doc.value.updatedAt = Date.now()
  }

  function applyDoc(next: Doc, keepHistory = true): void {
    if (keepHistory) mark(true)
    else {
      past.value = []
      future.value = []
    }
    doc.value = next
    currentPageId.value = next.pages[0]?.id ?? ''
    selectedBlockId.value = null
    scheduleSave()
  }

  function applyPlan(plan: DocPlan): Doc {
    const next = planToDoc(plan, doc.value)
    applyDoc(next, true)
    return next
  }

  function useTemplate(templateId: string): void {
    applyDoc(createDocFromTemplate(templateId, doc.value), true)
  }

  function startBlank(): void {
    const blank = createBlankDoc()
    applyDoc(
      {
        ...blank,
        id: doc.value.id,
        slug: doc.value.slug,
        createdAt: doc.value.createdAt,
      },
      true
    )
  }

  function loadDemo(): void {
    applyDoc(createDemoDoc(), true)
  }

  function importDoc(raw: string): { ok: boolean; message: string } {
    try {
      const parsed = JSON.parse(raw) as unknown
      const normalized = normalizeDoc(parsed)
      if (!normalized) return { ok: false, message: '这不是有效的项目 JSON' }
      applyDoc(normalized, true)
      return { ok: true, message: `已导入「${normalized.title}」` }
    } catch {
      return { ok: false, message: 'JSON 解析失败，检查一下文件内容' }
    }
  }

  function publish(): void {
    mark(true)
    doc.value.published = {
      url: `${window.location.origin}/p/${doc.value.slug}`,
      slug: doc.value.slug,
      at: Date.now(),
    }
    doc.value.updatedAt = Date.now()
  }

  function unpublish(): void {
    mark(true)
    doc.value.published = null
    doc.value.updatedAt = Date.now()
  }

  function applySuggestion(suggestion: Suggestion): void {
    mark(true)
    if (suggestion.patch?.theme) {
      doc.value.theme = { ...doc.value.theme, ...suggestion.patch.theme }
    }
    if (suggestion.blockId) {
      for (const page of doc.value.pages) {
        const block = page.blocks.find((item) => item.id === suggestion.blockId)
        if (!block) continue
        if (suggestion.patch?.props) block.props = { ...block.props, ...suggestion.patch.props }
        if (suggestion.patch?.style) block.style = { ...block.style, ...suggestion.patch.style }
        break
      }
    }
    doc.value.updatedAt = Date.now()
    scheduleSave()
  }

  function resetAll(): void {
    past.value = []
    future.value = []
    pendingSnapshot = null
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      /* 忽略 */
    }
    applyDoc(createDemoDoc(), false)
  }

  /* 外部改动（例如撤销后页面 id 变了）时兜底 */
  watch(
    () => doc.value.pages.length,
    () => {
      if (!doc.value.pages.some((page) => page.id === currentPageId.value)) {
        currentPageId.value = doc.value.pages[0]?.id ?? ''
      }
    }
  )

  return {
    // state
    doc,
    activeProjectId,
    currentPageId,
    selectedBlockId,
    past,
    future,
    savedAt,
    saving,
    restoredFromStorage,
    // getters
    currentPage,
    selectedBlock,
    blockCount,
    canUndo,
    canRedo,
    issues,
    issueSummary,
    shareUrl,
    templates: TEMPLATES,
    // actions
    selectPage,
    selectBlock,
    addPage,
    removePage,
    renamePage,
    setPageRole,
    movePage,
    duplicatePage,
    addBlock,
    removeBlock,
    duplicateBlock,
    moveBlock,
    moveBlockBy,
    toggleHidden,
    updateProps,
    updateBlockStyle,
    updatePageBg,
    updateTitle,
    updateMeta,
    updateTheme,
    applyDoc,
    applyPlan,
    useTemplate,
    startBlank,
    loadDemo,
    importDoc,
    publish,
    unpublish,
    applySuggestion,
    resetAll,
    undo,
    redo,
    saveNow,
    loadDocument,
  }
})
