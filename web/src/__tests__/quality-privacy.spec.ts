import { describe, expect, it } from 'vitest'

import { createBlock, DEFAULT_THEME } from '@/data/blockCatalog'
import { auditDoc } from '@/services/quality'
import type { Block, Doc, Page } from '@/types/editor'

/**
 * 发布前检查里「心理活动合规」那一段。
 *
 * 这几条不是样式建议，是**内容红线**：报名表收身份证、心理活动没有求助入口，
 * 都是评审和真实用户会直接指出来的问题。所以它们在 auditDoc 里是 error / warn，
 * 不是 info。
 */

function makePage(role: Page['role'], name: string, blocks: Block[]): Page {
  return { id: `page_${name}`, name, role, blocks, bg: '' }
}

function makeDoc(pages: Page[], title = '心晴分享会', org = '心理社'): Doc {
  return {
    id: 'doc_test',
    title,
    slug: 'xinqing',
    theme: { ...DEFAULT_THEME },
    pages,
    meta: { org, startAt: '2026-11-10 19:00', place: '学活 305', deadline: '', capacity: 0, contact: '' },
    createdAt: 0,
    updatedAt: 0,
    published: null,
  }
}

function formWithFields(labels: Array<{ label: string; required?: boolean }>): Block {
  return createBlock('form', {
    title: '报名信息',
    buttonText: '提交报名',
    fields: labels.map((item, index) => ({
      id: `f_${index}`,
      label: item.label,
      type: 'text',
      required: item.required ?? false,
      placeholder: '',
      options: [],
    })),
  })
}

function careCard(): Block {
  return createBlock('infoCard', {
    title: '如果现在不太好受',
    items: [{ id: 'it_1', icon: 'contact', label: '心理中心', value: '学活 305' }],
  })
}

function titles(doc: Doc): string[] {
  return auditDoc(doc).map((issue) => issue.title)
}

describe('心理活动的发布前合规检查', () => {
  it('收集身份证号会直接拦下，不许发布', () => {
    const doc = makeDoc([
      makePage('home', '首页', [createBlock('nav', { title: '心晴分享会' }), careCard()]),
      makePage('form', '报名', [formWithFields([{ label: '真实姓名' }, { label: '身份证号' }])]),
    ])

    const issues = auditDoc(doc)
    const hit = issues.find((item) => item.level === 'error' && item.title.includes('敏感信息'))
    expect(hit).toBeTruthy()
    expect(hit!.title).toContain('身份证号')
  })

  it('学号、家庭住址、既往病史都算敏感信息', () => {
    for (const label of ['学号', '家庭住址', '既往病史', '是否服药']) {
      const doc = makeDoc([
        makePage('home', '首页', [createBlock('nav', { title: '心晴分享会' }), careCard()]),
        makePage('form', '报名', [formWithFields([{ label }])]),
      ])
      expect(titles(doc).join(' ')).toContain('敏感信息')
    }
  })

  it('心理活动没有求助入口会提醒', () => {
    const doc = makeDoc([
      makePage('home', '首页', [createBlock('nav', { title: '心晴分享会' }), createBlock('text', { text: '一起来坐坐' })]),
      makePage('form', '报名', [formWithFields([{ label: '昵称' }])]),
    ])

    expect(titles(doc)).toContain('没有任何求助入口')
  })

  it('页面里写了心理中心就不再提示求助入口', () => {
    const doc = makeDoc([
      makePage('home', '首页', [createBlock('nav', { title: '心晴分享会' }), careCard()]),
      makePage('form', '报名', [formWithFields([{ label: '昵称' }])]),
    ])

    expect(titles(doc)).not.toContain('没有任何求助入口')
  })

  it('必填真实姓名会建议改成昵称', () => {
    const doc = makeDoc([
      makePage('home', '首页', [createBlock('nav', { title: '心晴分享会' }), careCard()]),
      makePage('form', '报名', [formWithFields([{ label: '姓名', required: true }])]),
    ])

    expect(titles(doc).some((title) => title.includes('要求填真名'))).toBe(true)
  })

  it('「昵称」不会被当成真名要求', () => {
    const doc = makeDoc([
      makePage('home', '首页', [createBlock('nav', { title: '心晴分享会' }), careCard()]),
      makePage('form', '报名', [formWithFields([{ label: '昵称', required: true }])]),
    ])

    expect(titles(doc).some((title) => title.includes('要求填真名'))).toBe(false)
  })

  it('普通招新页不会被心理活动的规矩误伤', () => {
    const doc = makeDoc(
      [
        makePage('home', '首页', [createBlock('nav', { title: '摄影社招新' })]),
        makePage('form', '报名', [formWithFields([{ label: '姓名', required: true }])]),
      ],
      '光影摄影社 2026 秋季招新',
      '光影摄影社'
    )

    const all = titles(doc).join(' ')
    expect(all).not.toContain('求助入口')
    expect(all).not.toContain('敏感信息')
    expect(all).not.toContain('要求填真名')
  })
})
