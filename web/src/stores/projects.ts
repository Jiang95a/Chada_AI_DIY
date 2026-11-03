import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import type { Doc } from '@/types/editor'
import { createBlankDoc, createDemoDoc, createDocFromTemplate } from '@/data/templates'
import { normalizeDoc } from '@/utils/doc'
import { slugify, uid } from '@/utils/misc'
import {
  createProject as createProjectApi,
  duplicateProject as duplicateProjectApi,
  getProject as getProjectApi,
  listProjects as listProjectsApi,
  removeProject as removeProjectApi,
  type ProjectApiRecord,
} from '@/services/projects'
import { putDraft } from '@/services/draft'
import { ApiError } from '@/services/http'
import { loadTemplateDocument } from '@/services/templates'

export type ProjectStatus = 'draft' | 'published' | 'unpublished' | 'archived'

export interface ProjectRecord {
  id: string
  type: string
  status: ProjectStatus
  coverTone: string
  coverCode: string
  coverTitle: string
  submissionCount: number
  document: Doc
  draftRevision?: number
  publicSlug?: string
  publicUrl?: string
}

interface CreateProjectInput {
  title: string
  type?: string
  org?: string
  templateId?: string
  document?: Doc
}

const STORAGE_KEY = 'diy-campus:projects:v1'
const REMOTE_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const LEGACY_DEMO_IDS = new Set(['prj-photo-club', 'prj-singing-vote', 'prj-reading-21', 'prj-volunteer-signup'])
const LEGACY_DEMO_TITLE_PATTERN =
  /光影摄影社|校园歌手大赛人气投票|21 天阅读打卡计划|迎新志愿者信息收集|第四届校园荧光夜跑|校学生会 2026 秋季招新/

function cloneDocument(document: Doc): Doc {
  return JSON.parse(JSON.stringify(document)) as Doc
}

function makeSeedProject(
  id: string,
  templateId: string,
  options: Omit<ProjectRecord, 'id' | 'document'> & {
    title: string
    slug: string
    org: string
  }
): ProjectRecord {
  const base = templateId === 'demo' ? createDemoDoc() : createDocFromTemplate(templateId, createDemoDoc())
  const now = Date.now()
  base.id = id
  base.title = options.title
  base.slug = options.slug
  base.meta = { ...base.meta, org: options.org }
  base.createdAt = now - 7 * 24 * 60 * 60 * 1000
  base.updatedAt = now - 2 * 60 * 60 * 1000
  base.published =
    options.status === 'published'
      ? {
          url: `${window.location.origin}/p/${options.slug}`,
          slug: options.slug,
          at: now - 24 * 60 * 60 * 1000,
        }
      : null
  return {
    id,
    type: options.type,
    status: options.status,
    coverTone: options.coverTone,
    coverCode: options.coverCode,
    coverTitle: options.coverTitle,
    submissionCount: options.submissionCount,
    document: base,
  }
}

function createSeedProjects(): ProjectRecord[] {
  return [
    makeSeedProject('prj-heart-garden', 'recruit', {
      title: '考试周心晴游园会',
      slug: 'heart-garden',
      org: '心屿心理协会',
      type: '心晴游园会',
      status: 'published',
      coverTone: 'read',
      coverCode: 'WELLBEING',
      coverTitle: '给忙碌的心\n留一小块空地',
      submissionCount: 126,
    }),
    makeSeedProject('prj-self-care-21', 'checkin', {
      title: '21 天自我关怀打卡',
      slug: 'self-care-21',
      org: '心晴志愿队',
      type: '自我关怀',
      status: 'published',
      coverTone: 'photo',
      coverCode: 'SELF CARE',
      coverTitle: '把照顾自己\n变成一件小事',
      submissionCount: 238,
    }),
    makeSeedProject('prj-mindful-club', 'recruit', {
      title: '心屿心理协会秋季招新',
      slug: 'mindful-club',
      org: '心屿心理协会',
      type: '心理社团',
      status: 'draft',
      coverTone: 'volunteer',
      coverCode: 'JOIN XINYU',
      coverTitle: '一起做有温度\n的校园心理活动',
      submissionCount: 0,
    }),
    makeSeedProject('prj-anonymous-box', 'signup', {
      title: '匿名心事提问箱',
      slug: 'anonymous-box',
      org: '心屿心理协会',
      type: '安心表达',
      status: 'unpublished',
      coverTone: 'music',
      coverCode: 'SAFE SPACE',
      coverTitle: '把想说的话\n轻轻放在这里',
      submissionCount: 0,
    }),
  ]
}

function restoreProjects(): ProjectRecord[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return createSeedProjects()
    const parsed = JSON.parse(raw) as Array<Partial<ProjectRecord>>
    if (!Array.isArray(parsed)) return createSeedProjects()
    const restored = parsed
      .map((item, index) => {
        if (typeof item.id === 'string' && LEGACY_DEMO_IDS.has(item.id)) return null
        const document = normalizeDoc(item.document)
        if (!document) return null
        if (LEGACY_DEMO_TITLE_PATTERN.test(document.title)) return null
        return {
          id: typeof item.id === 'string' ? item.id : `project-${index + 1}`,
          type: typeof item.type === 'string' ? item.type : '心理活动',
          status: (item.status ?? 'draft') as ProjectStatus,
          coverTone: typeof item.coverTone === 'string' ? item.coverTone : 'photo',
          coverCode: typeof item.coverCode === 'string' ? item.coverCode : 'ACTIVITY',
          coverTitle: typeof item.coverTitle === 'string' ? item.coverTitle : document.title,
          submissionCount: Number(item.submissionCount) || 0,
          document,
        } satisfies ProjectRecord
      })
      .filter((item): item is ProjectRecord => Boolean(item))
    return restored.length ? restored : createSeedProjects()
  } catch {
    return createSeedProjects()
  }
}

export const useProjectStore = defineStore('projects', () => {
  const projects = ref<ProjectRecord[]>(restoreProjects())
  const remoteLoading = ref(false)
  const remoteError = ref('')

  watch(
    projects,
    (value) => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
      } catch {
        /* localStorage 容量不足时不阻断编辑 */
      }
    },
    { deep: true }
  )

  const projectCount = computed(() => projects.value.length)
  const publishedCount = computed(() => projects.value.filter((project) => project.status === 'published').length)
  const draftCount = computed(() => projects.value.filter((project) => project.status === 'draft').length)

  function getById(id: string): ProjectRecord | undefined {
    return projects.value.find((project) => project.id === id)
  }

  function getBySlug(slug: string): ProjectRecord | undefined {
    return projects.value.find((project) => project.document.slug === slug)
  }

  function fromApi(project: ProjectApiRecord): ProjectRecord {
    return {
      id: project.id,
      type: project.activityType,
      status: project.status as ProjectStatus,
      coverTone: project.coverTone,
      coverCode: project.coverCode,
      coverTitle: project.coverTitle,
      submissionCount: project.submissionCount,
      document: project.draftDocument,
      draftRevision: project.draftRevision,
      publicSlug: project.publicSlug,
      publicUrl: project.publicUrl,
    }
  }

  async function loadProjects(): Promise<void> {
    remoteLoading.value = true
    remoteError.value = ''
    try {
      const rows = await listProjectsApi({ page: 1, pageSize: 100, sort: 'updated_at' })
      // 后端成功返回时以后端数据为准；空数组也是有效结果，不能再回填本地示例项目。
      projects.value = rows.filter((project) => !LEGACY_DEMO_TITLE_PATTERN.test(project.title)).map(fromApi)
    } catch (error) {
      remoteError.value = error instanceof Error ? error.message : '项目列表加载失败'
    } finally {
      remoteLoading.value = false
    }
  }

  async function refreshProject(projectId: string): Promise<ProjectRecord | undefined> {
    try {
      const remote = fromApi(await getProjectApi(projectId))
      const index = projects.value.findIndex((project) => project.id === projectId)
      if (index >= 0) projects.value[index] = remote
      else projects.value.unshift(remote)
      return remote
    } catch (error) {
      remoteError.value = error instanceof Error ? error.message : '项目加载失败'
      return getById(projectId)
    }
  }

  function uniqueSlug(title: string): string {
    const base = slugify(title) || 'campus-activity'
    let slug = base
    let index = 2
    while (projects.value.some((project) => project.document.slug === slug)) {
      slug = `${base}-${index}`
      index += 1
    }
    return slug
  }

  function createProject(input: CreateProjectInput): ProjectRecord {
    const title = input.title.trim() || '未命名心晴活动'
    let document: Doc
    if (input.document) {
      document = normalizeDoc(cloneDocument(input.document)) ?? createBlankDoc()
    } else if (input.templateId) {
      document = createDocFromTemplate(input.templateId, createDemoDoc())
    } else {
      document = createBlankDoc()
    }
    const now = Date.now()
    const id = uid('prj')
    document.id = id
    document.title = title.slice(0, 40)
    document.slug = uniqueSlug(title)
    document.meta = {
      ...document.meta,
      org: input.org?.trim() || document.meta.org,
    }
    document.createdAt = now
    document.updatedAt = now
    document.published = null

    const project: ProjectRecord = {
      id,
      type: input.type?.trim() || '心理活动',
      status: 'draft',
      coverTone: 'photo',
      coverCode: 'NEW / DRAFT',
      coverTitle: title,
      submissionCount: 0,
      document,
    }
    projects.value.unshift(project)
    return project
  }

  async function createProjectRemote(input: CreateProjectInput): Promise<ProjectRecord> {
    let initialDocument: Doc | undefined
    try {
      const title = input.title.trim() || '未命名心晴活动'
      const remoteTemplate =
        Boolean(input.templateId) &&
        /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(input.templateId ?? '')
      if (input.document) {
        initialDocument = cloneDocument(input.document)
      } else if (input.templateId && !remoteTemplate) {
        initialDocument = input.templateId.startsWith('custom-')
          ? await loadTemplateDocument(input.templateId)
          : createDocFromTemplate(input.templateId, createDemoDoc())
      } else if (!input.templateId) {
        initialDocument = createBlankDoc()
      }
      if (initialDocument) {
        initialDocument.title = title.slice(0, 40)
        initialDocument.slug = uniqueSlug(title)
        initialDocument.meta = {
          ...initialDocument.meta,
          org: input.org?.trim() || initialDocument.meta.org,
        }
        initialDocument.published = null
      }
      const remote = fromApi(
        await createProjectApi({
          title,
          activityType: input.type?.trim() || '心理活动',
          templateId: remoteTemplate ? input.templateId : undefined,
          initialDocument,
          coverTone: 'photo',
          coverCode: 'NEW / DRAFT',
          coverTitle: input.title.trim() || '未命名心晴活动',
        })
      )
      projects.value.unshift(remote)
      return remote
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 0) {
        remoteError.value = error instanceof Error ? error.message : '项目创建失败'
        throw error
      }
      remoteError.value = '后端暂时无法连接，项目已保存为本地离线草稿'
      return createProject({
        ...input,
        document: initialDocument ?? input.document,
      })
    }
  }

  function saveDocument(projectId: string, document: Doc): void {
    const project = getById(projectId)
    if (!project) return
    project.document = cloneDocument(document)
  }

  /**
   * 把文档保存到后端草稿。
   *
   * 老版本这里只有上面的 {@link saveDocument}（纯内存赋值），名字叫「保存」
   * 但不发任何请求，谁在它后面依赖「已经存上了」就会丢数据。
   * 现在编辑器之外的入口都走这个函数：写内存 + PUT /projects/{id}/draft。
   *
   * revision 冲突（409）不当作错误抛出，而是返回 conflict=true，
   * 让调用方决定是提示用户刷新还是覆盖，别把用户的编辑直接丢掉。
   */
  async function saveRemoteDraft(
    projectId: string,
    document: Doc
  ): Promise<{ ok: boolean; revision: number; conflict: boolean }> {
    const project = getById(projectId)
    const snapshot = cloneDocument(document)
    // 先写内存，保证界面上立刻看到，不依赖网络
    saveDocument(projectId, snapshot)
    try {
      const saved = await putDraft(projectId, project?.draftRevision ?? 0, snapshot)
      if (project) project.draftRevision = saved.revision
      return { ok: true, revision: saved.revision, conflict: false }
    } catch (error) {
      const status = (error as { status?: number })?.status
      return {
        ok: false,
        revision: project?.draftRevision ?? 0,
        conflict: status === 409,
      }
    }
  }

  function updateProject(projectId: string, patch: Partial<Pick<ProjectRecord, 'type' | 'status'>>): void {
    const project = getById(projectId)
    if (!project) return
    Object.assign(project, patch)
  }

  function publishProject(projectId: string, url: string, slug: string): void {
    const project = getById(projectId)
    if (!project) return
    project.status = 'published'
    project.document.slug = slug
    project.document.published = { url, slug, at: Date.now() }
    project.document.updatedAt = Date.now()
  }

  function unpublishProject(projectId: string): void {
    const project = getById(projectId)
    if (!project) return
    project.status = 'unpublished'
    project.document.published = null
    project.document.updatedAt = Date.now()
  }

  function duplicateProject(projectId: string): ProjectRecord | undefined {
    const source = getById(projectId)
    if (!source) return
    const title = `${source.document.title}（副本）`
    const copy = createProject({
      title,
      type: source.type,
      document: source.document,
    })
    copy.coverTone = source.coverTone
    copy.coverCode = source.coverCode
    copy.coverTitle = source.coverTitle
    copy.document.published = null
    copy.status = 'draft'
    return copy
  }

  async function duplicateProjectRemote(projectId: string): Promise<ProjectRecord> {
    const source = getById(projectId)
    if (!source) throw new Error('要复制的项目不存在')
    if (!REMOTE_ID_PATTERN.test(projectId)) {
      const copy = duplicateProject(projectId)
      if (!copy) throw new Error('复制项目失败')
      return copy
    }
    try {
      const remote = fromApi(await duplicateProjectApi(projectId, `${source.document.title}（副本）`))
      projects.value.unshift(remote)
      return remote
    } catch (error) {
      remoteError.value = error instanceof Error ? error.message : '复制项目失败'
      throw error
    }
  }

  function removeProject(projectId: string): void {
    projects.value = projects.value.filter((project) => project.id !== projectId)
  }

  async function removeProjectRemote(projectId: string): Promise<void> {
    if (!REMOTE_ID_PATTERN.test(projectId)) {
      removeProject(projectId)
      return
    }
    try {
      await removeProjectApi(projectId)
      removeProject(projectId)
    } catch (error) {
      remoteError.value = error instanceof Error ? error.message : '删除项目失败'
      throw error
    }
  }

  return {
    projects,
    remoteLoading,
    remoteError,
    projectCount,
    publishedCount,
    draftCount,
    getById,
    getBySlug,
    createProject,
    createProjectRemote,
    loadProjects,
    refreshProject,
    saveDocument,
    saveRemoteDraft,
    updateProject,
    publishProject,
    unpublishProject,
    duplicateProject,
    duplicateProjectRemote,
    removeProject,
    removeProjectRemote,
  }
})
