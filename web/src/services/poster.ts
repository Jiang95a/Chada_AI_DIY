import type { Doc } from '@/types/editor'
import { encode } from 'uqr'
import { safeName } from './exporters'
import { isDarkColor, mixColor } from '@/utils/theme'

/**
 * 分享海报：纯 Canvas 画出来，不依赖 html2canvas 之类的重库。
 * 750×1000（微信小图习惯尺寸），按 2 倍像素绘制保证清晰。
 */

const W = 750
const H = 1000

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.lineTo(x + w - radius, y)
  ctx.arcTo(x + w, y, x + w, y + radius, radius)
  ctx.lineTo(x + w, y + h - radius)
  ctx.arcTo(x + w, y + h, x + w - radius, y + h, radius)
  ctx.lineTo(x + radius, y + h)
  ctx.arcTo(x, y + h, x, y + h - radius, radius)
  ctx.lineTo(x, y + radius)
  ctx.arcTo(x, y, x + radius, y, radius)
  ctx.closePath()
}

function fitText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string {
  if (ctx.measureText(text).width <= maxWidth) return text
  let result = text
  while (result.length > 1 && ctx.measureText(`${result}…`).width > maxWidth) {
    result = result.slice(0, -1)
  }
  return `${result}…`
}

function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number, maxLines: number): string[] {
  const lines: string[] = []
  let current = ''
  for (const char of text) {
    const candidate = current + char
    if (ctx.measureText(candidate).width > maxWidth && current) {
      lines.push(current)
      current = char
      if (lines.length === maxLines) break
    } else {
      current = candidate
    }
  }
  if (lines.length < maxLines && current) lines.push(current)
  if (lines.length === maxLines && text.length > lines.join('').length) {
    lines[maxLines - 1] = fitText(ctx, `${lines[maxLines - 1] ?? ''}…`, maxWidth)
  }
  return lines
}

function drawQr(ctx: CanvasRenderingContext2D, url: string, x: number, y: number, size: number): void {
  ctx.fillStyle = '#ffffff'
  roundRect(ctx, x, y, size, size, 14)
  ctx.fill()

  const qr = encode(url || 'https://campus.local', { ecc: 'M', border: 1 })
  const inner = size - 28
  const moduleSize = inner / qr.size
  ctx.fillStyle = '#111827'
  qr.data.forEach((row, rowIndex) => {
    let col = 0
    while (col < row.length) {
      if (!row[col]) {
        col += 1
        continue
      }
      let run = 1
      while (col + run < row.length && row[col + run]) run += 1
      ctx.fillRect(
        x + 14 + col * moduleSize,
        y + 14 + rowIndex * moduleSize,
        Math.ceil(run * moduleSize),
        Math.ceil(moduleSize)
      )
      col += run
    }
  })
}

export async function buildPosterCanvas(doc: Doc, url: string): Promise<HTMLCanvasElement> {
  const canvas = document.createElement('canvas')
  const dpr = 2
  canvas.width = W * dpr
  canvas.height = H * dpr

  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('当前浏览器不支持 Canvas')
  ctx.scale(dpr, dpr)
  ctx.textBaseline = 'top'

  const primary = doc.theme.primary
  const accent = doc.theme.accent

  /* 背景 */
  const bg = ctx.createLinearGradient(0, 0, W, H)
  bg.addColorStop(0, primary)
  bg.addColorStop(1, accent)
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, W, H)

  ctx.globalAlpha = 0.16
  ctx.fillStyle = '#ffffff'
  ctx.beginPath()
  ctx.arc(96, 84, 130, 0, Math.PI * 2)
  ctx.fill()
  ctx.beginPath()
  ctx.arc(690, 940, 190, 0, Math.PI * 2)
  ctx.fill()
  ctx.globalAlpha = 0.1
  ctx.strokeStyle = '#ffffff'
  ctx.lineWidth = 2
  for (let i = 0; i < 5; i += 1) {
    ctx.beginPath()
    ctx.moveTo(-60 + i * 190, H + 40)
    ctx.lineTo(180 + i * 190, -40)
    ctx.stroke()
  }
  ctx.globalAlpha = 1

  /* 卡片 */
  const card = { x: 48, y: 112, w: W - 96, h: H - 112 - 64 }
  ctx.save()
  ctx.shadowColor = 'rgba(15, 23, 42, 0.28)'
  ctx.shadowBlur = 46
  ctx.shadowOffsetY = 20
  ctx.fillStyle = '#ffffff'
  roundRect(ctx, card.x, card.y, card.w, card.h, 34)
  ctx.fill()
  ctx.restore()

  const contentX = card.x + 42
  const contentW = card.w - 84

  /* 小标签 */
  ctx.font = '600 15px "PingFang SC", "Microsoft YaHei", sans-serif'
  const chipText = '心晴活动 · 扫码查看'
  const chipW = ctx.measureText(chipText).width + 28
  ctx.fillStyle = mixColor(primary, '#ffffff', 0.88)
  roundRect(ctx, contentX, card.y + 34, chipW, 32, 16)
  ctx.fill()
  ctx.fillStyle = isDarkColor(primary) ? primary : mixColor(primary, '#111827', 0.25)
  ctx.fillText(chipText, contentX + 14, card.y + 42)

  /* 标题 */
  ctx.fillStyle = primary
  roundRect(ctx, contentX, card.y + 34 + 32 + 26, 52, 7, 4)
  ctx.fill()

  ctx.fillStyle = '#111827'
  ctx.font = '700 38px "PingFang SC", "Microsoft YaHei", sans-serif'
  const titleLines = wrapText(ctx, doc.title || '活动邀请', contentW, 2)
  const titleTop = card.y + 34 + 32 + 26 + 7 + 22
  titleLines.forEach((line, index) => {
    ctx.fillText(line, contentX, titleTop + index * 50)
  })

  /* 信息行 */
  const metaStart = titleTop + titleLines.length * 50 + 20
  const rows: [string, string][] = [
    ['时间', doc.meta.startAt || '待补充'],
    ['地点', doc.meta.place || '待补充'],
    ['主办', doc.meta.org || '待补充'],
  ]
  ctx.font = '400 18px "PingFang SC", "Microsoft YaHei", sans-serif'
  rows.forEach(([label, value], index) => {
    const rowY = metaStart + index * 42
    ctx.fillStyle = mixColor(primary, '#ffffff', 0.15)
    ctx.beginPath()
    ctx.arc(contentX + 6, rowY + 12, 5, 0, Math.PI * 2)
    ctx.fill()

    ctx.fillStyle = '#98a2b3'
    ctx.font = '400 18px "PingFang SC", "Microsoft YaHei", sans-serif'
    ctx.fillText(label, contentX + 22, rowY)

    ctx.fillStyle = '#1f2937'
    ctx.font = '600 21px "PingFang SC", "Microsoft YaHei", sans-serif'
    const remaining = contentW - 60
    ctx.fillText(fitText(ctx, value, remaining), contentX + 78, rowY - 1)
  })

  /* 分隔线 */
  const lineY = metaStart + rows.length * 42 + 16
  ctx.strokeStyle = '#eef0f6'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(contentX, lineY)
  ctx.lineTo(contentX + contentW, lineY)
  ctx.stroke()

  /* 活动简介：取首页第一段文本，别让海报中间空一大块 */
  const home = doc.pages.find((page) => page.role === 'home') ?? doc.pages[0]
  const introBlock = home?.blocks.find((block) => block.type === 'text' && !block.hidden)
  const intro = introBlock
    ? String(introBlock.props.text ?? '')
        .replace(/\s+/g, ' ')
        .trim()
    : ''
  if (intro) {
    ctx.fillStyle = '#667085'
    ctx.font = '400 20px "PingFang SC", "Microsoft YaHei", sans-serif'
    const lines = wrapText(ctx, intro, contentW, 3)
    lines.forEach((line, index) => {
      ctx.fillText(line, contentX, lineY + 28 + index * 34)
    })
  }

  /* 二维码 */
  const qrSize = 236
  const qrX = card.x + (card.w - qrSize) / 2
  const qrY = card.y + card.h - qrSize - 92
  ctx.save()
  ctx.shadowColor = 'rgba(15, 23, 42, 0.12)'
  ctx.shadowBlur = 24
  ctx.shadowOffsetY = 8
  drawQr(ctx, url, qrX, qrY, qrSize)
  ctx.restore()

  ctx.fillStyle = '#475467'
  ctx.font = '500 18px "PingFang SC", "Microsoft YaHei", sans-serif'
  const tip = '扫码查看活动详情 · 在线报名'
  const tipW = ctx.measureText(tip).width
  ctx.fillText(tip, (W - tipW) / 2, qrY + qrSize + 18)

  /* 底部链接 */
  ctx.fillStyle = '#b3bac7'
  ctx.font = '400 13px "PingFang SC", "Microsoft YaHei", sans-serif'
  const linkText = fitText(ctx, url.replace(/^https?:\/\//, ''), contentW)
  const linkW = ctx.measureText(linkText).width
  ctx.fillText(linkText, (W - linkW) / 2, card.y + card.h - 30)

  return canvas
}

function download(blob: Blob, filename: string): void {
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1500)
}

export async function downloadSharePoster(doc: Doc, url: string): Promise<void> {
  const canvas = await buildPosterCanvas(doc, url)
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'))
  if (!blob) throw new Error('海报生成失败')
  download(blob, `${safeName(doc.title)}-分享海报.png`)
}

export async function posterDataUrl(doc: Doc, url: string): Promise<string> {
  const canvas = await buildPosterCanvas(doc, url)
  return canvas.toDataURL('image/png')
}
