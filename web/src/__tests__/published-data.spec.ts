import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'
import { appRoutes } from '@/router'

async function mountPublished() {
  localStorage.clear()
  localStorage.setItem('diy-campus:access-token', 'test-token')
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  const router = createRouter({ history: createMemoryHistory(), routes: appRoutes })
  await router.push('/published')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 0))
  await flushPromises()
  return wrapper
}

describe('参与数据总览', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('列出已发布应用并支持切换当前应用', async () => {
    const wrapper = await mountPublished()
    const apps = wrapper.findAll('.published-app-list > button')

    expect(apps.length).toBe(2)
    expect(wrapper.find('.summary-primary').text()).toContain('364')
    const care = apps.find((app) => app.text().includes('21 天自我关怀打卡'))
    await care!.trigger('click')
    expect(wrapper.find('.selected-app-head').text()).toContain('21 天自我关怀打卡')

    wrapper.unmount()
  })
})
