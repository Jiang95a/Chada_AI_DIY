import type { Doc } from '@/types/editor'
import { TEMPLATES, createDocFromTemplate } from '@/data/templates'
import { normalizePlan, planToDoc } from '@/services/planner'
import { normalizeDoc } from '@/utils/doc'
import { slugify, uid } from '@/utils/misc'
import { apiRequest, readData } from './http'

interface TemplateApiRecord {
  id: string
  code: string
  name: string
  category: string
  description?: string
  coverUrl?: string
  kind?: 'wellbeing' | 'career' | 'general'
  document: unknown
}

export type TemplateKind = 'wellbeing' | 'career' | 'general'

export interface TemplateCatalogItem {
  id: string
  code: string
  name: string
  category: string
  scene: string
  tagline: string
  icon: string
  gradient: [string, string]
  source: 'remote' | 'local' | 'custom'
  kind: TemplateKind
  build: () => Doc
}

interface CustomTemplateRecord {
  id: string
  name: string
  category: string
  kind: TemplateKind
  description: string
  document: unknown
  createdAt: number
  updatedAt: number
}

export interface SaveCustomTemplateInput {
  name: string
  category: string
  kind?: TemplateKind
  description?: string
  document: Doc
}

const CUSTOM_TEMPLATE_KEY = 'diy-campus:custom-templates:v1'

const CATEGORY_META: Record<string, { scene: string; icon: string; gradient: [string, string] }> = {
  recruitment: { scene: '心理社团招新', icon: 'users', gradient: ['#5d8c72', '#9fbfaa'] },
  registration: { scene: '心晴活动', icon: 'calendar', gradient: ['#b87c66', '#e0b69f'] },
  vote: { scene: '心理知识问答', icon: 'check-circle', gradient: ['#648b94', '#a7c1c6'] },
  checkin: { scene: '自我关怀打卡', icon: 'clock', gradient: ['#6f8f72', '#b9c79c'] },
  other: { scene: '其他心理活动', icon: 'file', gradient: ['#6b7d70', '#c2b39e'] },
}

const KIND_META: Record<TemplateKind, { scene: string; icon: string; gradient: [string, string] }> = {
  wellbeing: { scene: '心理活动', icon: 'spark', gradient: ['#5d8c72', '#9fbfaa'] },
  career: { scene: '生涯发展', icon: 'rocket', gradient: ['#7c8c75', '#c6b8a0'] },
  general: { scene: '通用模板', icon: 'grid', gradient: ['#708090', '#b9c2c9'] },
}

function toDoc(document: unknown): Doc | null {
  const normalized = normalizeDoc(document)
  if (normalized) return normalized
  const plan = normalizePlan(document)
  return plan ? planToDoc(plan) : null
}

function remoteItem(template: TemplateApiRecord): TemplateCatalogItem {
  const kind = template.kind ?? 'general'
  const meta = CATEGORY_META[template.category] ??
    KIND_META[kind] ?? {
      scene: template.category || '心理活动',
      icon: 'grid',
      gradient: ['#2563eb', '#7c3aed'] as [string, string],
    }
  return {
    id: template.id,
    code: template.code,
    name: template.name,
    category: template.category,
    scene: meta.scene,
    tagline: template.description || `${template.name}模板`,
    icon: meta.icon,
    gradient: meta.gradient,
    source: 'remote',
    kind,
    build: () => toDoc(template.document) ?? createDocFromTemplate('recruit'),
  }
}

function cloneDocument(document: Doc): Doc {
  return JSON.parse(JSON.stringify(document)) as Doc
}

function customBuild(record: CustomTemplateRecord): Doc {
  const source = normalizeDoc(record.document)
  if (!source) return createDocFromTemplate('recruit')
  const document = cloneDocument(source)
  document.id = uid('doc')
  document.slug = slugify(document.title)
  document.createdAt = Date.now()
  document.updatedAt = Date.now()
  document.published = null
  return document
}

function customItem(template: CustomTemplateRecord): TemplateCatalogItem {
  const kind = template.kind ?? 'wellbeing'
  const meta = CATEGORY_META[template.category] ??
    KIND_META[kind] ?? {
      scene: template.category || '我的模板',
      icon: 'file',
      gradient: ['#3158e8', '#16a085'] as [string, string],
    }
  return {
    id: template.id,
    code: template.id,
    name: template.name,
    category: template.category,
    scene: meta.scene,
    tagline: template.description || '我保存的自定义活动模板',
    icon: meta.icon,
    gradient: meta.gradient,
    source: 'custom',
    kind,
    build: () => customBuild(template),
  }
}

function restoreCustomTemplates(): CustomTemplateRecord[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(CUSTOM_TEMPLATE_KEY) ?? '[]') as unknown
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((item): item is CustomTemplateRecord => {
        if (!item || typeof item !== 'object') return false
        const record = item as Partial<CustomTemplateRecord>
        return (
          typeof record.id === 'string' &&
          typeof record.name === 'string' &&
          typeof record.category === 'string' &&
          Boolean(normalizeDoc(record.document))
        )
      })
      .sort((a, b) => b.updatedAt - a.updatedAt)
  } catch {
    return []
  }
}

function persistCustomTemplates(templates: CustomTemplateRecord[]): void {
  localStorage.setItem(CUSTOM_TEMPLATE_KEY, JSON.stringify(templates))
}

export function listCustomTemplates(): TemplateCatalogItem[] {
  return restoreCustomTemplates().map(customItem)
}

export function saveCustomTemplate(input: SaveCustomTemplateInput): TemplateCatalogItem {
  const document = normalizeDoc(cloneDocument(input.document))
  if (!document) throw new Error('当前页面结构无法保存为模板')
  const name = input.name.trim()
  if (!name) throw new Error('请填写模板名称')

  const records = restoreCustomTemplates()
  const existing = records.find((item) => item.name === name)
  const now = Date.now()
  const record: CustomTemplateRecord = {
    id: existing?.id ?? `custom-${now.toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
    name: name.slice(0, 30),
    category: input.category || 'other',
    kind: 'wellbeing',
    description: input.description?.trim().slice(0, 120) || '',
    document: cloneDocument(document),
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  }
  const next = [record, ...records.filter((item) => item.id !== record.id)]
  try {
    persistCustomTemplates(next)
  } catch {
    throw new Error('浏览器存储空间不足，模板保存失败')
  }
  return customItem(record)
}

export function removeCustomTemplate(id: string): void {
  persistCustomTemplates(restoreCustomTemplates().filter((item) => item.id !== id))
}

const localItems: TemplateCatalogItem[] = TEMPLATES.map((template) => ({
  id: template.id,
  code: template.id,
  name: template.name,
  category: template.id,
  scene: template.scene,
  tagline: template.tagline,
  icon: template.icon,
  gradient: template.gradient,
  source: 'local',
  kind: 'wellbeing',
  build: () => createDocFromTemplate(template.id),
}))

function filterKind(items: TemplateCatalogItem[], kind?: TemplateKind): TemplateCatalogItem[] {
  return kind ? items.filter((item) => item.kind === kind) : items
}

export async function loadTemplateCatalog(kind?: TemplateKind): Promise<TemplateCatalogItem[]> {
  const customItems = listCustomTemplates()
  try {
    const data = readData<TemplateApiRecord[]>(await apiRequest('/templates'))
    if (Array.isArray(data) && data.length) {
      const remote = data.filter((template) => !kind || (template.kind ?? 'general') === kind).map(remoteItem)
      return [...filterKind(customItems, kind), ...remote]
    }
  } catch {
    // 后端不可用时继续使用本地模板，保证演示和编辑流程不中断。
  }
  return [...filterKind(customItems, kind), ...filterKind(localItems, kind)]
}

export async function loadTemplateDocument(id: string): Promise<Doc> {
  const custom = restoreCustomTemplates().find((template) => template.id === id)
  if (custom) return customBuild(custom)
  try {
    const data = readData<TemplateApiRecord>(await apiRequest(`/templates/${encodeURIComponent(id)}`))
    const doc = toDoc(data.document)
    if (doc) return doc
  } catch {
    // 静态模板 id 在后端不存在时回退到本地构建。
  }
  return createDocFromTemplate(id)
}
