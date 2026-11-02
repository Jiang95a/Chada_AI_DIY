import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'
import { appRoutes } from '@/router'

/**
 * 这两个功能是「点了有没有反应」型的，光看代码不算数，得真挂载一次：
 *   1. 模板灵感 —— 从死锚点改成真路由，页面上要真有 4 张模板卡
 *   2. AI 生成 —— 左侧栏按钮要进入独立的全屏生成页
 */

async function mountAt(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: appRoutes })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 0))
  await flushPromises()
  return wrapper
}

describe('模板灵感与 AI 生成页面', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    localStorage.clear()
    // 路由守卫要求登录态，这里塞一个假 token 只为过门（不发请求）
    localStorage.setItem('diy-campus:access-token', 'test-token')
    // 明确模拟后端离线，验证模板与 AI 的本地降级链，避免假 token 触发 401 跳登录。
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('模板页渲染 4 张模板卡', async () => {
    const wrapper = await mountAt('/templates')
    expect(wrapper.findAll('.template-card').length).toBe(4)
    expect(wrapper.text()).toContain('用这个模板创建')
    wrapper.unmount()
  })

  it('左侧栏「模板灵感」是真链接，不是死锚点', async () => {
    const wrapper = await mountAt('/projects')
    const hrefs = wrapper.findAll('a.workspace-nav-item').map((node) => node.attributes('href'))
    expect(hrefs).toContain('/templates')
    expect(hrefs.some((href) => String(href).includes('#'))).toBe(false)
    wrapper.unmount()
  })

  it('左侧栏「AI 生成」进入独立创作页', async () => {
    const wrapper = await mountAt('/projects')
    expect(wrapper.find('.ai-page').exists()).toBe(false)

    const aiButton = wrapper.find('.workspace-nav-item.is-ai')
    expect(aiButton.exists()).toBe(true)
    await aiButton.trigger('click')
    await flushPromises()

    expect(wrapper.find('.ai-page').exists()).toBe(true)
    expect(wrapper.text()).toContain('开始 AI 活动创作')

    wrapper.unmount()
  })

  it('场景卡片会把示例需求填入大输入框', async () => {
    const wrapper = await mountAt('/ai')
    const scenes = wrapper.findAll('.ai-scene-grid button')
    expect(scenes.length).toBe(4)

    await scenes[0]!.trigger('click')
    expect((wrapper.find('.ai-composer textarea').element as HTMLTextAreaElement).value).toContain('摄影社')

    wrapper.unmount()
  })
})
