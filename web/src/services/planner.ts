import type { Block, BlockPlan, BlockType, Doc, DocMeta, DocPlan, Page, PageRole, Theme } from '@/types/editor'
import { isBlockType } from '@/types/editor'
import { createBlock, DEFAULT_THEME } from '@/data/blockCatalog'
import { templateById, TEMPLATES } from '@/data/templates'
import { linkPages, makePage } from '@/utils/doc'
import { uid, slugify } from '@/utils/misc'

/* ------------------------------------------------------------------------- */
/* 文案 → 结构化方案：本地规则兜底                                              */
/* ------------------------------------------------------------------------- */

const ALIAS: Record<string, BlockType> = {
  导航栏: 'nav',
  导航: 'nav',
  顶部导航: 'nav',
  navbar: 'nav',
  nav: 'nav',
  banner: 'banner',
  头图: 'banner',
  海报: 'banner',
  主视觉: 'banner',
  标题: 'heading',
  章节标题: 'heading',
  文本: 'text',
  正文: 'text',
  文案: 'text',
  段落: 'text',
  图片: 'image',
  照片: 'image',
  配图: 'image',
  信息卡片: 'infoCard',
  信息卡: 'infoCard',
  活动信息: 'infoCard',
  卡片: 'infoCard',
  按钮: 'button',
  报名按钮: 'button',
  表单: 'form',
  报名表单: 'form',
  问卷: 'form',
  单选框: 'radio',
  单选: 'radio',
  多选框: 'checkbox',
  多选: 'checkbox',
  复选: 'checkbox',
  二维码: 'qrcode',
  qr: 'qrcode',
  分割线: 'divider',
  倒计时: 'countdown',
}

function toType(token: string): BlockType | null {
  const key = token
    .toLowerCase()
    .replace(/[（(].*?[）)]/g, '')
    .replace(/[\s「」『』"'"']/g, '')
    .trim()
  if (!key) return null
  if (Object.prototype.hasOwnProperty.call(ALIAS, key)) return ALIAS[key] ?? null
  const found = Object.keys(ALIAS).find((alias) => key.startsWith(alias) || alias.startsWith(key))
  return found ? (ALIAS[found] ?? null) : null
}

const ROLE_BY_NAME: { test: RegExp; role: PageRole }[] = [
  { test: /首页|主页|欢迎/, role: 'home' },
  { test: /表单|报名|投票|打卡|填写|问卷/, role: 'form' },
  { test: /成功|完成|谢谢|结果/, role: 'success' },
]

export function inferRole(pageName: string, fallback: PageRole = 'content'): PageRole {
  for (const item of ROLE_BY_NAME) {
    if (item.test.test(pageName)) return item.role
  }
  return fallback
}

/**
 * 解析智能体最常见的输出形态：
 *   首页：导航栏 / Banner / 信息卡片 / 按钮
 *   - 报名表单: 表单, 多选框
 */
export function parsePlanFromText(text: string, title = ''): DocPlan | null {
  const pages: { name: string; role: PageRole; blocks: BlockPlan[] }[] = []

  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine
      .replace(/^[#>\s]*/, '')
      .replace(/^[-*•·]\s*/, '')
      .replace(/\*\*/g, '')
      .trim()
    if (!line) continue
    const match = line.match(/^([^：:]{2,14})[：:]\s*(.+)$/)
    if (!match) continue

    const name = (match[1] ?? '').trim()
    if (/^(页面|结构|方案|说明|规则|理由|注意|时间|地点|费用)$/.test(name)) continue
    if (!/[\u4e00-\u9fa5A-Za-z]/.test(name)) continue

    const body = (match[2] ?? '').trim()
    const tokens = body
      .split(/[\/、,，|·+→]+/)
      .map((token) => token.trim())
      .filter(Boolean)

    const blocks: BlockPlan[] = []
    for (const token of tokens) {
      const type = toType(token)
      if (type && !blocks.some((block) => block.type === type)) blocks.push({ type })
    }
    if (!blocks.length) continue
    pages.push({ name, role: inferRole(name), blocks })
  }

  if (!pages.length) return null
  if (!pages.some((page) => page.role === 'home')) pages[0]!.role = 'home'

  return {
    intent: 'agent-text',
    reason: '从智能体回复的「页面名：组件列表」结构里解析出来的页面骨架',
    title: title || '',
    source: 'agent',
    pages,
  }
}

/* ------------------------------------------------------------------------- */
/* 结构校验：AI 产物必须能落进受控组件                                          */
/* ------------------------------------------------------------------------- */

function sanitizeValue(value: unknown, depth = 0): any {
  if (typeof value === 'string') return value.slice(0, 2000)
  if (typeof value === 'number') return Number.isFinite(value) ? value : undefined
  if (typeof value === 'boolean') return value
  if (Array.isArray(value)) {
    return value.slice(0, 24).map((item) => sanitizeValue(item, depth + 1))
  }
  if (value && typeof value === 'object' && depth < 3) {
    const out: Record<string, any> = {}
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
      if (key.length > 40) continue
      const clean = sanitizeValue(item, depth + 1)
      if (clean !== undefined) out[key] = clean
    }
    return out
  }
  return undefined
}

function normalizeFormFields(raw: unknown): Record<string, any>[] | null {
  if (!Array.isArray(raw)) return null
  const allowed = ['text', 'tel', 'email', 'textarea', 'select', 'radio', 'checkbox']
  const fields = raw
    .slice(0, 12)
    .map((item) => {
      if (typeof item === 'string') {
        return {
          id: uid('f'),
          label: item.slice(0, 24),
          type: 'text',
          required: true,
          placeholder: '',
          options: [],
        }
      }
      if (!item || typeof item !== 'object') return null
      const record = item as Record<string, unknown>
      const type = allowed.includes(String(record.type)) ? String(record.type) : 'text'
      return {
        id: uid('f'),
        label: String(record.label ?? record.name ?? '未命名字段').slice(0, 24),
        type,
        required: Boolean(record.required),
        placeholder: String(record.placeholder ?? '').slice(0, 40),
        options: Array.isArray(record.options)
          ? record.options.slice(0, 8).map((option) => String(option).slice(0, 24))
          : [],
      }
    })
    .filter(Boolean) as Record<string, any>[]
  return fields.length ? fields : null
}

function normalizeStringList(raw: unknown): string[] | null {
  if (!Array.isArray(raw)) return null
  const list = raw
    .slice(0, 10)
    .map((item) => String(item).slice(0, 30))
    .filter(Boolean)
  return list.length ? list : null
}

function normalizeBlock(raw: unknown): BlockPlan | null {
  if (typeof raw === 'string') {
    const type = toType(raw)
    return type ? { type } : null
  }
  if (!raw || typeof raw !== 'object') return null
  const record = raw as Record<string, unknown>
  const rawType = String(record.type ?? record.component ?? '')
  const type = isBlockType(rawType) ? rawType : toType(rawType)
  if (!type) return null

  const props = sanitizeValue(record.props ?? record.properties ?? {}) as Record<string, any> | undefined
  return { type, props: props ?? {} }
}

export function normalizePlan(raw: unknown): DocPlan | null {
  if (!raw || typeof raw !== 'object') return null
  const record = raw as Record<string, any>
  const rawPages = Array.isArray(record.pages)
    ? record.pages
    : Array.isArray(record.page)
      ? record.page
      : Array.isArray(record.sections)
        ? record.sections
        : null
  if (!rawPages) return null

  const pages: DocPlan['pages'] = []
  for (const item of rawPages.slice(0, 8)) {
    if (!item || typeof item !== 'object') continue
    const pageRecord = item as Record<string, any>
    const name = String(pageRecord.name ?? pageRecord.title ?? '').slice(0, 16)
    const blocksRaw = Array.isArray(pageRecord.blocks)
      ? pageRecord.blocks
      : Array.isArray(pageRecord.components)
        ? pageRecord.components
        : []
    const blocks = blocksRaw
      .slice(0, 20)
      .map((block: unknown) => normalizeBlock(block))
      .filter((block: BlockPlan | null): block is BlockPlan => Boolean(block))

    if (!blocks.length) continue
    pages.push({
      name: name || `第 ${pages.length + 1} 页`,
      role: (['home', 'content', 'form', 'success'] as const).includes(pageRecord.role)
        ? (pageRecord.role as PageRole)
        : inferRole(name),
      blocks,
    })
  }

  if (!pages.length) return null
  if (!pages.some((page) => page.role === 'home')) pages[0]!.role = 'home'

  return {
    intent: String(record.intent ?? '').slice(0, 200),
    reason: String(record.reason ?? '').slice(0, 300),
    title: record.title ? String(record.title).slice(0, 40) : '',
    theme: sanitizeValue(record.theme) as Partial<Theme> | undefined,
    meta: sanitizeValue(record.meta) as Partial<DocMeta> | undefined,
    pages,
    source: 'agent',
  }
}

/* ------------------------------------------------------------------------- */
/* 方案 → 文档                                                                 */
/* ------------------------------------------------------------------------- */

const COLOR_KEYS: ('primary' | 'accent' | 'bg' | 'text')[] = ['primary', 'accent', 'bg', 'text']

function sanitizeTheme(raw: Partial<Theme> | undefined, base: Theme): Theme {
  if (!raw) return base
  const next: Theme = { ...base }
  for (const key of COLOR_KEYS) {
    const value = raw[key]
    if (typeof value === 'string' && /^#?[0-9a-fA-F]{3,8}$/.test(value.trim())) {
      next[key] = value.startsWith('#') ? value : `#${value}`
    }
  }
  if (typeof raw.radius === 'number') next.radius = Math.min(32, Math.max(0, raw.radius))
  if (typeof raw.fontScale === 'number') next.fontScale = Math.min(1.2, Math.max(0.9, raw.fontScale))
  if (typeof raw.spacing === 'number') next.spacing = Math.min(1.6, Math.max(0.8, raw.spacing))
  if (raw.buttonStyle && ['solid', 'gradient', 'outline'].includes(raw.buttonStyle)) {
    next.buttonStyle = raw.buttonStyle
  }
  if (raw.font && ['sans', 'serif', 'round'].includes(raw.font)) next.font = raw.font
  return next
}

function sanitizeMeta(raw: Partial<DocMeta> | undefined, base: DocMeta): DocMeta {
  if (!raw) return base
  const next = { ...base }
  for (const key of ['org', 'startAt', 'place', 'deadline', 'contact'] as const) {
    const value = raw[key]
    if (typeof value === 'string') next[key] = value.slice(0, 60)
  }
  if (typeof raw.capacity === 'number' && raw.capacity >= 0) next.capacity = Math.min(9999, raw.capacity)
  return next
}

function normalizeBlockProps(type: BlockType, props: Record<string, any>): Record<string, any> {
  const next: Record<string, any> = { ...props }

  if (type === 'form') {
    const fields = normalizeFormFields(props.fields)
    if (fields) next.fields = fields
    else delete next.fields
  }

  if (type === 'radio' || type === 'checkbox') {
    const options = normalizeStringList(props.options)
    if (options) next.options = options
    else delete next.options
  }

  if (type === 'infoCard') {
    if (Array.isArray(props.items)) {
      const allowedIcons = ['time', 'place', 'org', 'people', 'ticket', 'contact']
      next.items = props.items.slice(0, 6).map((item: any) => ({
        id: uid('it'),
        icon: allowedIcons.includes(item?.icon) ? item.icon : 'time',
        label: String(item?.label ?? '').slice(0, 12),
        value: String(item?.value ?? '待补充').slice(0, 60),
      }))
    }
  }

  if (type === 'button') {
    const action = props.action
    if (typeof action === 'string') {
      next.action = {
        type: action === 'submit' ? 'submit' : 'page',
        pageId: '',
        url: '',
        targetRole: 'form',
      }
    } else if (!action || typeof action !== 'object') {
      next.action = { type: 'page', pageId: '', url: '', targetRole: 'form' }
    } else {
      next.action = {
        type: ['page', 'url', 'submit', 'none'].includes(String(action.type)) ? action.type : 'page',
        pageId: typeof action.pageId === 'string' ? action.pageId : '',
        url: typeof action.url === 'string' ? action.url : '',
        targetRole: typeof action.targetRole === 'string' ? action.targetRole : undefined,
      }
    }
  }

  return next
}

export function planToPages(plan: DocPlan): Page[] {
  const pages: Page[] = plan.pages.map((pagePlan, index) => {
    const blocks = pagePlan.blocks
      .map((blockPlan) => {
        const type = isBlockType(blockPlan.type) ? blockPlan.type : null
        if (!type) return null
        return createBlock(type, normalizeBlockProps(type, blockPlan.props ?? {}))
      })
      .filter((block): block is Block => Boolean(block))

    if (!blocks.length) {
      blocks.push(createBlock('heading', { text: pagePlan.name || `第 ${index + 1} 页` }))
    }
    return makePage(pagePlan.name || `第 ${index + 1} 页`, pagePlan.role, blocks)
  })

  if (!pages.length) {
    pages.push(makePage('首页', 'home', [createBlock('nav'), createBlock('text')]))
  }
  linkPages(pages)
  return pages
}

export function planToDoc(plan: DocPlan, base?: Doc): Doc {
  const now = Date.now()
  const pages = planToPages(plan)
  const title = plan.title?.trim() || base?.title || '未命名活动'
  return {
    id: base?.id ?? uid('doc'),
    title,
    slug: base?.slug ?? slugify(title),
    theme: sanitizeTheme(plan.theme, base?.theme ?? DEFAULT_THEME),
    pages,
    meta: sanitizeMeta(
      plan.meta,
      base?.meta ?? {
        org: '',
        startAt: '',
        place: '',
        deadline: '',
        capacity: 0,
        contact: '',
      }
    ),
    createdAt: base?.createdAt ?? now,
    updatedAt: now,
    // 发布状态要保留：PRD 要求改内容不改链接
    published: base?.published ?? null,
  }
}

/** 把现有文档再导出成方案（编辑 Copilot 传上下文给智能体时用） */
export function docToPlan(doc: Doc, reason: string, source: DocPlan['source'] = 'local'): DocPlan {
  return {
    intent: doc.title,
    reason,
    title: doc.title,
    theme: {
      primary: doc.theme.primary,
      accent: doc.theme.accent,
      bg: doc.theme.bg,
      text: doc.theme.text,
    },
    meta: doc.meta,
    pages: doc.pages.map((page) => ({
      name: page.name,
      role: page.role,
      blocks: page.blocks.map((block) => ({ type: block.type, props: block.props })),
    })),
    source,
  }
}

/* ------------------------------------------------------------------------- */
/* 本地规则生成（后端没起来 / 没配 Key 时的完整替代路径）                         */
/* ------------------------------------------------------------------------- */

interface SceneRule {
  id: string
  test: RegExp
  label: string
}

const SCENE_RULES: SceneRule[] = [
  { id: 'recruit', test: /心理社团|招新|纳新|招募|新成员|加入我们/, label: '心理社团招新' },
  { id: 'vote', test: /知识问答|心理科普|科普|问答|测试/, label: '心理知识问答' },
  { id: 'checkin', test: /自我关怀|正念|打卡|练习|习惯|情绪/, label: '自我关怀打卡' },
  {
    id: 'signup',
    test: /游园|团辅|报名|分享会|讲座|沙龙|活动/,
    label: '心晴活动',
  },
]

function detectScene(prompt: string): SceneRule {
  return SCENE_RULES.find((rule) => rule.test.test(prompt)) ?? SCENE_RULES[3]!
}

function extractTitle(prompt: string): string {
  const quoted = prompt.match(/[「『“"]([^」』”"]{2,24})[」』”"]/)
  if (quoted?.[1]) return quoted[1].trim()

  const named = prompt.match(/([\u4e00-\u9fa5A-Za-z0-9]{2,16}(?:社|协会|学生会|部门|小组))/)
  const event = prompt.match(/([\u4e00-\u9fa5A-Za-z0-9]{2,18}(?:招新|打卡|投票|比赛|讲座|夜跑|分享会|训练营|活动))/)
  if (named?.[1] && event?.[1]) {
    // 「摄影社」+「摄影社招新」会拼成「摄影社 摄影社招新」，很难看
    return event[1].includes(named[1]) ? event[1] : `${named[1]} ${event[1]}`
  }
  if (event?.[1]) return event[1]
  if (named?.[1]) return `${named[1]}招新`
  return ''
}

function extractMeta(prompt: string): Partial<DocMeta> {
  const meta: Partial<DocMeta> = {}

  const time = prompt.match(/(\d{1,2}\s*月\s*\d{1,2}\s*日(?:\s*(?:周|星期)[一二三四五六日])?(?:\s*\d{1,2}[:：]\d{2})?)/)
  if (time?.[1]) meta.startAt = time[1]

  const place = prompt.match(
    /(?:地点|在|于)\s*[:：]?\s*([\u4e00-\u9fa5A-Za-z0-9]{2,10}(?:楼|室|厅|场|馆|教室|操场|广场|食堂|中心|报告厅))/
  )
  if (place?.[1]) meta.place = place[1]

  const capacity = prompt.match(/(\d{1,4})\s*(?:个)?人/)
  if (capacity?.[1]) {
    const value = Number(capacity[1])
    if (value > 0 && value < 10000) meta.capacity = value
  }

  const org = prompt.match(/([\u4e00-\u9fa5]{2,12}(?:社|协会|学生会|团委|部|小组))/)
  if (org?.[1]) meta.org = org[1]

  return meta
}

function patchInfoCards(pages: Page[], meta: DocMeta): void {
  for (const page of pages) {
    for (const block of page.blocks) {
      if (block.type !== 'infoCard' || !Array.isArray(block.props.items)) continue
      block.props.items = block.props.items.map((item: any) => {
        if (item.label.includes('时间') && meta.startAt) return { ...item, value: meta.startAt }
        if (item.label.includes('地点') && meta.place) return { ...item, value: meta.place }
        if ((item.label.includes('主办') || item.label.includes('社团')) && meta.org) {
          return { ...item, value: meta.org }
        }
        if (item.label.includes('名额') && meta.capacity) return { ...item, value: `${meta.capacity} 人` }
        return item
      })
    }
  }
}

export interface LocalResult {
  doc: Doc
  reason: string
  scene: string
}

/**
 * 没有智能体时也能给出「像样」的初稿：
 * 关键词 → 场景模板 → 把用户话里的时间/地点/人数填进信息卡片。
 */
export function localDoc(prompt: string, base?: Doc): LocalResult {
  const scene = detectScene(prompt)
  const template = templateById(scene.id) ?? TEMPLATES[0]!
  const build = template.build()

  const extracted = extractMeta(prompt)
  const title = extractTitle(prompt) || build.title
  const meta: DocMeta = { ...build.meta, ...extracted }

  if (!meta.org) meta.org = build.meta.org

  const pages = build.pages
  patchInfoCards(pages, meta)

  // 用户说了活动名，就把首页的导航和 Banner 标题一起改掉，别让模板文案露馅
  const home = pages.find((page) => page.role === 'home')
  if (home) {
    for (const block of home.blocks) {
      if (block.type === 'nav' && title) block.props.title = title.slice(0, 12)
      if (block.type === 'banner') {
        if (title) block.props.title = title
        block.props.subtitle = block.props.subtitle || `${meta.org}主办`
      }
    }
  }

  const doc: Doc = {
    id: base?.id ?? uid('doc'),
    title,
    slug: base?.slug ?? slugify(title),
    theme: build.theme,
    pages,
    meta,
    createdAt: base?.createdAt ?? Date.now(),
    updatedAt: Date.now(),
    published: base?.published ?? null,
  }

  const filled: string[] = []
  if (extracted.startAt) filled.push(`时间「${extracted.startAt}」`)
  if (extracted.place) filled.push(`地点「${extracted.place}」`)
  if (extracted.capacity) filled.push(`人数「${extracted.capacity}」`)

  return {
    doc,
    scene: template.name,
    reason:
      `关键词匹配到「${template.name}」模板，生成了 ${doc.pages.length} 个页面、` +
      `${doc.pages.reduce((sum, page) => sum + page.blocks.length, 0)} 个组件。` +
      (filled.length
        ? `从你的描述里识别到 ${filled.join('、')}，已填进信息卡片。`
        : '时间、地点没识别到，信息卡片里先留「待补充」，你在右侧属性面板改就行。'),
  }
}

/* ------------------------------------------------------------------------- */
/* 给智能体的提示词                                                            */
/* ------------------------------------------------------------------------- */

const PLAN_SCHEMA = `{
  "title": "活动名称",
  "intent": "你理解到的需求",
  "reason": "为什么这样排页面",
  "theme": { "primary": "#2d5046", "accent": "#b88c72", "bg": "#faf8f5", "text": "#1f3129" },
  "meta": { "org": "", "startAt": "", "place": "", "deadline": "", "capacity": 0 },
  "pages": [
    { "name": "首页", "role": "home", "blocks": [
      { "type": "nav", "props": { "title": "社团名" } },
      { "type": "banner", "props": { "title": "主标题", "subtitle": "副标题" } },
      { "type": "infoCard", "props": { "title": "活动信息", "items": [ { "icon": "time", "label": "时间", "value": "" } ] } },
      { "type": "button", "props": { "text": "立即报名", "action": { "type": "page", "targetRole": "form" } } }
    ] }
  ]
}`

export function buildPlanPrompt(prompt: string): string {
  return [
    `帮我做一个「${prompt}」的校园心理活动页面。`,
    '请把页面方案写成下面的 JSON 结构，只输出 JSON：',
    PLAN_SCHEMA,
    '规则：',
    '1. block 的 type 只能用：nav/banner/heading/text/image/infoCard/button/form/radio/checkbox/qrcode/divider/countdown；',
    '2. 页面 3~5 个，role 只用 home/content/form/success，最后要有 form 页和 success 页；',
    '3. 用户没提供的时间、地点、人数一律留空字符串或 0，不要编造；',
    '4. 文案要短、温柔、口语化，别用「诚挚邀请」这类公文腔。',
    '5. 只帮助组织活动，不做心理诊断，不生成风险判断或治疗建议。',
    '6. 需要收集敏感信息时，加入说明用途、查看范围和匿名选项。',
  ].join('\n')
}

/** 编辑 Copilot 的上下文摘要，控制 token 别太夸张 */
export function describeDocForAgent(doc: Doc, pageIndex: number, selectedSummary: string): string {
  const page = doc.pages[pageIndex]
  const pageLines = doc.pages
    .map(
      (item, index) =>
        `${index + 1}. ${item.name}（${item.role}）：${item.blocks.map((block) => block.type).join(' / ')}`
    )
    .join('\n')

  return [
    `活动名称：${doc.title}`,
    `活动信息：时间 ${doc.meta.startAt || '未填'} / 地点 ${doc.meta.place || '未填'} / 主办 ${doc.meta.org || '未填'}`,
    `页面结构：\n${pageLines}`,
    `当前页：${page?.name ?? '未知'}`,
    `当前选中：${selectedSummary || '无'}`,
    '请针对当前页面给 2~3 条可执行的修改建议，每条一句话，说清改哪个组件、改成什么。不要输出代码。',
  ].join('\n')
}
