import type { Doc } from '@/types/editor'
import { themeCssVars } from '@/utils/theme'

export function safeName(title: string): string {
  return (title || '活动页面').replace(/[\\/:*?"<>|\s]+/g, '-').slice(0, 40)
}

function download(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1500)
}

export function downloadText(content: string, filename: string, type = 'text/plain;charset=utf-8'): void {
  download(new Blob([content], { type }), filename)
}

export function exportDocJson(doc: Doc): void {
  downloadText(JSON.stringify(doc, null, 2), `${safeName(doc.title)}.json`, 'application/json;charset=utf-8')
}

/** 把当前页面里能读到的 CSS 规则拼起来（跨域样式表会抛 SecurityError，跳过） */
function collectStyles(): string {
  const chunks: string[] = []
  for (const sheet of Array.from(document.styleSheets)) {
    let rules: CSSRuleList | null = null
    try {
      rules = sheet.cssRules
    } catch {
      continue
    }
    if (!rules) continue
    for (const rule of Array.from(rules)) {
      chunks.push(rule.cssText)
    }
  }
  return chunks.join('\n')
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/**
 * 拼出一份能双击打开的静态 HTML。
 *
 * 直接复用运行中的 DOM 与样式（避免第二套渲染逻辑），
 * 所以导出和预览长得一模一样；表单不会真的提交，页面顶部有说明。
 */
export function buildStaticHtml(pageEl: HTMLElement, doc: Doc): string {
  const styles = collectStyles()
  const vars = Object.entries(themeCssVars(doc.theme))
    .map(([key, value]) => `${key}:${value}`)
    .join(';')

  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
<title>${escapeHtml(doc.title)}</title>
<style>${styles}</style>
<style>
  .export-shell .screen__body { height: auto !important; overflow: visible !important; }
  .export-note { max-width: 390px; margin: 0 auto 14px; padding: 10px 14px; border-radius: 12px;
    background: #edf4f0; color: #213e36; font: 12px/1.6 system-ui, "Microsoft YaHei", sans-serif; }
</style>
</head>
<body class="export-body">
  <div class="export-shell" style="${vars}">
    <div class="export-note">静态导出 · ${escapeHtml(doc.title)} · 表单为展示用，收集数据请走「发布」。</div>
    ${pageEl.outerHTML}
  </div>
</body>
</html>`
}

export function exportStaticHtml(pageEl: HTMLElement, doc: Doc): void {
  downloadText(buildStaticHtml(pageEl, doc), `${safeName(doc.title)}.html`, 'text/html;charset=utf-8')
}

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    try {
      const area = document.createElement('textarea')
      area.value = text
      area.style.position = 'fixed'
      area.style.opacity = '0'
      document.body.appendChild(area)
      area.select()
      const ok = document.execCommand('copy')
      area.remove()
      return ok
    } catch {
      return false
    }
  }
}
