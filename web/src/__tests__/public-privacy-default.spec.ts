import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import PublicActivityView from '@/views/PublicActivityView.vue'
import { createDocFromTemplate } from '@/data/templates'
import { normalizeActivityPrivacy } from '@/composables/useActivityPrivacy'
import { getPublicActivity, getPublicPrivacy } from '@/services/public'

vi.mock('@/services/public', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/public')>()
  return {
    ...actual,
    getPublicActivity: vi.fn(),
    getPublicPrivacy: vi.fn(),
    recordPublicEvent: vi.fn(),
  }
})

describe('公开活动隐私设置', () => {
  afterEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('服务端没有返回 consentRequired 时，按“需要同意”处理', () => {
    const privacy = normalizeActivityPrivacy({
      privacyMode: 'optional',
      consentText: '请阅读参与说明',
      retentionDays: 30,
    })

    expect(privacy.consentRequired).toBe(true)
    expect(privacy.consentText).toBe('请阅读参与说明')
  })

  it('服务端明确返回 false 时，尊重该设置', () => {
    const privacy = normalizeActivityPrivacy({
      privacyMode: 'anonymous',
      consentRequired: false,
      consentText: '本活动无需单独同意',
      retentionDays: 7,
    })

    expect(privacy.consentRequired).toBe(false)
  })

  it('老隐私接口不带 consentRequired 时，页面仍渲染同意说明', async () => {
    const document = createDocFromTemplate('vote')
    expect(document).toBeTruthy()
    const home = document!.pages[0]!
    home.blocks.push({
      id: 'form-test',
      type: 'form',
      props: { title: '', buttonText: '提交', successHint: '', fields: [] },
      style: { ...home.blocks[0]!.style },
      hidden: false,
    })
    home.blocks.push({
      id: 'duplicate-submit',
      type: 'button',
      props: { text: '提交', action: { type: 'submit', pageId: '', url: '' } },
      style: { ...home.blocks[0]!.style },
      hidden: false,
    })
    vi.mocked(getPublicActivity).mockResolvedValue({
      activity: { name: '心理知识问答', slug: 'quiz', status: 'published' },
      document: document!,
    })
    vi.mocked(getPublicPrivacy).mockResolvedValue({
      privacyMode: 'optional',
      consentText: '请阅读参与说明',
      retentionDays: 30,
    })

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/p/:slug', component: PublicActivityView },
        { path: '/projects', component: { template: '<div />' } },
        { path: '/resources', component: { template: '<div />' } },
        { path: '/trace', component: { template: '<div />' } },
      ],
    })
    await router.push('/p/quiz')
    await router.isReady()

    const wrapper = mount(PublicActivityView, {
      global: { plugins: [createPinia(), router] },
    })
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()

    expect(wrapper.find('.form-consent').exists()).toBe(true)
    expect(wrapper.text()).toContain('请阅读参与说明')
    // 表单自带提交按钮，页面里重复的「提交」按钮不应再渲染。
    const submitButtons = wrapper.findAll('button.cta').filter((button) => button.text() === '提交')
    expect(submitButtons.length).toBe(1)
    wrapper.unmount()
  })
})
