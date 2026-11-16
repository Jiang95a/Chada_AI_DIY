import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DynamicParticipation from '@/components/public/DynamicParticipation.vue'
import TraceTimelineView from '@/views/TraceTimelineView.vue'
import { emptyTrace } from '@/types/trace'
import { hasCareWords, parseTrace } from '@/services/traceParser'
import { useTraceStore } from '@/stores/trace'

describe('动态参与规则引擎', () => {
  it('从自由输入里抽取标签，并重算可走路径', async () => {
    const trace = emptyTrace('u1', 'a1', 'mental')
    const patch = await parseTrace({ text: '期末快到了，天天失眠，但又不敢跟家里说。', trace })

    expect(patch.tags?.map((tag) => tag.content)).toEqual(expect.arrayContaining(['压力', '睡眠', '家庭']))
    expect(patch.sessionContext?.availablePaths).toEqual(expect.arrayContaining(['sleep']))
    expect(patch.sessionContext?.currentHint).toContain('手机')
    expect(patch.summary).toContain('压力')
  })

  it('用户改主意时记录一次变化，而不是覆盖原标签', async () => {
    const first = await parseTrace({ text: '最近压力很大，睡不着。', trace: emptyTrace('u1', 'a1') })
    const trace = { ...emptyTrace('u1', 'a1'), ...first }
    const second = await parseTrace({ text: '其实我改主意了，我想先认识几个人。', trace })

    expect(second.tags?.map((tag) => tag.content)).toEqual(expect.arrayContaining(['压力', '睡眠', '想认识人']))
    expect(second.change).toBeTruthy()
    expect(second.summary).toContain('想认识人')
    expect(second.summary).toContain('压力')
    expect(second.promise).toBeUndefined()
  })

  it('职规域共用同一套返回结构', async () => {
    const patch = await parseTrace({ text: '想做产品，但怕选错。', trace: emptyTrace('u1', 'a2', 'career') })

    expect(patch.tags?.map((tag) => tag.content)).toEqual(expect.arrayContaining(['方向', '怕选错']))
    expect(patch.sessionContext?.availablePaths).toEqual(expect.arrayContaining(['map']))
  })

  it('只认明确的自伤表达，不误伤日常用语', () => {
    expect(hasCareWords('累死了，笑死')).toBe(false)
    expect(hasCareWords('我不想活了')).toBe(true)
  })

  it('有时间的抱怨不等于承诺', async () => {
    const patch = await parseTrace({ text: '这周考试好多，压力特别大。', trace: emptyTrace('u1', 'a3') })
    expect(patch.promise).toBeUndefined()
  })

  it('明确说要去做时才记下承诺', async () => {
    const patch = await parseTrace({
      text: '这周我想去问一个做产品的人三个问题。',
      trace: emptyTrace('u1', 'a4', 'career'),
    })
    expect(patch.promise).toContain('这周我想去问')
  })
})

describe('轨迹存储', () => {
  afterEach(() => {
    localStorage.clear()
  })

  it('便签勾选「存进我的轨迹」后生成私有副本', async () => {
    localStorage.setItem('diy-campus:device-id', 'device-store')
    setActivePinia(createPinia())
    const store = useTraceStore()

    await store.absorbNote('a9', 'mental', { text: '今天说出来以后，好像轻了一点。', mood: '还行' })

    const trace = store.get('a9')
    expect(trace?.notes?.[0]?.text).toContain('今天说出来')
    expect(trace?.notes?.[0]?.candidateTraceId).toBeTruthy()
    expect(trace?.summary).toBe('你在这里留下了一句话。')

    const saved = JSON.parse(localStorage.getItem('diy-campus:trace:v1:device-store:a9') ?? '{}')
    expect(saved.notes.length).toBe(1)
  })
})

describe('我的轨迹页', () => {
  afterEach(() => {
    localStorage.clear()
  })

  it('显示摘要、变化与未决问题，并支持单条删除', async () => {
    localStorage.setItem('diy-campus:device-id', 'device-test')
    const trace = {
      ...emptyTrace('device-test', 'a1'),
      summary: '这次你提到「压力」，想先缓一缓。',
      change: '从「想休息」到「压力」',
      openQuestion: '这周有没有哪个时刻，压力比平时轻一点？',
      tags: [{ content: '压力', source: 'auto' as const, createdAt: Date.now() }],
      updatedAt: Date.now(),
    }
    localStorage.setItem('diy-campus:trace:v1:device-test:a1', JSON.stringify(trace))

    const wrapper = mount(TraceTimelineView, {
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { props: ['to'], template: '<a><slot /></a>' } },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('这次你提到「压力」')
    expect(wrapper.text()).toContain('从「想休息」到「压力」')
    expect(wrapper.text()).toContain('还没回答')

    await wrapper.find('.trace-item header button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.trace-empty').exists()).toBe(true)
    expect(Object.keys(localStorage).some((key) => key.startsWith('diy-campus:trace:v1:'))).toBe(false)
  })
})

describe('动态参与面板', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  function mountPanel() {
    return mount(DynamicParticipation, {
      props: { slug: 'a1' },
      global: {
        plugins: [createPinia()],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
  }

  it('点快捷入口后面板当场变化，不需要下一步', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    const buttons = wrapper.findAll('.dyn-quick button')
    expect(buttons.length).toBe(4)
    await buttons[1]!.trigger('click')
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(wrapper.find('.dyn-tag').text()).toContain('想表达')
    expect(wrapper.find('.dyn-hint').text()).toContain('想说的话')
    expect(wrapper.find('.dyn-summary').exists()).toBe(true)
  })

  it('自由输入会新增标签、更新提示并写入本机轨迹', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.find('.dyn-input textarea').setValue('期末快到了，天天失眠，但又不敢跟家里说。')
    await wrapper.find('.dyn-input-foot button').trigger('click')
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))

    const tagText = wrapper.findAll('.dyn-tag').map((tag) => tag.text())
    expect(tagText.join(' ')).toContain('睡眠')
    expect(tagText.join(' ')).toContain('家庭')
    expect(wrapper.find('.dyn-open-question').text()).toContain('下次想问你')

    const keys = Object.keys(localStorage).filter((key) => key.startsWith('diy-campus:trace:v1:'))
    expect(keys.length).toBeGreaterThan(0)
    const saved = JSON.parse(localStorage.getItem(keys[0]!) ?? '{}')
    expect(saved.activityId).toBe('a1')
    expect(saved.tags.length).toBeGreaterThan(0)
  })
})
