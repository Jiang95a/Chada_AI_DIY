import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'
import { appRoutes } from '@/router'

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

describe('工作台外框入口', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('diy-campus:access-token', 'test-token')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('管理工作空间会进入空间设置', async () => {
    const wrapper = await mountAt('/projects')
    await wrapper.find('.workspace-org-card').trigger('click')
    const manage = wrapper.find('.workspace-org-menu .workspace-menu-footer')

    expect(manage.attributes('href')).toBe('/settings#workspace')
    expect(manage.text()).toContain('管理工作空间')
    wrapper.unmount()
  })

  it('通知支持全部标为已读', async () => {
    const wrapper = await mountAt('/projects')
    expect(wrapper.find('.workspace-notification-dot').exists()).toBe(true)

    await wrapper.find('.workspace-notification-button').trigger('click')
    expect(wrapper.text()).toContain('2 条未读')
    await wrapper.find('.workspace-notification-menu .workspace-menu-footer').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('0 条未读')
    expect(wrapper.find('.workspace-notification-dot').exists()).toBe(false)
    wrapper.unmount()

    const reloaded = await mountAt('/projects')
    expect(reloaded.find('.workspace-notification-dot').exists()).toBe(false)
    reloaded.unmount()
  })

  it('心理活动广场无需登录也能浏览', async () => {
    localStorage.clear()
    const wrapper = await mountAt('/campus')

    expect(wrapper.find('.campus-page').exists()).toBe(true)
    expect(wrapper.find('.public-shell').exists()).toBe(true)
    wrapper.unmount()
  })

  it('心晴工具箱无需登录也能使用', async () => {
    localStorage.clear()
    const wrapper = await mountAt('/tools')

    expect(wrapper.find('.tools-page').exists()).toBe(true)
    expect(wrapper.find('.public-shell').exists()).toBe(true)
    await wrapper.find('.grounding-card > button').trigger('click')
    expect(wrapper.find('.grounding-count').text()).toContain('02 / 05')
    wrapper.unmount()
  })

  it('关于心屿无需登录也能访问', async () => {
    localStorage.clear()
    const wrapper = await mountAt('/about')

    expect(wrapper.find('.about-page').exists()).toBe(true)
    expect(wrapper.find('.public-shell').exists()).toBe(true)
    wrapper.unmount()
  })
})
