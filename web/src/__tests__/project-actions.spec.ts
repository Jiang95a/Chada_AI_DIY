import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'
import { appRoutes } from '@/router'
import { useProjectStore } from '@/stores/projects'

async function mountProjects() {
  const router = createRouter({ history: createMemoryHistory(), routes: appRoutes })
  await router.push('/projects')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 0))
  await flushPromises()
  return wrapper
}

describe('项目卡片操作', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('diy-campus:access-token', 'test-token')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('三个点会打开项目操作菜单', async () => {
    const wrapper = await mountProjects()

    expect(wrapper.find('.project-context-menu').exists()).toBe(false)
    await wrapper.find('.project-more').trigger('click')

    expect(wrapper.find('.project-context-menu').exists()).toBe(true)
    expect(wrapper.find('.project-context-menu').text()).toContain('删除项目')

    wrapper.unmount()
  })

  it('可以复制并删除本地演示项目', async () => {
    const wrapper = await mountProjects()
    const initialCount = wrapper.findAll('.project-card').length

    await wrapper.find('.project-more').trigger('click')
    await wrapper.find('.project-context-menu button').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.project-card').length).toBe(initialCount + 1)

    await wrapper.find('.project-more').trigger('click')
    const dangerButton = wrapper.find('.project-context-menu button.is-danger')
    await dangerButton.trigger('click')
    await flushPromises()

    const confirmButton = document.body.querySelector('.delete-project-button') as HTMLButtonElement | null
    expect(confirmButton).not.toBeNull()
    confirmButton?.click()
    await flushPromises()

    expect(wrapper.findAll('.project-card').length).toBe(initialCount)
    wrapper.unmount()
  })

  it('只有断网时才创建本地离线项目', async () => {
    setActivePinia(createPinia())
    const store = useProjectStore()
    const project = await store.createProjectRemote({ title: '离线测试活动', type: '讲座' })

    expect(project.id.startsWith('prj_')).toBe(true)
    expect(store.remoteError).toContain('本地离线草稿')
  })

  it('服务端错误不会伪装成本地创建成功', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: { code: 'INTERNAL_ERROR', message: '服务端创建失败' } }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      })
    )
    setActivePinia(createPinia())
    const store = useProjectStore()
    const initialCount = store.projects.length

    await expect(store.createProjectRemote({ title: '服务端错误测试', type: '讲座' })).rejects.toThrow('服务端创建失败')
    expect(store.projects.length).toBe(initialCount)
  })
})
