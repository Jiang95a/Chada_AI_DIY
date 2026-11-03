<script setup lang="ts">
import { computed, onMounted, provide, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import BlockView from '@/components/editor/BlockView.vue'
import Icon from '@/components/ui/Icon.vue'
import { FORM_CONTEXT } from '@/composables/formContext'
import { useToast } from '@/composables/useToast'
import { useEditorStore } from '@/stores/editor'
import { useProjectStore } from '@/stores/projects'
import { themeCssVars } from '@/utils/theme'
import { parseDate } from '@/utils/misc'
import { getActivityPrivacy } from '@/composables/useActivityPrivacy'
import { getPublicActivity, recordPublicEvent, submitForm } from '@/services/public'
import { ApiError } from '@/services/http'
import type { Doc } from '@/types/editor'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const editorStore = useEditorStore()
const toast = useToast()
const remoteDocument = ref<Doc | null>(null)
const remoteActivity = ref<{ name: string; slug: string; status: string } | null>(null)
const publicState = ref<'loading' | 'ready' | 'offline' | 'not-found' | 'error'>('loading')
const submitting = ref(false)
const formStartedPages = new Set<string>()
const consentAccepted = ref(false)
const hasSubmitted = ref(false)
const feedbackChoice = ref('')
const feedbackNote = ref('')
const feedbackSaved = ref(false)

const slug = computed(() => String(route.params.slug ?? ''))
const project = computed(() => projectStore.getBySlug(slug.value))
const fallbackDocument = computed(() => project.value?.document ?? editorStore.doc)
const document = computed(
  () => remoteDocument.value ?? (publicState.value === 'offline' ? fallbackDocument.value : null)
)
const privacy = computed(() => getActivityPrivacy(document.value?.id || project.value?.id || '', slug.value))
const organizer = computed(
  () => privacy.value.organizerName || document.value?.meta.org || project.value?.document.meta.org || '校园心理社团'
)
const activityPhase = computed(() => {
  const start = parseDate(document.value?.meta.startAt ?? '')
  if (!start) return '开放参与'
  const now = Date.now()
  if (now < start) return '即将开始'
  if (now < start + 3 * 60 * 60 * 1000) return '正在进行'
  return '活动已结束'
})
const pages = computed(() => document.value?.pages ?? [])
const currentPageId = ref('')
const currentPage = computed(
  () =>
    pages.value.find((page) => page.id === currentPageId.value) ??
    pages.value.find((page) => page.role === 'home') ??
    pages.value[0]
)
const visibleBlocks = computed(() => currentPage.value?.blocks.filter((block) => !block.hidden) ?? [])
const themeVars = computed(() => themeCssVars(document.value?.theme ?? editorStore.doc.theme))
const shareUrl = computed(
  () =>
    project.value?.publicUrl ||
    document.value?.published?.url ||
    `${window.location.origin}/p/${document.value?.slug ?? slug.value}`
)
const unavailable = computed(() => {
  if (publicState.value === 'not-found') {
    return {
      icon: 'file',
      title: '活动不存在或尚未发布',
      text: '请确认分享链接是否正确，或联系活动发布者重新发布。',
    }
  }
  if (publicState.value === 'error') {
    return {
      icon: 'info',
      title: '活动页面加载失败',
      text: '服务暂时没有返回有效内容，请稍后刷新重试。',
    }
  }
  return {
    icon: 'layers',
    title: '正在加载活动',
    text: '正在获取最新发布内容，请稍候。',
  }
})

const answers = reactive<Record<string, string | string[]>>({})
const errors = reactive<Record<string, boolean>>({})

function setAnswer(key: string, value: string | string[]): void {
  answers[key] = value
  if (errors[key]) delete errors[key]
  void recordFormStarted()
}

function recordFormStarted(): void {
  const page = currentPage.value
  if (!page || !page.blocks.some((block) => ['form', 'radio', 'checkbox'].includes(block.type))) return
  if (formStartedPages.has(page.id)) return
  formStartedPages.add(page.id)
  void recordPublicEvent(slug.value, {
    eventType: 'form_started',
    pageId: page.id,
    metadata: { source: 'web' },
  }).catch(() => {
    // 埋点失败不能阻塞表单填写。
  })
}

function clearError(key: string): void {
  if (errors[key]) delete errors[key]
}

provide(FORM_CONTEXT, { answers, errors, setAnswer, clearError })

function resetPage(): void {
  currentPageId.value = pages.value.find((page) => page.role === 'home')?.id ?? pages.value[0]?.id ?? ''
}

watch(document, resetPage, { immediate: true })

onMounted(async () => {
  if (!slug.value) {
    publicState.value = 'not-found'
    return
  }
  try {
    const result = await getPublicActivity(slug.value)
    remoteDocument.value = result.document
    remoteActivity.value = result.activity
    publicState.value = 'ready'
  } catch (error) {
    if (error instanceof ApiError && error.status === 0) {
      // 只有真正断网时才使用本地文档，避免把 404/未发布误显示成本地演示内容。
      publicState.value = 'offline'
    } else if (error instanceof ApiError && (error.status === 404 || error.status === 410)) {
      publicState.value = 'not-found'
    } else {
      publicState.value = 'error'
    }
  }
})

function isEmpty(value: string | string[] | undefined): boolean {
  if (value === undefined) return true
  return Array.isArray(value) ? value.length === 0 : !value.trim()
}

function validate(): boolean {
  let valid = true
  for (const block of currentPage.value?.blocks ?? []) {
    if (block.hidden) continue
    if (block.type === 'form') {
      const fields = Array.isArray(block.props.fields) ? block.props.fields : []
      for (const field of fields) {
        if (!field.required) continue
        const key = `${block.id}:${field.id}`
        if (isEmpty(answers[key])) {
          errors[key] = true
          valid = false
        }
      }
    }
    if ((block.type === 'radio' || block.type === 'checkbox') && block.props.required) {
      if (isEmpty(answers[block.id])) {
        errors[block.id] = true
        valid = false
      }
    }
  }
  return valid
}

async function handleAction(action: any): Promise<void> {
  if (!action) return
  if (action.type === 'url' && action.url) {
    window.open(String(action.url), '_blank', 'noopener')
    return
  }
  if (action.type === 'page') {
    const target =
      pages.value.find((page) => page.id === action.pageId) ??
      pages.value.find((page) => page.role === action.targetRole)
    if (target) currentPageId.value = target.id
    return
  }
  if (action.type === 'submit') {
    if (privacy.value.consentRequired && !consentAccepted.value) {
      toast.pushToast('请先阅读并勾选参与说明', 'warn')
      return
    }
    if (!validate()) {
      toast.pushToast('请先补全红色标记的必填项', 'warn')
      return
    }
    const formBlock = currentPage.value?.blocks.find((block) => block.type === 'form')
    if (!formBlock || !currentPage.value) {
      toast.pushToast('当前页面没有可提交的表单', 'warn')
      return
    }
    submitting.value = true
    try {
      await submitForm(slug.value, {
        pageId: currentPage.value.id,
        formComponentId: formBlock.id,
        answers,
      })
      const successPage = pages.value.find((page) => page.role === 'success')
      if (successPage) currentPageId.value = successPage.id
      hasSubmitted.value = true
      toast.pushToast('报名提交成功', 'success')
    } catch (error) {
      toast.pushToast(error instanceof Error ? error.message : '后端提交失败', 'warn')
    } finally {
      submitting.value = false
    }
  }
}

function saveFeedback(): void {
  if (!feedbackChoice.value) {
    toast.pushToast('先选择一个最接近的感受吧', 'warn')
    return
  }
  try {
    localStorage.setItem(
      `diy-campus:activity-feedback:${slug.value}`,
      JSON.stringify({ choice: feedbackChoice.value, note: feedbackNote.value.trim(), at: Date.now() })
    )
    feedbackSaved.value = true
    toast.pushToast('谢谢你的反馈', 'success')
  } catch {
    toast.pushToast('反馈保存失败', 'warn')
  }
}

function downloadCalendar(): void {
  const start = parseDate(document.value?.meta.startAt ?? '')
  if (!start) {
    toast.pushToast('活动还没有填写开始时间', 'warn')
    return
  }
  const end = start + 90 * 60 * 1000
  const stamp = (time: number) => {
    const date = new Date(time)
    const pad = (value: number) => String(value).padStart(2, '0')
    return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}T${pad(date.getHours())}${pad(
      date.getMinutes()
    )}00`
  }
  const escape = (value: string) => value.replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/,/g, '\\,')
  const ics = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//XinYu TongXing//Activity//CN',
    'BEGIN:VEVENT',
    `UID:${slug.value}-${start}@xinyu`,
    `DTSTART:${stamp(start)}`,
    `DTEND:${stamp(end)}`,
    `SUMMARY:${escape(document.value?.title || '心晴活动')}`,
    `LOCATION:${escape(document.value?.meta.place || '')}`,
    `DESCRIPTION:${escape(shareUrl.value)}`,
    'END:VEVENT',
    'END:VCALENDAR',
  ].join('\r\n')
  const link = window.document.createElement('a')
  const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  link.href = url
  link.download = `${document.value?.title || '心晴活动'}.ics`
  link.click()
  URL.revokeObjectURL(url)
}

async function shareActivity(): Promise<void> {
  try {
    if (navigator.share) {
      await navigator.share({ title: document.value?.title ?? '心晴活动', url: shareUrl.value })
      return
    }
    await navigator.clipboard.writeText(shareUrl.value)
    toast.pushToast('活动链接已复制', 'success')
  } catch {
    toast.pushToast('分享已取消', 'info')
  }
}

function goBack(): void {
  if (window.history.length > 1) router.back()
  else void router.push('/projects')
}
</script>

<template>
  <main class="activity-page studio-activity">
    <div class="activity-shell public-document-shell" :style="themeVars">
      <header class="activity-nav public-document-nav">
        <div class="activity-nav-left">
          <button class="activity-back" type="button" aria-label="返回上一页" @click="goBack">←</button>
          <span class="activity-brand"
            ><i><b /><b /></i><span>心屿同行</span></span
          >
        </div>
        <button type="button" aria-label="分享活动" @click="shareActivity">
          <Icon name="external" :size="15" />分享
        </button>
      </header>

      <div v-if="currentPage" class="public-document-page">
        <BlockView
          v-for="block in visibleBlocks"
          :key="block.id"
          :block="block"
          :theme="document?.theme ?? editorStore.doc.theme"
          :pages="pages"
          :share-url="shareUrl"
          mode="preview"
          @action="handleAction"
        />
      </div>

      <div v-else class="public-document-empty">
        <Icon :name="unavailable.icon" :size="28" />
        <strong>{{ unavailable.title }}</strong>
        <p>{{ unavailable.text }}</p>
        <RouterLink to="/projects">返回心晴运营台</RouterLink>
      </div>
    </div>
    <p class="activity-preview-note">
      公开活动 · {{ activityPhase }} ·
      {{
        remoteActivity?.status === 'published'
          ? '后端已发布'
          : project?.status === 'published'
            ? '本地已发布'
            : '本地预览'
      }}
      <span v-if="submitting"> · 提交中...</span>
    </p>
    <aside v-if="privacy.consentRequired" class="activity-consent-panel">
      <div class="consent-heading">
        <span><Icon name="lock" :size="14" />参与前请了解</span>
        <em>{{
          privacy.privacyMode === 'anonymous'
            ? '匿名参与'
            : privacy.privacyMode === 'optional'
              ? '可选匿名'
              : '实名参与'
        }}</em>
      </div>
      <label>
        <input v-model="consentAccepted" type="checkbox" />
        <span>{{ privacy.consentText }}</span>
      </label>
      <div class="consent-tags">
        <span v-if="privacy.allowLeave"><Icon name="check" :size="12" />可随时离开</span>
        <span v-if="privacy.quietSpace"><Icon name="check" :size="12" />设有安静休息区</span>
        <span>
          <Icon name="clock" :size="12" />信息保留 {{ privacy.retentionDays || '长期'
          }}{{ privacy.retentionDays ? ' 天' : '' }}
        </span>
      </div>
    </aside>
    <section v-if="hasSubmitted" class="activity-feedback">
      <div>
        <span>活动后的轻量反馈</span>
        <h3>这次参与，对你来说怎么样？</h3>
        <p>不评价心理状态，只帮助组织者把下一次活动做得更好。</p>
      </div>
      <div class="feedback-choices">
        <button
          v-for="item in [
            { id: 'helpful', label: '有一点帮助' },
            { id: 'okay', label: '感觉还可以' },
            { id: 'not-fit', label: '不太适合我' },
          ]"
          :key="item.id"
          type="button"
          :class="{ 'is-active': feedbackChoice === item.id }"
          @click="feedbackChoice = item.id"
        >
          {{ item.label }}
        </button>
      </div>
      <input v-model="feedbackNote" maxlength="160" placeholder="一句可选建议，不填写也可以" />
      <button class="feedback-save" type="button" @click="saveFeedback">
        <Icon name="check" :size="13" />{{ feedbackSaved ? '已记录' : '留下反馈' }}
      </button>
    </section>
    <aside class="activity-safety-footer">
      <div>
        <Icon name="users" :size="15" />
        <span
          >活动组织方：<strong>{{ organizer }}</strong></span
        >
      </div>
      <p>本页面用于活动组织与参与，不提供心理诊断或治疗建议。</p>
      <button type="button" @click="downloadCalendar"><Icon name="calendar" :size="13" />加入日历</button>
      <RouterLink to="/resources">查看心理资源与求助方向 <Icon name="arrow-right" :size="13" /></RouterLink>
    </aside>
  </main>
</template>

<style scoped>
.activity-consent-panel,
.activity-feedback {
  width: min(760px, calc(100% - 32px));
  margin: 14px auto 0;
  border: 1px solid #dce8df;
  border-radius: 15px;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 14px 35px -34px rgb(32 63 46 / 30%);
}

.activity-consent-panel {
  padding: 14px;
}

.consent-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.consent-heading > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--brand-700);
  font-size: 9px;
  font-weight: 750;
}

.consent-heading em {
  padding: 3px 7px;
  border-radius: 99px;
  color: var(--brand-600);
  background: var(--brand-50);
  font-size: 7px;
  font-style: normal;
}

.activity-consent-panel > label {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: var(--ink-500);
  font-size: 8px;
  line-height: 1.75;
}

.activity-consent-panel input {
  margin-top: 2px;
  accent-color: var(--brand-600);
}

.consent-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 10px;
}

.consent-tags span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 7px;
  border-radius: 999px;
  color: var(--ink-500);
  background: var(--ink-50);
  font-size: 7px;
}

.activity-feedback {
  padding: 16px;
}

.activity-feedback > div:first-child > span {
  color: var(--brand-500);
  font-size: 7px;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.activity-feedback h3 {
  margin-top: 4px;
  font-family: var(--font-serif);
  font-size: 17px;
}

.activity-feedback p {
  margin-top: 5px;
  color: var(--ink-400);
  font-size: 8px;
}

.feedback-choices {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 13px;
}

.feedback-choices button {
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-500);
  background: #fff;
  font-size: 8px;
  cursor: pointer;
}

.feedback-choices button.is-active {
  border-color: var(--brand-300);
  color: var(--brand-700);
  background: var(--brand-50);
}

.activity-feedback > input {
  width: 100%;
  min-height: 34px;
  margin-top: 8px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 8px;
  outline: 0;
  font-size: 8px;
}

.activity-feedback .feedback-save {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
  padding: 0;
  border: 0;
  color: var(--brand-600);
  background: transparent;
  font-size: 8px;
  font-weight: 700;
  cursor: pointer;
}

.activity-safety-footer {
  display: flex;
  width: min(760px, calc(100% - 32px));
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin: 18px auto 28px;
  padding: 11px 14px;
  border: 1px solid #dde8df;
  border-radius: 12px;
  color: #718078;
  background: rgb(255 255 255 / 72%);
  font-size: 8px;
}

.activity-safety-footer > div,
.activity-safety-footer > button,
.activity-safety-footer > a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.activity-safety-footer p {
  flex: 1;
  text-align: center;
}

.activity-safety-footer a {
  color: var(--brand-600);
  font-weight: 700;
}

.activity-safety-footer > button {
  padding: 0;
  border: 0;
  color: var(--brand-600);
  background: transparent;
  font-size: 8px;
  cursor: pointer;
}

@media (max-width: 640px) {
  .activity-safety-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .consent-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .activity-safety-footer p {
    text-align: left;
  }
}
</style>
