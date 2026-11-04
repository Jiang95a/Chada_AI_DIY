<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import {
  getSubmissionSummary,
  listSubmissions,
  type SubmissionRecord,
  type SubmissionSummary,
} from '@/services/submissions'
import { useProjectStore, type ProjectRecord } from '@/stores/projects'

const REMOTE_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const projectStore = useProjectStore()
const toast = useToast()

const loading = ref(true)
const search = ref('')
const selectedId = ref('')
const submissions = ref<SubmissionRecord[]>([])
const submissionSummary = ref<SubmissionSummary | null>(null)
const submissionLoading = ref(false)
const submissionError = ref('')

const publishedProjects = computed(() =>
  projectStore.projects
    .filter((project) => project.status === 'published')
    .sort((a, b) => b.document.updatedAt - a.document.updatedAt)
)

const visibleProjects = computed(() => {
  const query = search.value.trim().toLowerCase()
  if (!query) return publishedProjects.value
  return publishedProjects.value.filter((project) =>
    `${project.document.title} ${project.type} ${project.coverTitle}`.toLowerCase().includes(query)
  )
})

const selectedProject = computed(
  () => publishedProjects.value.find((project) => project.id === selectedId.value) ?? publishedProjects.value[0]
)

const totalParticipants = computed(() =>
  publishedProjects.value.reduce((total, project) => total + project.submissionCount, 0)
)
const withParticipants = computed(() => publishedProjects.value.filter((project) => project.submissionCount > 0).length)
const latestPublished = computed(() => publishedProjects.value[0] ?? null)
const maxSubmissions = computed(() => Math.max(1, ...publishedProjects.value.map((project) => project.submissionCount)))

const fieldLabels = computed(() => {
  const labels = new Map<string, string>()
  for (const page of selectedProject.value?.document.pages ?? []) {
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

const participantRows = computed(() =>
  submissions.value.map((submission) => {
    const anonymous = Boolean(submission.anonymous || submission.privacyMode === 'anonymous')
    return {
      ...submission,
      anonymous,
      name: anonymous
        ? `匿名参与者${submission.anonymousToken ? ` #${submission.anonymousToken}` : ''}`
        : findAnswer(submission.answers, ['姓名', '名字', '昵称', 'name']) || '未填写姓名',
      contact: anonymous ? '已隐藏' : findAnswer(submission.answers, ['手机', '电话', '微信', '邮箱', '联系']),
      details: anonymous
        ? []
        : answerEntries(submission.answers).filter((item) => !['姓名', '名字', '昵称'].includes(item.label)),
    }
  })
)

function projectSlug(project: ProjectRecord): string {
  return project.publicSlug || project.document.published?.slug || project.document.slug
}

function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return '最近更新'
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function formatSubmittedAt(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value || '—'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function answerEntries(answers: Record<string, unknown>): Array<{ label: string; value: string }> {
  return Object.entries(answers ?? {}).map(([key, value]) => ({
    label: fieldLabels.value.get(key) ?? key.split(':').slice(-1)[0] ?? key,
    value: displayAnswer(value),
  }))
}

function displayAnswer(value: unknown): string {
  if (Array.isArray(value)) return value.map(String).join('、') || '未填写'
  if (value === null || value === undefined || value === '') return '未填写'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function findAnswer(answers: Record<string, unknown>, keywords: string[]): string {
  const entry = answerEntries(answers).find((item) => keywords.some((keyword) => item.label.includes(keyword)))
  return entry?.value === '未填写' ? '' : (entry?.value ?? '')
}

function selectProject(project: ProjectRecord): void {
  selectedId.value = project.id
}

async function loadParticipants(project: ProjectRecord | undefined): Promise<void> {
  submissions.value = []
  submissionSummary.value = null
  submissionError.value = ''
  if (!project) return
  if (!REMOTE_ID_PATTERN.test(project.id)) {
    submissionError.value = '当前是本地演示活动，连接后端并发布真实活动后可读取参与明细。'
    return
  }
  submissionLoading.value = true
  try {
    const result = await listSubmissions(project.id, { page: 1, pageSize: 8 })
    submissions.value = result.items
    try {
      submissionSummary.value = await getSubmissionSummary(project.id)
    } catch {
      submissionSummary.value = null
    }
  } catch (error) {
    submissionError.value = error instanceof Error ? error.message : '参与信息加载失败'
  } finally {
    submissionLoading.value = false
  }
}

async function refresh(): Promise<void> {
  loading.value = true
  await projectStore.loadProjects()
  loading.value = false
  await loadParticipants(selectedProject.value)
  if (!projectStore.remoteError) toast.pushToast('参与数据已刷新', 'success')
}

watch(
  publishedProjects,
  (projects) => {
    if (!projects.some((project) => project.id === selectedId.value)) {
      selectedId.value = projects[0]?.id ?? ''
    }
  },
  { immediate: true }
)

watch(
  selectedProject,
  (project) => {
    void loadParticipants(project)
  },
  { immediate: true }
)

onMounted(async () => {
  await projectStore.loadProjects()
  loading.value = false
})
</script>

<template>
  <WorkspaceShell>
    <main class="published-page">
      <header class="published-heading">
        <div>
          <p class="eyebrow">Participation overview</p>
          <h1>参与数据</h1>
          <p>集中查看已发布的心晴活动、参与人数和必要的基础信息。</p>
        </div>
        <div class="published-heading-actions">
          <button class="app-button app-button--secondary" type="button" :disabled="loading" @click="refresh">
            <Icon name="refresh" :size="15" />{{ loading ? '同步中' : '刷新数据' }}
          </button>
          <RouterLink class="app-button app-button--primary" to="/projects/new">
            <Icon name="plus" :size="15" />发起新活动
          </RouterLink>
        </div>
      </header>

      <section class="published-summary">
        <article class="summary-primary">
          <span>累计参与人次</span>
          <strong>{{ totalParticipants }}</strong>
          <p>来自 {{ publishedProjects.length }} 个心晴活动</p>
          <div class="summary-spark" aria-hidden="true">
            <i
              v-for="(project, index) in publishedProjects.slice(0, 8)"
              :key="project.id"
              :style="{
                height: `${Math.max(16, (project.submissionCount / maxSubmissions) * 100)}%`,
                animationDelay: `${index * 60}ms`,
              }"
            />
          </div>
        </article>
        <article>
          <span class="summary-icon is-blue"><Icon name="rocket" :size="17" /></span>
          <div>
            <small>已发布活动</small><strong>{{ publishedProjects.length }}</strong
            ><em>正在公开访问</em>
          </div>
        </article>
        <article>
          <span class="summary-icon is-green"><Icon name="users" :size="17" /></span>
          <div>
            <small>收到参与</small><strong>{{ withParticipants }}</strong
            ><em>个活动有参与记录</em>
          </div>
        </article>
        <article>
          <span class="summary-icon is-amber"><Icon name="clock" :size="17" /></span>
          <div>
            <small>最近发布</small>
            <strong>{{ latestPublished ? formatTime(latestPublished.document.updatedAt) : '—' }}</strong>
            <em>{{ latestPublished?.document.title || '暂无发布记录' }}</em>
          </div>
        </article>
      </section>

      <section v-if="publishedProjects.length" class="published-workspace">
        <aside class="published-apps">
          <header>
            <div>
              <span>心晴活动</span>
              <strong>{{ publishedProjects.length }} 个已发布</strong>
            </div>
            <label>
              <Icon name="search" :size="14" />
              <input v-model="search" aria-label="搜索已发布心理活动" placeholder="搜索心晴活动" />
            </label>
          </header>

          <div class="published-app-list">
            <button
              v-for="project in visibleProjects"
              :key="project.id"
              type="button"
              :class="{ 'is-active': project.id === selectedProject?.id }"
              @click="selectProject(project)"
            >
              <span class="app-cover" :class="`cover--${project.coverTone}`">{{ project.coverCode || 'APP' }}</span>
              <span class="app-list-copy">
                <strong>{{ project.document.title }}</strong>
                <small>{{ project.type }} · {{ formatTime(project.document.updatedAt) }}更新</small>
                <i><b :style="{ width: `${Math.max(4, (project.submissionCount / maxSubmissions) * 100)}%` }" /></i>
              </span>
              <span class="app-count"
                ><strong>{{ project.submissionCount }}</strong
                ><small>人参与</small></span
              >
            </button>

            <p v-if="!visibleProjects.length">没有找到匹配的心晴活动。</p>
          </div>
        </aside>

        <section v-if="selectedProject" class="published-detail">
          <header class="selected-app-head">
            <div>
              <span class="selected-status"><i />开放中</span>
              <h2>{{ selectedProject.document.title }}</h2>
              <p>{{ selectedProject.type }} · {{ selectedProject.coverCode || 'CAMPUS APPLICATION' }}</p>
            </div>
            <div class="selected-actions">
              <RouterLink :to="`/p/${projectSlug(selectedProject)}`" target="_blank">
                <Icon name="external" :size="14" />打开页面
              </RouterLink>
              <RouterLink :to="`/editor/${selectedProject.id}`"> <Icon name="layout" :size="14" />继续编辑 </RouterLink>
            </div>
          </header>

          <div class="selected-metrics">
            <article>
              <span><Icon name="users" :size="16" /></span>
              <div>
                <small>参与人数</small><strong>{{ selectedProject.submissionCount }}</strong>
              </div>
            </article>
            <article>
              <span><Icon name="file" :size="16" /></span>
              <div>
                <small>页面数量</small><strong>{{ selectedProject.document.pages.length }}</strong>
              </div>
            </article>
            <article>
              <span><Icon name="clock" :size="16" /></span>
              <div>
                <small>最近更新</small><strong>{{ formatTime(selectedProject.document.updatedAt) }}</strong>
              </div>
            </article>
            <RouterLink :to="`/projects/${selectedProject.id}/submissions`">
              查看完整参与数据 <Icon name="arrow-right" :size="14" />
            </RouterLink>
          </div>

          <section class="participant-card">
            <header>
              <div>
                <span>参与记录</span>
                <h3>最近参与的同学</h3>
              </div>
              <span class="participant-total">共 {{ selectedProject.submissionCount }} 人</span>
            </header>
            <div v-if="submissionSummary" class="participant-privacy-summary">
              <span>收到 {{ submissionSummary.total }} 份记录</span>
              <span>{{ submissionSummary.anonymous }} 份匿名参与</span>
              <span>{{ submissionSummary.consented }} 份已同意</span>
            </div>

            <div v-if="submissionLoading" class="participant-state">
              <Icon name="refresh" :size="22" />
              <strong>正在读取参与信息</strong>
            </div>
            <div v-else-if="submissionError" class="participant-state is-warning">
              <Icon name="cloud" :size="22" />
              <strong>参与明细暂不可用</strong>
              <p>{{ submissionError }}</p>
              <button type="button" @click="loadParticipants(selectedProject)">重新读取</button>
            </div>
            <div v-else-if="participantRows.length" class="participant-table-wrap">
              <table class="participant-table">
                <thead>
                  <tr>
                    <th>参与人</th>
                    <th>联系方式</th>
                    <th>其他信息</th>
                    <th>提交时间</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in participantRows" :key="row.id">
                    <td>
                      <span class="participant-avatar">{{ row.name.slice(0, 1) }}</span>
                      <span class="participant-name">
                        <strong>{{ row.name }}</strong>
                        <em v-if="row.anonymous">匿名</em>
                      </span>
                    </td>
                    <td>{{ row.contact || '未填写' }}</td>
                    <td>
                      <span v-for="detail in row.details.slice(0, 2)" :key="`${row.id}-${detail.label}`">
                        <b>{{ detail.label }}</b
                        >{{ detail.value }}
                      </span>
                      <em v-if="!row.details.length">{{ row.anonymous ? '匿名记录已隐藏' : '无其他信息' }}</em>
                    </td>
                    <td>{{ formatSubmittedAt(row.submittedAt) }}</td>
                    <td>
                      <span class="record-status is-valid">{{ row.status === 'valid' ? '有效' : '无效' }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="participant-state">
              <span class="participant-empty-icon"><Icon name="users" :size="23" /></span>
              <strong>还没有参与记录</strong>
              <p>把活动页面分享给同学，新参与记录会显示在这里。</p>
              <RouterLink :to="`/projects/${selectedProject.id}/submissions`">打开参与管理</RouterLink>
            </div>
          </section>
        </section>
      </section>

      <section v-else class="published-empty">
        <span><Icon name="rocket" :size="28" /></span>
        <strong>还没有已发布的心晴活动</strong>
        <p>创建心理活动并完成发布后，这里会汇总参与人数和必要的信息。</p>
        <RouterLink class="app-button app-button--primary" to="/projects/new">
          <Icon name="plus" :size="15" />创建第一个活动
        </RouterLink>
      </section>
    </main>
  </WorkspaceShell>
</template>

<style scoped>
.published-page {
  width: min(1440px, 100%);
  margin: 0 auto;
  padding: 28px 34px 60px;
}

.published-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.published-heading h1 {
  margin: 3px 0 7px;
  color: var(--ink-900, #101828);
  font-size: 25px;
  letter-spacing: -0.035em;
}

.published-heading > div > p:last-child {
  color: var(--ink-500, #667085);
  font-size: 12px;
}

.eyebrow {
  color: var(--ink-400, #98a2b3);
  font-size: 9px;
  font-weight: 750;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.published-heading-actions {
  display: flex;
  gap: 8px;
}

.published-summary {
  display: grid;
  gap: 12px;
  margin-top: 22px;
  grid-template-columns: minmax(260px, 1.4fr) repeat(3, minmax(160px, 0.7fr));
}

.published-summary > article {
  display: flex;
  min-height: 128px;
  align-items: center;
  gap: 12px;
  padding: 20px;
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 17px;
  background: #fff;
  box-shadow: 0 14px 35px -34px rgb(15 23 42 / 35%);
}

.published-summary > article:not(.summary-primary) > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.published-summary small {
  color: var(--ink-500, #667085);
  font-size: 9px;
  font-weight: 650;
}

.published-summary em {
  overflow: hidden;
  margin-top: 4px;
  color: var(--ink-400, #98a2b3);
  font-size: 8px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.published-summary article > div > strong {
  margin-top: 6px;
  color: var(--ink-900, #101828);
  font-size: 23px;
  letter-spacing: -0.04em;
}

.summary-primary {
  position: relative;
  overflow: hidden;
  align-items: flex-start !important;
  flex-direction: column;
  color: #fff;
  background:
    radial-gradient(circle at 90% 10%, rgb(255 255 255 / 24%), transparent 28%),
    linear-gradient(135deg, #172554, #3556bf 63%, #24a17b) !important;
}

.summary-primary > span {
  color: rgb(255 255 255 / 62%);
  font-size: 9px;
  font-weight: 650;
}

.summary-primary > strong {
  margin-top: 4px;
  font-size: 38px;
  letter-spacing: -0.06em;
}

.summary-primary > p {
  color: rgb(255 255 255 / 58%);
  font-size: 9px;
}

.summary-spark {
  position: absolute;
  right: 20px;
  bottom: 17px;
  display: flex;
  height: 56px;
  align-items: flex-end;
  gap: 4px;
}

.summary-spark i {
  width: 5px;
  border-radius: 99px;
  background: rgb(255 255 255 / 38%);
  animation: bar-in 0.55s ease both;
}

.summary-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 11px;
  place-items: center;
}

.summary-icon.is-blue {
  color: #3158df;
  background: #eef2ff;
}

.summary-icon.is-green {
  color: #07845f;
  background: #ecfdf5;
}

.summary-icon.is-amber {
  color: #b45309;
  background: #fffaeb;
}

.published-workspace {
  display: grid;
  align-items: start;
  gap: 16px;
  margin-top: 18px;
  grid-template-columns: 330px minmax(0, 1fr);
}

.published-apps,
.published-detail,
.participant-card,
.published-empty {
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 16px 40px -38px rgb(15 23 42 / 34%);
}

.published-apps {
  overflow: hidden;
}

.published-apps > header {
  display: flex;
  gap: 12px;
  padding: 17px;
  border-bottom: 1px solid var(--line, #e9ecf3);
  flex-direction: column;
}

.published-apps > header > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.published-apps > header span {
  color: var(--ink-800, #1e293b);
  font-size: 11px;
  font-weight: 750;
}

.published-apps > header strong {
  color: var(--ink-400, #98a2b3);
  font-size: 8px;
}

.published-apps label {
  display: flex;
  height: 35px;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 9px;
  color: var(--ink-400, #98a2b3);
  background: var(--ink-50, #f8fafc);
}

.published-apps input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 9px;
}

.published-app-list {
  display: grid;
  max-height: 590px;
  padding: 7px;
  overflow-y: auto;
  gap: 4px;
}

.published-app-list > button {
  display: grid;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--ink-800, #1e293b);
  background: transparent;
  text-align: left;
  cursor: pointer;
  grid-template-columns: 42px minmax(0, 1fr) auto;
}

.published-app-list > button:hover {
  background: var(--ink-50, #f8fafc);
}

.published-app-list > button.is-active {
  border-color: var(--brand-100, #e0e7ff);
  background: #f7f7ff;
  box-shadow: inset 3px 0 var(--brand-500, #6366f1);
}

.app-cover {
  display: grid;
  width: 42px;
  height: 48px;
  padding: 6px;
  border-radius: 8px;
  color: #fff;
  font-size: 6px;
  font-weight: 800;
  letter-spacing: 0.06em;
  place-items: end start;
}

.app-cover.cover--photo {
  background: linear-gradient(145deg, #88ad94, #365746);
}

.app-cover.cover--music,
.app-cover.cover--vote {
  background: linear-gradient(145deg, #617d84, #b8cbc8);
}

.app-cover.cover--read,
.app-cover.cover--checkin {
  background: linear-gradient(145deg, #3f6b50, #b7c99e);
}

.app-cover.cover--volunteer,
.app-cover.cover--event {
  background: linear-gradient(145deg, #547a73, #d2b5a3);
}

.app-list-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.app-list-copy > strong {
  overflow: hidden;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-list-copy > small {
  margin-top: 3px;
  color: var(--ink-400, #98a2b3);
  font-size: 7px;
}

.app-list-copy > i {
  height: 3px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--ink-100, #f2f4f7);
}

.app-list-copy > i > b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--brand-500, #6366f1), #55c7ad);
}

.app-count {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
}

.app-count > strong {
  color: var(--brand-600, #4f46e5);
  font-size: 15px;
}

.app-count > small {
  margin-top: 2px;
  color: var(--ink-400, #98a2b3);
  font-size: 7px;
}

.published-app-list > p {
  padding: 24px 10px;
  color: var(--ink-400, #98a2b3);
  font-size: 9px;
  text-align: center;
}

.published-detail {
  min-width: 0;
  overflow: hidden;
}

.selected-app-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
  background:
    radial-gradient(circle at 90% 0%, rgb(99 102 241 / 9%), transparent 28%), linear-gradient(180deg, #fbfcff, #fff);
}

.selected-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #07845f;
  font-size: 8px;
  font-weight: 750;
}

.selected-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #12b76a;
  box-shadow: 0 0 0 4px rgb(18 183 106 / 9%);
}

.selected-app-head h2 {
  margin: 8px 0 4px;
  color: var(--ink-900, #101828);
  font-size: 20px;
  letter-spacing: -0.035em;
}

.selected-app-head p {
  color: var(--ink-400, #98a2b3);
  font-size: 9px;
}

.selected-actions {
  display: flex;
  gap: 6px;
}

.selected-actions a {
  display: inline-flex;
  min-height: 33px;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 8px;
  color: var(--ink-600, #475467);
  background: #fff;
  font-size: 8px;
}

.selected-actions a:first-child {
  border-color: var(--brand-100, #e0e7ff);
  color: var(--brand-600, #4f46e5);
}

.selected-metrics {
  display: grid;
  align-items: center;
  gap: 8px;
  padding: 0 22px 18px;
  grid-template-columns: repeat(3, minmax(120px, 1fr)) auto;
}

.selected-metrics article {
  display: flex;
  min-height: 65px;
  align-items: center;
  gap: 9px;
  padding: 11px;
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 11px;
  background: #fff;
}

.selected-metrics article > span {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border-radius: 9px;
  color: var(--brand-600, #4f46e5);
  background: var(--brand-50, #eef2ff);
  place-items: center;
}

.selected-metrics article > div {
  display: flex;
  flex-direction: column;
}

.selected-metrics small {
  color: var(--ink-400, #98a2b3);
  font-size: 7px;
}

.selected-metrics strong {
  margin-top: 2px;
  color: var(--ink-800, #1e293b);
  font-size: 14px;
}

.selected-metrics > a {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 5px;
  padding: 0 11px;
  color: var(--brand-600, #4f46e5);
  font-size: 8px;
  font-weight: 700;
  white-space: nowrap;
}

.participant-card {
  margin: 0 22px 22px;
  box-shadow: none;
}

.participant-card > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 16px;
  border-bottom: 1px solid var(--line, #e9ecf3);
}

.participant-card > header span {
  color: var(--brand-600, #4f46e5);
  font-size: 7px;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.participant-card h3 {
  margin-top: 4px;
  color: var(--ink-900, #101828);
  font-size: 13px;
}

.participant-total {
  padding: 4px 8px;
  border-radius: 999px;
  color: var(--ink-500, #667085) !important;
  background: var(--ink-50, #f8fafc);
  letter-spacing: 0 !important;
}

.participant-privacy-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding: 9px 16px;
  border-bottom: 1px solid var(--line, #e9ecf3);
  background: #fbfcf9;
}

.participant-privacy-summary span {
  padding: 4px 7px;
  border-radius: 999px;
  color: var(--ink-500, #667085);
  background: #fff;
  font-size: 7px;
}

.participant-table-wrap {
  overflow-x: auto;
}

.participant-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 9px;
}

.participant-table th {
  padding: 10px 14px;
  color: var(--ink-400, #98a2b3);
  background: var(--ink-50, #f8fafc);
  font-size: 7px;
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.participant-table td {
  padding: 11px 14px;
  border-top: 1px solid var(--line, #e9ecf3);
  color: var(--ink-600, #475467);
  vertical-align: middle;
}

.participant-table td:first-child {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--ink-800, #1e293b);
}

.participant-name {
  display: flex;
  align-items: center;
  gap: 5px;
}

.participant-name em {
  padding: 2px 5px;
  border-radius: 99px;
  color: var(--brand-600, #4f46e5);
  background: var(--brand-50, #eef2ff);
  font-size: 6px;
  font-style: normal;
}

.participant-avatar {
  display: grid;
  width: 27px;
  height: 27px;
  flex: 0 0 auto;
  border-radius: 8px;
  color: var(--brand-600, #4f46e5);
  background: var(--brand-50, #eef2ff);
  font-size: 9px;
  font-weight: 750;
  place-items: center;
}

.participant-table td:nth-child(3) {
  max-width: 260px;
}

.participant-table td:nth-child(3) > span {
  display: inline-flex;
  gap: 3px;
  margin: 2px 8px 2px 0;
  color: var(--ink-500, #667085);
}

.participant-table td:nth-child(3) b {
  color: var(--ink-400, #98a2b3);
  font-weight: 500;
}

.participant-table td:nth-child(3) > em {
  color: var(--ink-400, #98a2b3);
  font-size: 7px;
  font-style: normal;
}

.record-status {
  padding: 3px 7px;
  border-radius: 999px;
  color: #07845f;
  background: #ecfdf5;
  font-size: 7px;
  font-weight: 700;
}

.participant-state {
  display: flex;
  min-height: 190px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  padding: 24px;
  color: var(--ink-400, #98a2b3);
  text-align: center;
}

.participant-state.is-warning {
  color: #b45309;
  background: #fffcf5;
}

.participant-state strong {
  color: var(--ink-700, #344054);
  font-size: 10px;
}

.participant-state.is-warning strong {
  color: #92400e;
}

.participant-state p {
  max-width: 420px;
  font-size: 8px;
  line-height: 1.7;
}

.participant-state button,
.participant-state a {
  margin-top: 4px;
  padding: 5px 8px;
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 6px;
  color: var(--brand-600, #4f46e5);
  background: #fff;
  font-size: 8px;
  cursor: pointer;
}

.participant-empty-icon {
  display: grid;
  width: 42px;
  height: 42px;
  margin-bottom: 3px;
  border-radius: 12px;
  color: var(--brand-500, #6366f1);
  background: var(--brand-50, #eef2ff);
  place-items: center;
}

.published-empty {
  display: flex;
  min-height: 420px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9px;
  margin-top: 18px;
  text-align: center;
}

.published-empty > span {
  display: grid;
  width: 58px;
  height: 58px;
  border-radius: 18px;
  color: var(--brand-600, #4f46e5);
  background: var(--brand-50, #eef2ff);
  place-items: center;
}

.published-empty strong {
  margin-top: 4px;
  color: var(--ink-900, #101828);
  font-size: 15px;
}

.published-empty p {
  max-width: 460px;
  color: var(--ink-500, #667085);
  font-size: 10px;
  line-height: 1.7;
}

.published-empty a {
  margin-top: 7px;
}

@keyframes bar-in {
  from {
    opacity: 0;
    transform: scaleY(0);
    transform-origin: bottom;
  }
}

@media (max-width: 1180px) {
  .published-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .summary-primary {
    grid-column: 1 / -1;
  }

  .published-workspace {
    grid-template-columns: 290px minmax(0, 1fr);
  }

  .selected-metrics {
    grid-template-columns: repeat(3, minmax(100px, 1fr));
  }

  .selected-metrics > a {
    grid-column: 1 / -1;
    justify-self: end;
  }
}

@media (max-width: 860px) {
  .published-page {
    padding: 20px;
  }

  .published-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .published-summary,
  .published-workspace {
    grid-template-columns: 1fr;
  }

  .published-app-list {
    max-height: 300px;
  }

  .selected-app-head {
    flex-direction: column;
  }

  .participant-card {
    margin: 0 14px 14px;
  }
}

@media (max-width: 560px) {
  .published-heading-actions {
    width: 100%;
    flex-direction: column;
  }

  .selected-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
