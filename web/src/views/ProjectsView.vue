<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { useProjectStore, type ProjectRecord, type ProjectStatus } from '@/stores/projects'

const projectStore = useProjectStore()
const route = useRoute()
const router = useRouter()
const toast = useToast()
const statusFilter = ref<'all' | 'published' | 'draft'>('all')
const sortMode = ref<'updated' | 'created' | 'title'>('updated')
const openMenuId = ref('')
const actionProjectId = ref('')
const deleteTarget = ref<ProjectRecord | null>(null)

onMounted(() => {
  document.addEventListener('click', closeProjectMenu)
  void projectStore.loadProjects()
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeProjectMenu)
})

const statusLabels: Record<ProjectStatus, string> = {
  draft: '草稿',
  published: '已发布',
  unpublished: '已下线',
  archived: '已归档',
}

const statusTones: Record<ProjectStatus, string> = {
  draft: 'draft',
  published: 'published',
  unpublished: 'offline',
  archived: 'offline',
}

const projects = computed(() =>
  projectStore.projects.map((project) => ({
    ...project,
    title: project.document.title,
    statusLabel: statusLabels[project.status],
    statusTone: statusTones[project.status],
    submissions: project.submissionCount
      ? `${project.submissionCount} 人参与`
      : project.status === 'published'
        ? '暂无提交'
        : '尚未发布',
    updatedAt: formatProjectDate(project.document.updatedAt),
    slug: project.document.slug,
  }))
)

const stats = computed(() => [
  {
    label: '全部心晴活动',
    value: String(projectStore.projectCount),
    hint: `${projectStore.draftCount} 个正在编辑`,
    icon: 'layers',
    tone: 'blue',
  },
  {
    label: '已发布活动',
    value: String(projectStore.publishedCount),
    hint: '正在开放参与',
    icon: 'eye',
    tone: 'green',
  },
  {
    label: '累计参与',
    value: String(projectStore.projects.reduce((sum, project) => sum + project.submissionCount, 0)),
    hint: '全部活动合计',
    icon: 'users',
    tone: 'violet',
  },
  {
    label: '本周新增',
    value: String(
      projectStore.projects.filter((project) => Date.now() - project.document.createdAt < 7 * 86400000).length
    ),
    hint: '最近 7 天创建',
    icon: 'chart',
    tone: 'amber',
  },
])

const rawSearchQuery = computed(() => {
  const value = route.query.q
  return (Array.isArray(value) ? (value[0] ?? '') : (value ?? '')).trim()
})
const searchQuery = computed(() => rawSearchQuery.value.toLowerCase())

const filteredProjects = computed(() => {
  const items = projects.value.filter((project) => {
    const statusMatched =
      statusFilter.value === 'all' ||
      (statusFilter.value === 'published' && project.status === 'published') ||
      (statusFilter.value === 'draft' && project.status === 'draft')
    if (!statusMatched) return false
    if (!searchQuery.value) return true
    const searchable = `${project.title} ${project.type} ${project.statusLabel} ${project.submissions}`.toLowerCase()
    return searchable.includes(searchQuery.value)
  })
  if (sortMode.value === 'title') {
    return [...items].sort((a, b) => a.title.localeCompare(b.title, 'zh-CN'))
  }
  if (sortMode.value === 'created') {
    // 后端 GET /projects?sort=created_at 已经支持按创建时间倒序；
    // 这里做本地二次排序，保证离线/演示模式下行为一致。
    return [...items].sort((a, b) => (b.document.createdAt ?? 0) - (a.document.createdAt ?? 0))
  }
  return items
})

const statusCounts = computed(() => ({
  all: projects.value.length,
  published: projects.value.filter((project) => project.status === 'published').length,
  draft: projects.value.filter((project) => project.status === 'draft').length,
}))

function formatProjectDate(timestamp: number): string {
  const diff = Date.now() - timestamp
  if (diff < 60 * 60 * 1000) return '刚刚更新'
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)} 小时前更新`
  if (diff < 7 * 24 * 60 * 60 * 1000) return `${Math.floor(diff / 86400000)} 天前更新`
  return new Date(timestamp).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function toggleSort(): void {
  // 三态循环：最近更新 → 最近创建 → 名称排序 → 最近更新
  sortMode.value = sortMode.value === 'updated' ? 'created' : sortMode.value === 'created' ? 'title' : 'updated'
}

function clearSearch(): void {
  statusFilter.value = 'all'
  void router.replace({ path: '/projects' })
}

function toggleProjectMenu(projectId: string): void {
  openMenuId.value = openMenuId.value === projectId ? '' : projectId
}

function closeProjectMenu(): void {
  openMenuId.value = ''
}

async function duplicate(project: ProjectRecord): Promise<void> {
  if (actionProjectId.value) return
  actionProjectId.value = project.id
  closeProjectMenu()
  try {
    await projectStore.duplicateProjectRemote(project.id)
    toast.pushToast('项目副本已创建', 'success')
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '复制项目失败', 'error')
  } finally {
    actionProjectId.value = ''
  }
}

function askDelete(project: ProjectRecord): void {
  closeProjectMenu()
  deleteTarget.value = project
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value || actionProjectId.value) return
  actionProjectId.value = deleteTarget.value.id
  try {
    await projectStore.removeProjectRemote(deleteTarget.value.id)
    toast.pushToast('项目已删除', 'success')
    deleteTarget.value = null
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '删除项目失败', 'error')
  } finally {
    actionProjectId.value = ''
  }
}
</script>

<template>
  <WorkspaceShell>
    <main class="dashboard-page studio-dashboard">
      <section class="dashboard-heading">
        <div>
          <p class="eyebrow">Wellbeing operations</p>
          <h1>心晴运营台</h1>
          <p>组织心理社团活动，查看参与状态，用更清晰的方式陪伴同学。</p>
        </div>
        <RouterLink class="app-button app-button--primary" to="/projects/new">
          <Icon name="plus" :size="16" />
          发起心晴活动
        </RouterLink>
      </section>

      <section class="stats-grid" id="overview">
        <article v-for="stat in stats" :key="stat.label" class="stat-card">
          <span class="stat-icon" :class="`stat-icon--${stat.tone}`"><Icon :name="stat.icon" :size="17" /></span>
          <div>
            <span class="stat-label">{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
            <small>{{ stat.hint }}</small>
          </div>
        </article>
      </section>

      <section class="projects-section">
        <div class="projects-toolbar">
          <div class="project-view-tabs">
            <button :class="{ 'is-active': statusFilter === 'all' }" type="button" @click="statusFilter = 'all'">
              全部项目 <span>{{ statusCounts.all }}</span>
            </button>
            <button
              :class="{ 'is-active': statusFilter === 'published' }"
              type="button"
              @click="statusFilter = 'published'"
            >
              已发布 <span>{{ statusCounts.published }}</span>
            </button>
            <button :class="{ 'is-active': statusFilter === 'draft' }" type="button" @click="statusFilter = 'draft'">
              草稿 <span>{{ statusCounts.draft }}</span>
            </button>
          </div>
          <div class="project-toolbar-actions">
            <button class="project-filter" type="button" @click="toggleSort">
              {{ sortMode === 'updated' ? '最近更新' : sortMode === 'created' ? '最近创建' : '名称排序' }}
              <Icon name="chevron" :size="13" />
            </button>
          </div>
        </div>

        <div v-if="projectStore.remoteError" class="project-sync-warning">
          <Icon name="cloud" :size="15" />
          <span>{{ projectStore.remoteError }}</span>
          <button type="button" @click="projectStore.loadProjects">重新同步</button>
        </div>

        <div v-if="searchQuery" class="project-search-summary">
          <span>搜索“{{ rawSearchQuery }}”，找到 {{ filteredProjects.length }} 个项目</span>
          <button type="button" @click="clearSearch">清除搜索</button>
        </div>

        <div v-if="projectStore.remoteLoading && projectStore.projects.length === 0" class="project-empty-state">
              <span><Icon name="layers" :size="22" /></span>
              <strong>正在加载心晴活动</strong>
              <p>正在同步运营数据，请稍候。</p>
        </div>

        <div v-else class="project-grid">
          <article
            v-for="project in filteredProjects"
            :key="project.id"
            class="project-card"
            :class="{ 'has-open-menu': openMenuId === project.id }"
          >
            <div class="project-cover" :class="`project-cover--${project.coverTone}`">
              <div class="project-cover-pattern" />
              <span class="project-cover-code">{{ project.coverCode }}</span>
              <strong>{{ project.coverTitle }}</strong>
              <span class="project-cover-dot" />
            </div>
            <div class="project-card-body">
              <div class="project-card-head">
                <div>
                  <span class="project-type">{{ project.type }}</span>
                  <h2>{{ project.title }}</h2>
                </div>
                <div class="project-more-wrap" @click.stop>
                  <button
                    class="project-more"
                    :class="{ 'is-open': openMenuId === project.id }"
                    type="button"
                    aria-label="更多操作"
                    :aria-expanded="openMenuId === project.id"
                    @click.stop="toggleProjectMenu(project.id)"
                  >
                    <Icon name="more" :size="17" />
                  </button>
                  <div v-if="openMenuId === project.id" class="project-context-menu" @click.stop>
                    <RouterLink :to="`/editor/${project.id}`" @click="closeProjectMenu">
                      <Icon name="layout" :size="14" />继续编辑
                    </RouterLink>
                    <RouterLink :to="`/p/${project.slug}`" target="_blank" @click="closeProjectMenu">
                      <Icon name="eye" :size="14" />预览页面
                    </RouterLink>
                    <RouterLink :to="`/projects/${project.id}/analytics`" @click="closeProjectMenu">
                      <Icon name="chart" :size="14" />查看数据
                    </RouterLink>
                    <button type="button" :disabled="actionProjectId === project.id" @click="duplicate(project)">
                      <Icon name="copy" :size="14" />{{ actionProjectId === project.id ? '处理中…' : '创建副本' }}
                    </button>
                    <div class="project-context-divider" />
                    <button class="is-danger" type="button" @click="askDelete(project)">
                      <Icon name="trash" :size="14" />删除项目
                    </button>
                  </div>
                </div>
              </div>
              <div class="project-meta-row">
                <span class="status-pill" :class="`status-pill--${project.statusTone}`">
                  <span />{{ project.statusLabel }}
                </span>
                <span>{{ project.submissions }}</span>
              </div>
              <div class="project-card-footer">
                <span>{{ project.updatedAt }}</span>
                <div class="project-card-actions">
                  <RouterLink :to="`/projects/${project.id}/analytics`">
                    <Icon name="chart" :size="14" />数据
                  </RouterLink>
                  <RouterLink :to="`/p/${project.slug}`" target="_blank"><Icon name="eye" :size="14" />预览</RouterLink>
                  <RouterLink class="project-edit-link" :to="`/editor/${project.id}`">
                    编辑<Icon name="arrow-right" :size="14" />
                  </RouterLink>
                </div>
              </div>
            </div>
          </article>

          <div v-if="filteredProjects.length === 0" class="project-empty-state">
            <span><Icon :name="projectStore.projects.length === 0 ? 'file' : 'search'" :size="22" /></span>
            <strong>{{ projectStore.projects.length === 0 ? '还没有心晴活动' : '没有找到匹配的活动' }}</strong>
            <p>
              {{
                projectStore.projects.length === 0
                  ? '从心晴模板、AI 或海报开始制作第一个心理活动页面。'
                  : '尝试换一个关键词，或者清除当前搜索与筛选条件。'
              }}
            </p>
            <RouterLink
              v-if="projectStore.projects.length === 0"
              class="app-button app-button--primary"
              to="/projects/new"
            >
              <Icon name="plus" :size="14" />
              发起第一个心晴活动
            </RouterLink>
            <button v-else class="app-button app-button--secondary" type="button" @click="clearSearch">清除搜索</button>
          </div>

          <RouterLink v-if="projectStore.projects.length > 0" class="project-create-card" to="/projects/new">
            <span class="project-create-icon"><Icon name="plus" :size="21" /></span>
            <strong>创建一场新的心晴活动</strong>
            <p>从心理社团模板开始，或者让 AI 根据需求生成。</p>
            <span>开始创建 <Icon name="arrow-right" :size="14" /></span>
          </RouterLink>
        </div>
      </section>
    </main>

    <BaseModal
      :open="Boolean(deleteTarget)"
      title="删除项目"
      subtitle="删除后项目将从工作台移除，此操作不能直接撤销。"
      :width="430"
      @close="deleteTarget = null"
    >
      <div class="delete-project-confirm">
        <span><Icon name="alert" :size="20" /></span>
        <div>
          <strong>确定删除「{{ deleteTarget?.document.title }}」？</strong>
          <p>项目对应的报名数据和公开链接将无法继续访问，请确认不再需要后再删除。</p>
        </div>
      </div>
      <template #footer>
        <button class="app-button app-button--secondary" type="button" @click="deleteTarget = null">取消</button>
        <button class="delete-project-button" type="button" :disabled="Boolean(actionProjectId)" @click="confirmDelete">
          <Icon name="trash" :size="14" />{{ actionProjectId ? '正在删除…' : '确认删除' }}
        </button>
      </template>
    </BaseModal>
  </WorkspaceShell>
</template>

<style scoped>
.project-card.has-open-menu {
  z-index: 12;
  overflow: visible;
}

.project-sync-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 9px 11px;
  border: 1px solid #fdecc8;
  border-radius: 10px;
  color: #92400e;
  background: #fffaeb;
  font-size: 10px;
}

.project-sync-warning span {
  flex: 1;
}

.project-sync-warning button {
  padding: 3px 7px;
  border: 1px solid #f5d383;
  border-radius: 6px;
  color: #92400e;
  background: #fff;
  font-size: 9px;
  cursor: pointer;
}

.project-card.has-open-menu .project-cover {
  border-radius: var(--radius-md, 12px) var(--radius-md, 12px) 0 0;
}

.project-more-wrap {
  position: relative;
  flex: 0 0 auto;
}

.project-more.is-open {
  color: var(--brand-600, #4f46e5);
  background: var(--brand-50, #eef2ff);
}

.project-context-menu {
  position: absolute;
  z-index: 30;
  top: 36px;
  right: 0;
  display: grid;
  width: 158px;
  padding: 5px;
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 11px;
  background: #fff;
  box-shadow: 0 16px 38px -16px rgb(15 23 42 / 28%);
}

.project-context-menu > a,
.project-context-menu > button {
  display: flex;
  width: 100%;
  min-height: 34px;
  align-items: center;
  gap: 8px;
  padding: 0 9px;
  border: 0;
  border-radius: 7px;
  color: var(--ink-600, #475467);
  background: transparent;
  font-size: 10px;
  text-align: left;
  cursor: pointer;
}

.project-context-menu > a:hover,
.project-context-menu > button:hover:not(:disabled) {
  color: var(--ink-900, #0f172a);
  background: var(--ink-50, #f8fafc);
}

.project-context-menu > button:disabled {
  opacity: 0.55;
  cursor: wait;
}

.project-context-menu > button.is-danger {
  color: #d92d20;
}

.project-context-menu > button.is-danger:hover {
  color: #b42318;
  background: var(--rose-50, #fef2f2);
}

.project-context-divider {
  height: 1px;
  margin: 4px;
  background: var(--line, #e9ecf3);
}

.delete-project-confirm {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.delete-project-confirm > span {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border-radius: 12px;
  color: #d92d20;
  background: var(--rose-50, #fef2f2);
  place-items: center;
}

.delete-project-confirm strong {
  color: var(--ink-900, #101828);
  font-size: 12px;
}

.delete-project-confirm p {
  margin: 6px 0 0;
  color: var(--ink-500, #667085);
  font-size: 11px;
  line-height: 1.7;
}

.delete-project-button {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 6px;
  padding: 0 13px;
  border: 0;
  border-radius: 9px;
  color: #fff;
  background: #d92d20;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.delete-project-button:hover:not(:disabled) {
  background: #b42318;
}

.delete-project-button:disabled {
  opacity: 0.55;
  cursor: wait;
}
</style>
