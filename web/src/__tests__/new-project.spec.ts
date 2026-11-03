import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'
import { appRoutes } from '@/router'
import { useProjectStore } from '@/stores/projects'

describe('新建活动', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('diy-campus:access-token', 'test-token')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('创建项目时使用用户选择的活动类型', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: appRoutes })
    await router.push('/projects/new')
    await router.isReady()
    const pinia = createPinia()
    const wrapper = mount(App, { global: { plugins: [pinia, router] } })

    await flushPromises()
    await wrapper.find('select').setValue('心理科普')
    await wrapper.find('.create-form-actions .app-button--primary').trigger('click')
    await flushPromises()

    const projectStore = useProjectStore(pinia)
    expect(projectStore.projects[0]?.type).toBe('心理科普')

    wrapper.unmount()
  })
})
