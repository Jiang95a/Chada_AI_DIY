import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'
import { appRoutes } from '@/router'

const cases = [
  { path: '/', selector: '.landing-page', text: '让每一份心事' },
  { path: '/projects', selector: '.studio-dashboard', text: '心晴运营台' },
  { path: '/projects/new', selector: '.studio-create', text: '制作一个心晴活动页面' },
  { path: '/published', selector: '.published-page', text: '参与数据' },
  { path: '/templates', selector: '.templates-page', text: '心晴模板' },
  { path: '/ai', selector: '.ai-page', text: '让 AI 一起构思心晴活动' },
  { path: '/campus', selector: '.campus-page', text: '发现一场' },
  { path: '/resources', selector: '.resources-page', text: '不知道从哪里开始时' },
  { path: '/tools', selector: '.tools-page', text: '心晴工具箱' },
  { path: '/about', selector: '.about-page', text: '关于心屿' },
  { path: '/profile', selector: '.account-page', text: '个人资料' },
  { path: '/settings', selector: '.settings-page', text: '偏好设置' },
  { path: '/help', selector: '.help-page', text: '帮助与反馈' },
  { path: '/editor/demo', selector: '.studio-editor', text: '质量检查' },
  // 测试环境无法连接 8080，按“断网才本地兜底”的规则回退到编辑器演示文档。
  // 后端返回 404/未发布时不会再走这个回退。
  { path: '/p/heart-garden', selector: '.studio-activity', text: '和愿意倾听的人' },
]

describe('Vue 前端路由冒烟测试', () => {
  it.each(cases)('$path 可以渲染', async ({ path, selector, text }) => {
    localStorage.clear()
    localStorage.setItem('diy-campus:access-token', 'test-token')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    const router = createRouter({
      history: createMemoryHistory(),
      routes: appRoutes,
    })
    await router.push(path)
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()
    expect(wrapper.find(selector).exists()).toBe(true)
    expect(wrapper.text()).toContain(text)
    wrapper.unmount()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })
})
