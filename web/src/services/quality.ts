import type { Doc, Issue, Page } from '@/types/editor'
import { metaOf } from '@/data/blockCatalog'
import { parseDate, uid } from '@/utils/misc'
import { contrastRatio } from '@/utils/theme'

/**
 * 发布前质量检查。
 *
 * 刻意写成纯函数 + 规则表：不依赖后端、不花钱、结果可解释。
 * 每条都带 pageId / blockId，界面能直接定位到组件。
 */

const LEVEL_ORDER: Record<Issue['level'], number> = { error: 0, warn: 1, info: 2 }

function issue(level: Issue['level'], title: string, detail: string, page: Page, blockId?: string): Issue {
  return { id: uid('issue'), level, title, detail, pageId: page.id, blockId }
}

function str(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

/* ------------------------------------------------------------------------- */
/* 心理活动的合规检查（心屿）                                                   */
/* ------------------------------------------------------------------------- */

/** 从标题和文案里判断这是不是心理类活动——决定后面几条规则要不要更严。 */
const WELLBEING_HINT = /心理|心晴|情绪|正念|减压|自我关怀|团辅|团体辅导|树洞|匿名|倾诉|关怀/

/** 这些字段出现在报名表上就不是"多收了一点"，是性质问题。 */
const SENSITIVE_FIELD =
  /身份证|身份号|学号|家庭住址|住址|父母|家长电话|病史|既往|服药|用药|诊断|自伤|自杀|抑郁史/

/** 页面里只要有其中一处，就认为"能找到人"。 */
const CARE_HINT = /心理中心|心理健康教育中心|热线|求助|危机干预|110|120|12356/

function formFieldsOf(doc: Doc): Array<{ label: string; placeholder: string; required: boolean; formId: string }> {
  const out: Array<{ label: string; placeholder: string; required: boolean; formId: string }> = []
  for (const page of doc.pages) {
    for (const block of page.blocks) {
      if (block.type !== 'form') continue
      const fields = Array.isArray(block.props.fields) ? block.props.fields : []
      for (const field of fields) {
        const item = field as Record<string, unknown>
        out.push({
          label: str(item.label).trim(),
          placeholder: str(item.placeholder).trim(),
          required: Boolean(item.required),
          formId: block.id,
        })
      }
    }
  }
  return out
}

/** 全文文本，用来判断"这是不是心理活动""有没有求助入口"。 */
function documentText(doc: Doc): string {
  const parts: string[] = [doc.title, doc.meta.org, doc.meta.place]
  for (const page of doc.pages) {
    parts.push(page.name)
    for (const block of page.blocks) {
      const props = block.props as Record<string, unknown>
      for (const key of ['text', 'title', 'subtitle', 'question', 'label', 'value', 'caption', 'buttonText']) {
        const value = props[key]
        if (typeof value === 'string') parts.push(value)
      }
      // 求助入口通常就在信息卡片里（icon=contact 的几行），不翻数组等于没查
      const items = props.items
      if (Array.isArray(items)) {
        for (const item of items) {
          if (!item || typeof item !== 'object') continue
          const row = item as Record<string, unknown>
          parts.push(str(row.label), str(row.value))
        }
      }
    }
  }
  return parts.join(' ')
}

function sensitiveFields(doc: Doc): string[] {
  const found = formFieldsOf(doc)
    .filter((field) => SENSITIVE_FIELD.test(field.label) || SENSITIVE_FIELD.test(field.placeholder))
    .map((field) => field.label || field.placeholder)
  return [...new Set(found)]
}

export function auditDoc(doc: Doc): Issue[] {
  const issues: Issue[] = []
  const pages = doc.pages

  if (!pages.length) {
    issues.push({
      id: uid('issue'),
      level: 'error',
      title: '没有任何页面',
      detail: '至少需要一个页面才能发布。',
      pageId: '',
    })
    return issues
  }

  const home = pages[0] as Page

  // 心理类活动有几条额外的规矩，先判断一次，后面多处要用
  const isWellbeing = WELLBEING_HINT.test(documentText(doc))

  /* ---------- 全局：活动信息完整性 ---------- */
  const missing: string[] = []
  if (!doc.meta.startAt.trim()) missing.push('时间')
  if (!doc.meta.place.trim()) missing.push('地点')
  if (!doc.meta.org.trim()) missing.push('主办方')
  if (missing.length) {
    issues.push(
      issue(
        'warn',
        `活动信息缺 ${missing.join('、')}`,
        '信息卡片、分享海报和二维码落地页都会用到这几项，先去右侧「页面」里补上。',
        home
      )
    )
  }

  if (doc.meta.capacity > 0 && doc.meta.deadline && parseDate(doc.meta.deadline) === null) {
    issues.push(
      issue('info', '报名截止时间格式看不懂', `「${doc.meta.deadline}」解析不了，建议写成 2026-10-25 22:00。`, home)
    )
  }

  if (doc.title.length > 26) {
    issues.push(issue('warn', '活动名称偏长', '分享海报和导航栏会被截断，建议控制在 20 字以内。', home))
  }

  /* ---------- 主题对比度 ---------- */
  const buttonContrast = contrastRatio('#ffffff', doc.theme.primary)
  if (buttonContrast < 3) {
    issues.push(
      issue(
        'warn',
        '主色上的白字对比度不足',
        `当前对比度 ${buttonContrast.toFixed(1)}:1，低于 3:1。按钮文字可能看不清，把主色调深一点。`,
        home
      )
    )
  }
  const textContrast = contrastRatio(doc.theme.text, doc.theme.bg)
  if (textContrast < 4.5) {
    issues.push(
      issue('warn', '正文与背景对比度不足', `当前 ${textContrast.toFixed(1)}:1，低于 4.5:1。深色主题尤其要注意。`, home)
    )
  }

  /* ---------- 逐页检查 ---------- */
  const hasFormPage = pages.some((page) => page.role === 'form')
  const hasSuccessPage = pages.some((page) => page.role === 'success')
  const nameCount = new Map<string, number>()
  for (const page of pages) nameCount.set(page.name, (nameCount.get(page.name) ?? 0) + 1)

  for (const page of pages) {
    const visible = page.blocks.filter((block) => !block.hidden)

    if (!visible.length) {
      issues.push(issue('warn', `「${page.name}」是空页面`, '空页面发出去会很难看，至少放个标题和一句话。', page))
    }
    if ((nameCount.get(page.name) ?? 0) > 1) {
      issues.push(issue('info', `页面重名：${page.name}`, '发布后不太好辨认，建议改个名字。', page))
    }
    if (visible.length > 22) {
      issues.push(issue('info', `「${page.name}」组件偏多`, '手机上要滑很久，考虑拆成两页。', page))
    }

    const types = visible.map((block) => block.type)
    const hasInput = types.some((type) => type === 'form' || type === 'radio' || type === 'checkbox')
    const hasSubmit = visible.some((block) => {
      if (block.type !== 'button') return false
      const action = block.props.action
      return action?.type === 'submit'
    })
    const formBlocks = visible.filter((block) => block.type === 'form')
    const choiceBlocks = visible.filter((block) => block.type === 'radio' || block.type === 'checkbox')

    if (page.role === 'form' && !hasInput) {
      issues.push(
        issue('error', `「${page.name}」没有可填写的组件`, '报名页至少要有一个表单 / 单选框 / 多选框。', page)
      )
    }
    if (hasInput && !hasSubmit && !formBlocks.length) {
      issues.push(issue('error', `「${page.name}」没有提交入口`, '有选择项却没有提交按钮，参与者填完也没法交。', page))
    }

    /* 表单块 */
    for (const block of formBlocks) {
      const fields = Array.isArray(block.props.fields) ? block.props.fields : []
      if (!fields.length) {
        issues.push(issue('error', '表单是空的', '一个字段都没有，参与者没法填。', page, block.id))
        continue
      }
      const labels = fields.map((field: any) => str(field.label).trim())
      if (labels.some((label) => !label)) {
        issues.push(issue('error', '有字段没写名称', '空字段用户看不懂要填什么。', page, block.id))
      }
      const duplicated = labels.filter((label, index) => label && labels.indexOf(label) !== index)
      if (duplicated.length) {
        issues.push(
          issue(
            'warn',
            `字段重复：${[...new Set(duplicated)].join('、')}`,
            '同一个表单里字段名重复，容易填错。',
            page,
            block.id
          )
        )
      }
      if (!fields.some((field: any) => field.required)) {
        issues.push(
          issue(
            'warn',
            '没有任何必填字段',
            isWellbeing
              ? '心理类活动至少把联系方式设为必填就够了，其余尽量选填——别靠「全都必填」来拿信息。'
              : '关键字段（姓名、手机号）建议设为必填。',
            page,
            block.id
          )
        )
      }
      const hasContact = fields.some((field: any) => field.type === 'tel' || field.type === 'email')
      if (!hasContact && !isWellbeing) {
        issues.push(issue('info', '缺少联系方式字段', '加一个手机号或邮箱，报名后你才联系得上人。', page, block.id))
      }
      if (fields.length > 8) {
        issues.push(
          issue('warn', '字段太多', `${fields.length} 个字段，手机上填起来很累，建议压到 6 个以内。`, page, block.id)
        )
      }
      const buttonText = str(block.props.buttonText)
      if (!buttonText.trim()) {
        issues.push(issue('error', '提交按钮没有文案', '按钮是空的，用户不知道点了会发生什么。', page, block.id))
      } else if (buttonText.length > 8) {
        issues.push(
          issue('warn', '提交按钮文案偏长', `「${buttonText}」在窄屏上可能换行，建议 4~6 个字。`, page, block.id)
        )
      }
    }

    /* 选择项 */
    for (const block of choiceBlocks) {
      const options = Array.isArray(block.props.options) ? block.props.options : []
      if (options.length < 2) {
        issues.push(issue('error', '选项不足两个', '单选/多选至少要有两个选项。', page, block.id))
      }
      if (block.type === 'checkbox' && Number(block.props.max) > options.length && options.length > 0) {
        issues.push(issue('info', '多选上限超过选项数', '这个上限等于没限。', page, block.id))
      }
    }

    /* 主要组件文案 */
    for (const block of visible) {
      const meta = metaOf(block.type)
      if (block.type === 'heading') {
        const text = str(block.props.text)
        if (!text.trim()) {
          issues.push(issue('error', '标题是空的', `删掉或者补一句话。`, page, block.id))
        } else if (text.length > 22) {
          issues.push(
            issue(
              'warn',
              '标题太长',
              `「${text.slice(0, 12)}…」共 ${text.length} 字，手机上会占三四行。`,
              page,
              block.id
            )
          )
        }
      }

      if (block.type === 'button') {
        const text = str(block.props.text).trim()
        if (!text) {
          issues.push(issue('error', '按钮没有文案', '空按钮等于没有。', page, block.id))
        } else if (['提交', '点击', '确定', '按钮'].includes(text)) {
          issues.push(
            issue('warn', `按钮文案「${text}」太泛`, '写清动作，例如「立即报名」「提交投票」。', page, block.id)
          )
        } else if (text.length > 10) {
          issues.push(issue('warn', '按钮文案偏长', `「${text}」建议压到 6 个字以内。`, page, block.id))
        }
      }

      if (block.type === 'text') {
        const text = str(block.props.text)
        if (text.length > 220) {
          issues.push(
            issue(
              'warn',
              '这段文字太长',
              `${text.length} 字。参与者不会在手机上读完，拆成两段或做成折叠。`,
              page,
              block.id
            )
          )
        }
        if (/[A-Za-z0-9]{30,}/.test(text)) {
          issues.push(issue('warn', '疑似会溢出', '这段里有超长英文/数字串，移动端可能横向溢出。', page, block.id))
        }
        if (!text.trim()) {
          issues.push(issue('info', `${meta.label}内容为空`, '空文本块只占位置，建议删掉。', page, block.id))
        }
      }

      if (block.type === 'banner') {
        if (!str(block.props.title).trim()) {
          issues.push(issue('warn', 'Banner 没有主标题', '首屏第一句话最重要，别空着。', page, block.id))
        }
        if (!str(block.props.image).trim()) {
          issues.push(issue('info', 'Banner 用的是渐变底', '换成真实海报或活动照片会更有说服力。', page, block.id))
        }
      }

      if (block.type === 'image' && !str(block.props.url).trim()) {
        issues.push(issue('info', '图片是占位图', '在右侧属性面板填图片地址。', page, block.id))
      }

      if (block.type === 'nav' && !str(block.props.title).trim()) {
        issues.push(issue('warn', '导航栏没有标题', '参与者不知道自己在哪个页面。', page, block.id))
      }

      if (block.type === 'countdown') {
        const target = str(block.props.target)
        if (!target.trim()) {
          issues.push(issue('warn', '倒计时没设时间', '填个截止时间，例如 2026-10-25 22:00。', page, block.id))
        } else {
          const stamp = parseDate(target)
          if (stamp === null) {
            issues.push(issue('warn', '倒计时时间解析不了', '格式建议 2026-10-25 22:00。', page, block.id))
          } else if (stamp < Date.now()) {
            issues.push(issue('info', '倒计时目标时间已过', '页面会显示「已截止」文案。', page, block.id))
          }
        }
      }

      if (block.type === 'qrcode' && !str(block.props.url).trim()) {
        issues.push(issue('info', '二维码还没设内容', '发布后会自动使用活动链接。', page, block.id))
      }
    }
  }

  /* ---------- 心理活动的隐私与合规（心屿） ---------- */
  // 放在页面循环外面：判断「有没有求助入口」「有没有敏感字段」得看全文档。
  const sensitive = sensitiveFields(doc)
  if (sensitive.length) {
    issues.push(
      issue(
        'error',
        `表单在收集敏感信息：${sensitive.join('、')}`,
        '心理类活动不该收身份证号、学号、家庭情况、既往病史这类信息。报名表不是筛查工具——收得越少，别人才越敢来。',
        pages[0] as Page
      )
    )
  }

  if (isWellbeing) {
    if (!CARE_HINT.test(documentText(doc))) {
      issues.push(
        issue(
          'warn',
          '没有任何求助入口',
          '心理类活动的页面上要有一处能找到心理中心或热线。有人点进来的时候状态可能已经很差，页面得能接住。',
          pages[0] as Page
        )
      )
    }

    const realName = formFieldsOf(doc).find(
      (field) => field.required && /姓名|真名/.test(field.label) && !/昵称/.test(field.label)
    )
    if (realName) {
      issues.push(
        issue(
          'warn',
          `「${realName.label}」要求填真名`,
          '建议改成「昵称」并设为选填。想参加又怕被认出来的人，会在这一屏退出。',
          pages[0] as Page,
          realName.formId
        )
      )
    }
  }

  if (hasFormPage && !hasSuccessPage) {
    issues.push(issue('warn', '没有「提交成功」页', '提交后没有反馈，参与者会以为没交上。', pages[0] as Page))
  }

  if (!doc.published) {
    issues.push({
      id: uid('issue'),
      level: 'info',
      title: '还没发布',
      detail: '发布后才能拿到链接和二维码。',
      pageId: home.id,
    })
  }

  return issues.sort((a, b) => LEVEL_ORDER[a.level] - LEVEL_ORDER[b.level])
}

export function summarize(issues: Issue[]): {
  error: number
  warn: number
  info: number
} {
  return {
    error: issues.filter((item) => item.level === 'error').length,
    warn: issues.filter((item) => item.level === 'warn').length,
    info: issues.filter((item) => item.level === 'info').length,
  }
}
