<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useProjectStore, type ProjectRecord } from '@/stores/projects'
import { useToast } from '@/composables/useToast'
import { getAnalyticsSummary, type AnalyticsSummary } from '@/services/analytics'
import { listSubmissions, type SubmissionRecord, type SubmissionStatus } from '@/services/submissions'
import { createExport, getExport, getExportDownloadUrl, listExports, type ExportJob } from '@/services/exports'

type DataTab = 'analytics' | 'submissions' | 'exports'

const route = useRoute()
const projectStore = useProjectStore()
const toast = useToast()

const project = ref<ProjectRecord | null>(null)
const projectLoading = ref(true)
const projectError = ref('')
const loading = ref(false)
const loadError = ref('')

const summary = ref<AnalyticsSummary | null>(null)
const submissions = ref<SubmissionRecord[]>([])
const submissionTotal = ref(0)
const submissionPage = ref(1)
const submissionPageSize = 20
const submissionStatus = ref<SubmissionStatus | ''>('')
const exports = ref<ExportJob[]>([])
const exporting = ref(false)
const downloadingId = ref('')
const refreshingExportId = ref('')

const projectId = computed(() => String(route.params.projectId ?? ''))
const activeTab = computed<DataTab>(() => {
  if (route.name === 'project-submissions') return 'submissions'
  if (route.name === 'project-exports') return 'exports'
  return 'analytics'
})
const projectPath = computed(() => `/projects/${encodeURIComponent(projectId.value)}`)
const editPath = computed(() => `/editor/${encodeURIComponent(projectId.value)}`)
const previewPath = computed(() => {
  if (!project.value) return ''
  const slug = project.value.publicSlug || project.value.document.published?.slug
  return slug ? `/p/${encodeURIComponent(slug)}` : ''
})
const tabs = computed(() => [
  {
    id: 'analytics' as const,
    label: '分析统计',
    hint: summary.value ? `${num(summary.value.pageViews)} 次浏览` : '浏览与转化',
    icon: 'chart',
    href: `${projectPath.value}/analytics`,
  },
  {
    id: 'submissions' as const,
    label: '参与记录',
    hint: submissionTotal.value ? `${submissionTotal.value} 条记录` : '查看原始数据',
    icon: 'users',
    href: `${projectPath.value}/submissions`,
  },
  {
    id: 'exports' as const,
    label: '数据导出',
    hint: exports.value.length ? `${exports.value.length} 个文件` : '生成 CSV',
    icon: 'download',
    href: `${projectPath.value}/exports`,
  },
])
const exportStorageKey = computed(() => `diy-campus:exports:${projectId.value}`)

// 后端返回的数字字段一律走一遍 num()。老版本后端在「还没发布」的项目上会给出
// null（SUM 空集合 / 字段缺失），直接插值会渲染成「undefined 次浏览」。
const num = (value: unknown): number => {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

const trend = computed(() =>
  (Array.isArray(summary.value?.byDay) ? summary.value.byDay : []).slice(-7).map((item) => ({
    ...item,
    date: String(item.date ?? ''),
    views: num(item.views),
    submissions: num(item.submissions),
    count: num(item.count),
  }))
)
const trendMax = computed(() =>
  Math.max(1, ...trend.value.flatMap((item) => [item.views, item.submissions]))
)
const eventDistribution = computed(() =>
  Object.entries(summary.value?.byEventType ?? {})
    .map(([type, count]) => ({ type, count: num(count), label: eventLabel(type) }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 6)
)
const eventMax = computed(() => Math.max(1, ...eventDistribution.value.map((item) => item.count)))
const conversionPercent = computed(() =>
  summary.value ? Math.round(num(summary.value.conversionRate) * 1000) / 10 : 0
)
const submissionTotalPages = computed(() => Math.max(1, Math.ceil(submissionTotal.value / submissionPageSize)))
const fieldLabels = computed(() => {
  const labels = new Map<string, string>()
  for (const page of project.value?.document.pages ?? []) {
    for (const block of page.blocks) {
      if (block.type === 'form' && Array.isArray(block.props.fields)) {
        for (const field of block.props.fields) {
          labels.set(`${block.id}:${String(field.id)}`, String(field.label || '未命名字段'))
        }
      }
      if (block.type === 'radio' || block.type === 'checkbox') {
        labels.set(block.id, String(block.props.label || block.props.title || block.props.question || '选择题'))
      }
    }
  }
  return labels
})

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '数据加载失败，请稍后重试'
}

async function ensureProject(): Promise<void> {
  projectLoading.value = true
  projectError.value = ''
  try {
    const local = projectStore.getById(projectId.value)
    project.value = local ?? (await projectStore.refreshProject(projectId.value)) ?? null
    if (!project.value) projectError.value = '项目不存在，或你没有查看数据的权限'
  } catch (error) {
    project.value = null
    projectError.value = errorText(error)
  } finally {
    projectLoading.value = false
  }
}

async function loadAnalytics(): Promise<void> {
  summary.value = await getAnalyticsSummary(projectId.value)
}

async function loadSubmissions(): Promise<void> {
  const result = await listSubmissions(projectId.value, {
    status: submissionStatus.value,
    page: submissionPage.value,
    pageSize: submissionPageSize,
  })
  submissions.value = result.items
  submissionTotal.value = result.pagination.total
  submissionPage.value = result.pagination.page
}

function restoreExports(): ExportJob[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(exportStorageKey.value) ?? '[]') as unknown
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((item): item is ExportJob => Boolean(item && typeof item === 'object' && 'id' in item))
      .slice(0, 8)
  } catch {
    return []
  }
}

function persistExports(): void {
  try {
    localStorage.setItem(exportStorageKey.value, JSON.stringify(exports.value.slice(0, 8)))
  } catch {
    // 本地记录失败不影响后端导出。
  }
}

function upsertExport(job: ExportJob): void {
  const index = exports.value.findIndex((item) => item.id === job.id)
  if (index >= 0) exports.value[index] = { ...exports.value[index], ...job }
  else exports.value.unshift(job)
  exports.value = exports.value
    .sort((a, b) => String(b.createdAt ?? '').localeCompare(String(a.createdAt ?? '')))
    .slice(0, 8)
  persistExports()
}

async function loadExports(): Promise<void> {
  const localJobs = restoreExports()
  let remoteJobs: ExportJob[] = []
  try {
    remoteJobs = await listExports(projectId.value)
  } catch {
    // 后端暂时不可用时保留浏览器内导出记录。
  }
  const merged = new Map<string, ExportJob>()
  for (const job of localJobs) merged.set(job.id, job)
  for (const job of remoteJobs) merged.set(job.id, { ...merged.get(job.id), ...job })
  exports.value = [...merged.values()]
  const snapshots = await Promise.allSettled(
    exports.value.map(async (job) => ({ job, remote: await getExport(job.id) }))
  )
  for (const snapshot of snapshots) {
    if (snapshot.status === 'fulfilled') upsertExport({ ...snapshot.value.job, ...snapshot.value.remote })
  }
}

async function loadActiveTab(): Promise<void> {
  if (!project.value) return
  loading.value = true
  loadError.value = ''
  try {
    if (activeTab.value === 'analytics') await loadAnalytics()
    else if (activeTab.value === 'submissions') await loadSubmissions()
    else await loadExports()
  } catch (error) {
    loadError.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function refreshCurrentTab(): Promise<void> {
  await loadActiveTab()
  if (!loadError.value) toast.pushToast('数据已刷新', 'success')
}

function setSubmissionFilter(status: SubmissionStatus | ''): void {
  if (submissionStatus.value === status) return
  submissionStatus.value = status
  submissionPage.value = 1
  void loadSubmissions().catch((error) => {
    loadError.value = errorText(error)
  })
}

function changeSubmissionPage(delta: number): void {
  const next = submissionPage.value + delta
  if (next < 1 || next > submissionTotalPages.value) return
  submissionPage.value = next
  void loadSubmissions().catch((error) => {
    loadError.value = errorText(error)
  })
}

function eventLabel(type: string): string {
  const labels: Record<string, string> = {
    public_page_view: '页面浏览',
    form_started: '开始填写',
    form_submit: '点击提交',
    form_submitted: '提交表单',
    submission_succeeded: '提交成功',
    submission_rejected: '提交被拦截',
  }
  return labels[type] ?? type.replace(/_/g, ' ')
}

function eventPercent(count: number): number {
  return Math.max(4, Math.round((count / eventMax.value) * 100))
}

function chartPoints(key: 'views' | 'submissions'): string {
  const length = trend.value.length
  if (!length) return ''
  return trend.value
    .map((item, index) => {
      const x = length === 1 ? 50 : (index / (length - 1)) * 100
      const value = Number(item[key]) || 0
      const y = 44 - (value / trendMax.value) * 36
      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')
}

function formatDay(value: string): string {
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value.slice(5)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function pageName(pageId: string): string {
  return project.value?.document.pages.find((page) => page.id === pageId)?.name || '活动页面'
}

function answerEntries(answers: Record<string, unknown>): Array<{ label: string; value: string }> {
  return Object.entries(answers ?? {}).map(([key, raw]) => ({
    label: fieldLabels.value.get(key) ?? key.split(':').slice(-1)[0] ?? key,
    value: displayAnswer(raw),
  }))
}

function displayAnswer(value: unknown): string {
  if (Array.isArray(value)) return value.map(String).join('、') || '未填写'
  if (value === null || value === undefined || value === '') return '未填写'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function exportStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    queued: '排队中',
    running: '生成中',
    succeeded: '可下载',
    failed: '生成失败',
    expired: '已过期',
  }
  return labels[status] ?? status
}

function exportFileName(job: ExportJob): string {
  const key = job.objectKey?.split('/').slice(-1)[0]
  return key || `心晴活动参与数据-${formatDateTime(job.createdAt).replace(/\//g, '-')}.csv`
}

async function requestExport(): Promise<void> {
  if (!project.value || exporting.value) return
  exporting.value = true
  try {
    const job = await createExport(projectId.value)
    upsertExport(job)
    toast.pushToast(
      job.status === 'succeeded' ? 'CSV 已生成，可以下载' : '导出任务已创建',
      job.status === 'failed' ? 'warn' : 'success'
    )
  } catch (error) {
    toast.pushToast(errorText(error), 'error')
  } finally {
    exporting.value = false
  }
}

async function refreshExport(job: ExportJob): Promise<void> {
  refreshingExportId.value = job.id
  try {
    upsertExport({ ...job, ...(await getExport(job.id)) })
  } catch (error) {
    toast.pushToast(errorText(error), 'warn')
  } finally {
    refreshingExportId.value = ''
  }
}

async function downloadExport(job: ExportJob): Promise<void> {
  if (downloadingId.value) return
  downloadingId.value = job.id
  try {
    const url = await getExportDownloadUrl(job.id)
    if (!url) throw new Error('下载地址暂不可用')
    const link = document.createElement('a')
    link.href = url
    link.download = exportFileName(job)
    link.rel = 'noopener'
    document.body.append(link)
    link.click()
    link.remove()
  } catch (error) {
    toast.pushToast(errorText(error), 'error')
  } finally {
    downloadingId.value = ''
  }
}

let initialized = false

onMounted(async () => {
  await ensureProject()
  initialized = true
  await loadActiveTab()
})

watch(projectId, async () => {
  if (!initialized) return
  summary.value = null
  submissions.value = []
  submissionTotal.value = 0
  submissionPage.value = 1
  submissionStatus.value = ''
  exports.value = []
  await ensureProject()
  await loadActiveTab()
})

watch(activeTab, async () => {
  if (!initialized) return
  await loadActiveTab()
})
</script>

<template>
  <WorkspaceShell>
    <main class="data-dashboard">
      <header class="data-heading">
        <div>
          <RouterLink class="data-back" to="/projects"><span>←</span>返回心晴运营台</RouterLink>
          <p class="eyebrow">Activity intelligence</p>
          <h1>活动参与数据</h1>
          <p>{{ project?.document.title || '心理活动' }} · 浏览、参与和导出都在这里完成。</p>
        </div>
        <div class="data-heading-actions">
          <button class="app-button app-button--secondary" type="button" :disabled="loading" @click="refreshCurrentTab">
            <Icon name="refresh" :size="15" />
            {{ loading ? '刷新中' : '刷新数据' }}
          </button>
          <RouterLink v-if="previewPath" class="app-button app-button--secondary" :to="previewPath" target="_blank">
            <Icon name="eye" :size="15" />预览页面
          </RouterLink>
          <RouterLink class="app-button app-button--primary" :to="editPath">
            <Icon name="layout" :size="15" />继续编辑
          </RouterLink>
        </div>
      </header>

      <nav class="data-tabs" aria-label="活动数据导航">
        <RouterLink
          v-for="tab in tabs"
          :key="tab.id"
          :to="tab.href"
          class="data-tab"
          :class="{ 'is-active': activeTab === tab.id }"
        >
          <span><Icon :name="tab.icon" :size="17" /></span>
          <span>
            <strong>{{ tab.label }}</strong>
            <small>{{ tab.hint }}</small>
          </span>
        </RouterLink>
      </nav>

      <section v-if="projectLoading" class="data-state">
        <span class="data-state-icon"><Icon name="refresh" :size="23" /></span>
        <strong>正在读取项目</strong>
        <p>正在确认项目权限并加载活动信息。</p>
      </section>

      <section v-else-if="projectError" class="data-state data-state--error">
        <span class="data-state-icon"><Icon name="alert" :size="23" /></span>
        <strong>无法打开项目数据</strong>
        <p>{{ projectError }}</p>
        <RouterLink class="app-button app-button--secondary" to="/projects">返回工作台</RouterLink>
      </section>

      <template v-else>
        <section v-if="loading" class="data-state data-state--compact">
          <span class="data-state-icon"><Icon name="refresh" :size="21" /></span>
          <strong>正在加载{{ tabs.find((item) => item.id === activeTab)?.label }}</strong>
          <p>统计数据会直接从业务后端读取。</p>
        </section>

        <section v-else-if="loadError" class="data-state data-state--error data-state--compact">
          <span class="data-state-icon"><Icon name="alert" :size="21" /></span>
          <strong>数据加载失败</strong>
          <p>{{ loadError }}</p>
          <button class="app-button app-button--secondary" type="button" @click="refreshCurrentTab">重新加载</button>
        </section>

        <template v-else>
          <section v-if="activeTab === 'analytics'" class="data-panel">
            <div class="metric-grid">
              <article class="metric-card">
                <span class="metric-icon metric-icon--blue"><Icon name="eye" :size="18" /></span>
                <div>
                  <small>页面浏览</small><strong>{{ num(summary?.pageViews) }}</strong
                  ><em>累计公开页访问</em>
                </div>
              </article>
              <article class="metric-card">
                <span class="metric-icon metric-icon--violet"><Icon name="cursor" :size="18" /></span>
                <div>
                  <small>开始填写</small><strong>{{ num(summary?.formStarted) }}</strong
                  ><em>触发表单交互</em>
                </div>
              </article>
              <article class="metric-card">
                <span class="metric-icon metric-icon--green"><Icon name="check-circle" :size="18" /></span>
                <div>
                  <small>有效提交</small><strong>{{ num(summary?.validSubmissions) }}</strong>
                  <em>共 {{ num(summary?.submissions) }} 条提交</em>
                </div>
              </article>
              <article class="metric-card">
                <span class="metric-icon metric-icon--amber"><Icon name="chart" :size="18" /></span>
                <div>
                  <small>填写转化率</small><strong>{{ conversionPercent }}%</strong><em>有效提交 / 开始填写</em>
                </div>
              </article>
            </div>

            <div class="analytics-grid">
              <article class="data-card trend-card">
                <div class="data-card-heading">
                  <div>
                    <span>访问趋势</span>
                    <h2>最近 7 个活跃日</h2>
                  </div>
                  <div class="chart-legend">
                    <span><i class="is-view" />浏览</span>
                    <span><i class="is-submit" />提交</span>
                  </div>
                </div>

                <div v-if="trend.length" class="trend-chart">
                  <div class="trend-chart-value">
                    <strong>{{ num(summary?.pageViews) }}</strong>
                    <span>累计浏览 · {{ num(summary?.publishedPages) }} 个页面被访问</span>
                  </div>
                  <svg viewBox="0 0 100 48" preserveAspectRatio="none" role="img" aria-label="浏览与提交趋势">
                    <line v-for="line in [8, 20, 32, 44]" :key="line" x1="0" :y1="line" x2="100" :y2="line" />
                    <polyline class="trend-line trend-line--views" :points="chartPoints('views')" />
                    <polyline class="trend-line trend-line--submissions" :points="chartPoints('submissions')" />
                  </svg>
                  <div class="trend-axis">
                    <span v-for="item in trend" :key="item.date">{{ formatDay(item.date) }}</span>
                  </div>
                </div>
                <div v-else class="data-empty data-empty--inset">
                  <Icon name="chart" :size="25" />
                  <strong>还没有访问趋势</strong>
                  <p>发布心晴活动并分享链接后，这里会按天汇总浏览与参与。</p>
                </div>
              </article>

              <article class="data-card event-card">
                <div class="data-card-heading">
                  <div>
                    <span>行为构成</span>
                    <h2>关键事件</h2>
                  </div>
                  <span class="data-card-tag">实时累计</span>
                </div>
                <div v-if="eventDistribution.length" class="event-list">
                  <div v-for="item in eventDistribution" :key="item.type" class="event-row">
                    <div>
                      <span>{{ item.label }}</span
                      ><strong>{{ item.count }}</strong>
                    </div>
                    <span class="event-track"><i :style="{ width: `${eventPercent(item.count)}%` }" /></span>
                  </div>
                </div>
                <div v-else class="data-empty data-empty--inset">
                  <Icon name="cursor" :size="25" />
                  <strong>还没有行为事件</strong>
                  <p>同学打开或填写活动页面后，会产生浏览与参与事件。</p>
                </div>
              </article>
            </div>
          </section>

          <section v-else-if="activeTab === 'submissions'" class="data-panel">
            <div class="panel-toolbar">
              <div class="filter-pills" aria-label="提交状态筛选">
                <button
                  type="button"
                  :class="{ 'is-active': submissionStatus === '' }"
                  @click="setSubmissionFilter('')"
                >
                  全部
                </button>
                <button
                  type="button"
                  :class="{ 'is-active': submissionStatus === 'valid' }"
                  @click="setSubmissionFilter('valid')"
                >
                  有效
                </button>
                <button
                  type="button"
                  :class="{ 'is-active': submissionStatus === 'invalid' }"
                  @click="setSubmissionFilter('invalid')"
                >
                  无效
                </button>
              </div>
              <span class="panel-count">共 {{ submissionTotal }} 条参与记录</span>
            </div>

            <article class="data-card submission-card">
              <div v-if="submissions.length" class="submission-table-wrap">
                <table class="submission-table">
                  <thead>
                    <tr>
                      <th>提交时间</th>
                      <th>表单内容</th>
                      <th>来源页面</th>
                      <th>状态</th>
                      <th>记录 ID</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="submission in submissions" :key="submission.id">
                      <td>
                        <strong>{{ formatDateTime(submission.submittedAt) }}</strong>
                      </td>
                      <td>
                        <div class="answer-list">
                          <span
                            v-for="answer in answerEntries(submission.answers).slice(0, 3)"
                            :key="`${submission.id}-${answer.label}`"
                          >
                            <em>{{ answer.label }}</em
                            >{{ answer.value }}
                          </span>
                          <small v-if="Object.keys(submission.answers ?? {}).length > 3">
                            +{{ Object.keys(submission.answers).length - 3 }} 项
                          </small>
                        </div>
                      </td>
                      <td>{{ pageName(submission.pageId) }}</td>
                      <td>
                        <span class="record-status" :class="`record-status--${submission.status}`">
                          {{ submission.status === 'valid' ? '有效' : '无效' }}
                        </span>
                      </td>
                      <td>
                        <code>{{ submission.id.slice(0, 8) }}</code>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-else class="data-empty">
                <Icon name="users" :size="28" />
                <strong>{{ submissionStatus ? '没有符合条件的参与记录' : '还没有参与记录' }}</strong>
                <p>
                  {{ submissionStatus ? '切换筛选状态再查看。' : '发布活动并收到第一份参与记录后，会显示在这里。' }}
                </p>
              </div>
            </article>

            <footer v-if="submissionTotal > 0" class="data-pagination">
              <span>第 {{ submissionPage }} / {{ submissionTotalPages }} 页</span>
              <div>
                <button type="button" :disabled="submissionPage <= 1" @click="changeSubmissionPage(-1)">上一页</button>
                <button
                  type="button"
                  :disabled="submissionPage >= submissionTotalPages"
                  @click="changeSubmissionPage(1)"
                >
                  下一页
                </button>
              </div>
            </footer>
          </section>

          <section v-else class="data-panel">
            <article class="export-hero">
              <div class="export-hero-copy">
                <span class="export-file-icon"><Icon name="file" :size="25" /></span>
                <div>
                  <p class="eyebrow">CSV export</p>
                  <h2>把参与数据带回 Excel</h2>
                  <p>导出包含提交时间、来源页面、表单状态和完整答案，文件生成后可下载 24 小时。</p>
                </div>
              </div>
              <button class="app-button app-button--primary" type="button" :disabled="exporting" @click="requestExport">
                <Icon name="download" :size="15" />
                {{ exporting ? '正在生成 CSV' : '生成 CSV 文件' }}
              </button>
            </article>

            <div class="export-facts">
              <article>
                <span><Icon name="check-circle" :size="16" /></span>
                <div>
                  <strong>UTF-8 BOM</strong>
                  <p>Excel 打开中文不乱码</p>
                </div>
              </article>
              <article>
                <span><Icon name="cloud" :size="16" /></span>
                <div>
                  <strong>后端安全存储</strong>
                  <p>下载链接需要登录权限</p>
                </div>
              </article>
              <article>
                <span><Icon name="clock" :size="16" /></span>
                <div>
                  <strong>保留 24 小时</strong>
                  <p>过期后可重新生成</p>
                </div>
              </article>
            </div>

            <article class="data-card export-history">
              <div class="data-card-heading">
                <div>
                  <span>最近文件</span>
                  <h2>导出记录</h2>
                </div>
                <span class="data-card-tag">{{ exports.length }} 个</span>
              </div>
              <div v-if="exports.length" class="export-list">
                <div v-for="job in exports" :key="job.id" class="export-row">
                  <span class="export-row-icon"><Icon name="file" :size="18" /></span>
                  <div class="export-row-copy">
                    <strong>{{ exportFileName(job) }}</strong>
                    <span>{{ formatDateTime(job.createdAt) }} · {{ job.id.slice(0, 8) }}</span>
                  </div>
                  <span class="export-status" :class="`export-status--${job.status}`">
                    {{ exportStatusLabel(job.status) }}
                  </span>
                  <button
                    v-if="job.status === 'succeeded'"
                    class="export-action"
                    type="button"
                    :disabled="downloadingId === job.id"
                    @click="downloadExport(job)"
                  >
                    <Icon name="download" :size="14" />{{ downloadingId === job.id ? '准备中' : '下载' }}
                  </button>
                  <button
                    v-else
                    class="export-action"
                    type="button"
                    :disabled="refreshingExportId === job.id"
                    @click="refreshExport(job)"
                  >
                    <Icon name="refresh" :size="14" />刷新
                  </button>
                </div>
              </div>
              <div v-else class="data-empty data-empty--inset">
                <Icon name="download" :size="27" />
                <strong>还没有导出记录</strong>
                <p>点击“生成 CSV 文件”，参与数据会整理成可直接打开的表格。</p>
              </div>
            </article>
          </section>
        </template>
      </template>
    </main>
  </WorkspaceShell>
</template>
