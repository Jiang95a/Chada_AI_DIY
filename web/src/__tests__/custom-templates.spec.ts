import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createDemoDoc } from '@/data/templates'
import {
  listCustomTemplates,
  loadTemplateDocument,
  removeCustomTemplate,
  saveCustomTemplate,
} from '@/services/templates'
import { useProjectStore } from '@/stores/projects'

describe('我的模板', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('可以保存、读取、更新和删除自定义模板', async () => {
    const document = createDemoDoc()
    document.title = '我的招新模板'

    const saved = saveCustomTemplate({
      name: '我的招新模板',
      category: 'recruitment',
      description: '适合社团招新',
      document,
    })
    expect(saved.source).toBe('custom')
    expect(listCustomTemplates()).toHaveLength(1)

    const reused = await loadTemplateDocument(saved.id)
    expect(reused.title).toBe('我的招新模板')
    expect(reused.published).toBeNull()

    saveCustomTemplate({
      name: '我的招新模板',
      category: 'recruitment',
      description: '更新后的说明',
      document,
    })
    expect(listCustomTemplates()).toHaveLength(1)

    removeCustomTemplate(saved.id)
    expect(listCustomTemplates()).toHaveLength(0)
  })

  it('自定义模板可以在断网时创建本地活动', async () => {
    const document = createDemoDoc()
    document.title = '可复用招新活动'
    const template = saveCustomTemplate({
      name: '可复用招新活动',
      category: 'recruitment',
      document,
    })

    setActivePinia(createPinia())
    const store = useProjectStore()
    const project = await store.createProjectRemote({
      title: '新的招新活动',
      type: '社团招新',
      templateId: template.id,
    })

    expect(project.document.pages.length).toBe(document.pages.length)
    expect(project.document.pages.reduce((total, page) => total + page.blocks.length, 0)).toBe(
      document.pages.reduce((total, page) => total + page.blocks.length, 0)
    )
  })
})
