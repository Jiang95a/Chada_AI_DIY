<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { useProjectStore } from '@/stores/projects'
import { askAgent, extractJson, PLAN_SYSTEM_EXTRA, probeAgent } from '@/services/agent'
import { buildPlanPrompt, localDoc, normalizePlan, parsePlanFromText, planToDoc } from '@/services/planner'
import type { Doc } from '@/types/editor'

const EXAMPLES = [
  {
    title: '心理社团招新',
    prompt: '心屿心理协会秋季招新，包含社团介绍、活动方向和报名表',
    icon: 'users',
  },
  {
    title: '心晴游园会',
    prompt: '考试周心晴游园会，设置五个解压互动摊位，需要匿名报名',
    icon: 'calendar',
  },
  {
    title: '心理知识问答',
    prompt: '做一次心理健康知识问答，完成后给出科普反馈，不做心理诊断',
    icon: 'check-circle',
  },
  {
    title: '自我关怀打卡',
    prompt: '21 天自我关怀打卡，每天完成一个轻量练习并记录一句感受',
    icon: 'clock',
  },
]

const route = useRoute()
const router = useRouter()
const toast = useToast()
const projectStore = useProjectStore()

const prompt = ref('')
const busy = ref(false)
const step = ref('')
const errorText = ref('')
const status = ref<'unknown' | 'online' | 'offline'>('unknown')
const statusLabel = ref('检测智能体…')
const result = ref<{ doc: Doc; note: string; reason: string; online: boolean } | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

const summary = computed(() => {
  if (!result.value) return null
  const doc = result.value.doc
  return {
    pages: doc.pages.length,
    blocks: doc.pages.reduce((total, page) => total + page.blocks.length, 0),
    title: doc.title,
  }
})

async function refreshStatus(): Promise<void> {
  status.value = 'unknown'
  statusLabel.value = '检测智能体…'
  const health = await probeAgent(2500)
  if (health.ok) {
    status.value = 'online'
    const model = (health.health?.llm as { model?: string } | undefined)?.model
    statusLabel.value = model ? `AI 在线 · ${model}` : 'AI 在线'
  } else {
    status.value = 'offline'
    statusLabel.value = '本地规则可用'
  }
}

function useExample(text: string): void {
  prompt.value = text
  void nextTick(() => textareaRef.value?.focus())
}

function guessType(text: string): string {
  if (/招新|纳新|心理社团/.test(text)) return '心理社团'
  if (/问答|知识|科普/.test(text)) return '心理知识问答'
  if (/打卡|关怀|练习/.test(text)) return '自我关怀'
  if (/游园|团辅|报名|分享|讲座/.test(text)) return '心晴活动'
  return '心理活动'
}

async function generate(): Promise<void> {
  const text = prompt.value.trim()
  if (!text) {
    errorText.value = '请先描述你要办什么活动'
    textareaRef.value?.focus()
    return
  }

  busy.value = true
  errorText.value = ''
  result.value = null
  try {
    if (status.value === 'unknown') await refreshStatus()
    if (status.value === 'online') {
      step.value = '正在规划页面结构与组件…'
      try {
        const reply = await askAgent(buildPlanPrompt(text), { systemExtra: PLAN_SYSTEM_EXTRA })
        const json = extractJson(reply.reply)
        const plan = normalizePlan(json) ?? parsePlanFromText(reply.reply)
        if (plan) {
          const extra = [`${reply.latencyMs}ms`]
          if (reply.ragHits > 0) extra.push(`资料 ${reply.ragHits} 条`)
          if (reply.toolCalls.length) extra.push(reply.toolCalls.map((call) => call.name).join(' / '))
          result.value = {
            doc: planToDoc(plan),
            note: `${json ? '结构化 JSON' : '文本结构解析'} · ${extra.join(' · ')}`,
            reason: plan.reason || '智能体按活动需求规划了页面结构。',
            online: true,
          }
          return
        }
        toast.pushToast('智能体没有返回可解析结构，已使用本地规则', 'warn')
      } catch (error) {
        status.value = 'offline'
        const message = error instanceof Error ? error.message : '智能体调用失败'
        toast.pushToast(`${message}，已切换到本地规则`, 'warn')
      }
    }

    step.value = '正在匹配心晴活动模板…'
    const local = localDoc(text)
    result.value = {
      doc: local.doc,
      note: `本地规则 · ${local.scene}模板`,
      reason: local.reason,
      online: false,
    }
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '页面生成失败，请重试'
  } finally {
    busy.value = false
    step.value = ''
  }
}

async function createFromResult(): Promise<void> {
  if (!result.value || busy.value) return
  busy.value = true
  step.value = '正在创建项目…'
  try {
    const doc = result.value.doc
    const title = doc.title?.trim() || prompt.value.trim().slice(0, 24) || '未命名活动'
    const project = await projectStore.createProjectRemote({
      title,
      type: guessType(prompt.value),
      org: doc.meta.org,
      document: doc,
    })
    toast.pushToast('项目已创建，正在打开编辑器', 'success')
    await router.push(`/editor/${project.id}`)
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '创建项目失败'
  } finally {
    busy.value = false
    step.value = ''
  }
}

onMounted(async () => {
  const queryPrompt = typeof route.query.prompt === 'string' ? route.query.prompt : ''
  if (queryPrompt) prompt.value = queryPrompt
  await refreshStatus()
  await nextTick()
  textareaRef.value?.focus()
})
</script>

<template>
  <WorkspaceShell>
    <main class="ai-page">
      <header class="ai-page-heading">
        <div>
          <p class="eyebrow">AI Activity Builder</p>
          <h1>让 AI 一起构思心晴活动</h1>
          <p>描述活动对象、参与方式和需要收集的信息，AI 会规划页面、组件与安心参与流程。</p>
        </div>
        <div :class="`ai-status is-${status}`"><i />{{ statusLabel }}</div>
      </header>

      <div class="ai-builder-layout">
        <section class="ai-builder-card">
          <header class="ai-builder-intro">
            <span class="ai-builder-icon"><Icon name="spark" :size="22" /></span>
            <div>
              <span>AI WELLBEING CREATOR</span>
              <h2>从一场心晴活动开始</h2>
              <p>可以直接描述需求，也可以先选择一个心理社团常用场景。</p>
            </div>
          </header>

          <div class="ai-section-label">
            <span>心晴场景</span>
            <small>点击后填入示例，不会创建项目</small>
          </div>
          <div class="ai-scene-grid">
            <button v-for="example in EXAMPLES" :key="example.title" type="button" @click="useExample(example.prompt)">
              <span><Icon :name="example.icon" :size="18" /></span>
              <div>
                <strong>{{ example.title }}</strong>
                <small>{{ example.prompt }}</small>
              </div>
              <Icon name="arrow-right" :size="15" />
            </button>
          </div>

          <div class="ai-section-label ai-section-label--composer">
            <span>活动需求</span>
            <small>建议写明活动对象、参与方式、隐私范围和需要收集的信息</small>
          </div>
          <div class="ai-composer">
            <textarea
              ref="textareaRef"
              v-model="prompt"
              rows="5"
              maxlength="1000"
              placeholder="例如：做一个考试周心晴游园会页面，要有活动介绍、摊位安排、匿名报名和保密说明，整体风格温柔、安静…"
              @keydown.ctrl.enter.prevent="generate"
              @keydown.meta.enter.prevent="generate"
            />
            <div class="ai-composer-tools">
              <span><kbd>Ctrl</kbd><kbd>Enter</kbd> 快速生成</span>
              <span>{{ prompt.length }} / 1000</span>
            </div>
            <div class="ai-composer-actions">
              <RouterLink to="/templates"><Icon name="grid" :size="15" />改用心晴模板</RouterLink>
              <button type="button" :disabled="busy" @click="generate">
                <Icon name="spark" :size="17" />{{ busy ? '正在生成…' : '生成心晴活动页' }}
              </button>
            </div>
          </div>

          <p v-if="errorText" class="ai-page-error"><Icon name="alert" :size="14" />{{ errorText }}</p>
          <p v-else-if="step" class="ai-page-step"><Icon name="spark" :size="14" />{{ step }}</p>
        </section>

        <aside class="ai-builder-side">
          <article class="ai-guide-card">
            <span class="ai-guide-kicker">生成流程</span>
            <h2>AI 会准备什么</h2>
            <ol>
              <li>
                <b>01</b>
                <div><strong>规划安心参与流程</strong><small>活动介绍、参与说明、表单和确认页</small></div>
              </li>
              <li>
                <b>02</b>
                <div><strong>匹配合适组件</strong><small>活动信息、保密提示、参与表和按钮</small></div>
              </li>
              <li>
                <b>03</b>
                <div><strong>生成可编辑草稿</strong><small>进入页面工坊继续修改和发布</small></div>
              </li>
            </ol>
          </article>
          <article class="ai-guide-note">
            <Icon name="wand" :size="18" />
            <div>
              <strong>不做心理判断，只帮助组织活动</strong>
              <p>AI 只生成页面与文案建议，不进行心理诊断，也不替组织者判断参与者的心理状态。</p>
            </div>
          </article>
        </aside>
      </div>

      <section v-if="result && summary" class="ai-result">
        <header>
          <div>
            <span>{{ result.online ? 'AI 生成方案' : '本地规则方案' }}</span>
            <h2>{{ summary.title }}</h2>
          </div>
          <em>{{ summary.pages }} 页 · {{ summary.blocks }} 个组件</em>
        </header>
        <p>{{ result.reason }}</p>
        <div class="ai-result-pages">
          <article v-for="(page, index) in result.doc.pages" :key="page.id">
            <b>0{{ index + 1 }}</b>
            <strong>{{ page.name }}</strong>
            <span>{{ page.blocks.length }} 个组件</span>
          </article>
        </div>
        <footer>
          <span>{{ result.note }}</span>
          <button type="button" @click="generate"><Icon name="refresh" :size="14" />换一个方案</button>
          <button class="ai-result-create" type="button" :disabled="busy" @click="createFromResult">
            <Icon name="arrow-right" :size="15" />{{ busy ? '创建中…' : '创建项目并进入编辑器' }}
          </button>
        </footer>
      </section>
    </main>
  </WorkspaceShell>
</template>

<style scoped>
.ai-page {
  width: 100%;
  max-width: 1220px;
  margin: 0 auto;
  padding: 26px 30px 42px;
}

.ai-page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

.ai-page-heading h1 {
  margin: 3px 0 7px;
  color: var(--ink-900, #101828);
  font-size: 24px;
  letter-spacing: -0.03em;
}

.ai-page-heading > div > p:last-child {
  margin: 0;
  color: var(--ink-500, #667085);
  font-size: 12px;
  line-height: 1.7;
}

.eyebrow {
  margin: 0;
  color: var(--ink-400, #98a2b3);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.13em;
  text-transform: uppercase;
}

.ai-status {
  display: inline-flex;
  min-height: 30px;
  align-items: center;
  gap: 7px;
  padding: 0 11px;
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 999px;
  color: var(--ink-500, #667085);
  background: #fff;
  font-size: 10px;
  font-weight: 650;
  white-space: nowrap;
}

.ai-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #98a2b3;
}

.ai-status.is-online i {
  background: #12b76a;
  box-shadow: 0 0 0 4px rgb(18 183 106 / 10%);
}

.ai-status.is-offline i {
  background: #f59e0b;
}

.ai-builder-layout {
  display: grid;
  align-items: start;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr) 286px;
}

.ai-builder-card,
.ai-guide-card,
.ai-guide-note,
.ai-result {
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  background: var(--surface);
  box-shadow: var(--shadow-card);
}

.ai-builder-card {
  padding: 24px;
}

.ai-builder-intro {
  display: flex;
  align-items: center;
  gap: 13px;
  padding-bottom: 19px;
  border-bottom: 1px solid var(--line, #e9ecf3);
}

.ai-builder-icon {
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  border-radius: 13px;
  color: var(--brand-600);
  background: linear-gradient(135deg, var(--brand-100), var(--accent-100));
  place-items: center;
}

.ai-builder-intro > div > span {
  color: var(--brand-600);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.ai-builder-intro h2 {
  margin: 3px 0 4px;
  color: var(--ink-900, #101828);
  font-size: 18px;
}

.ai-builder-intro p {
  color: var(--ink-500, #667085);
  font-size: 11px;
}

.ai-section-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 20px 0 9px;
}

.ai-section-label span {
  color: var(--ink-800, #1e293b);
  font-size: 11px;
  font-weight: 750;
}

.ai-section-label small {
  color: var(--ink-400, #98a2b3);
  font-size: 9px;
}

.ai-section-label--composer {
  margin-top: 23px;
}

.ai-scene-grid {
  display: grid;
  gap: 9px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.ai-scene-grid button {
  display: grid;
  min-height: 72px;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 13px;
  color: var(--ink-800, #1e293b);
  background: #fff;
  text-align: left;
  cursor: pointer;
  grid-template-columns: auto minmax(0, 1fr) auto;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    transform 0.16s ease;
}

.ai-scene-grid button:hover {
  border-color: var(--brand-200);
  background: var(--brand-50);
  transform: translateY(-1px);
}

.ai-scene-grid button > span {
  display: grid;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: var(--brand-600);
  background: var(--brand-50);
  place-items: center;
}

.ai-scene-grid button > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.ai-scene-grid strong {
  font-size: 11px;
}

.ai-scene-grid small {
  overflow: hidden;
  color: var(--ink-400, #98a2b3);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-scene-grid button > svg {
  color: var(--ink-300, #d0d5dd);
}

.ai-composer {
  overflow: hidden;
  border: 1px solid var(--line-strong, #dfe3ee);
  border-radius: var(--radius-card);
  background: var(--surface-raised);
  box-shadow: 0 12px 35px -30px rgb(45 80 70 / 30%);
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}

.ai-composer:focus-within {
  border-color: var(--brand-400);
  box-shadow: 0 0 0 3px rgb(45 80 70 / 8%);
}

.ai-composer textarea {
  width: 100%;
  min-height: 126px;
  padding: 16px 17px 7px;
  border: 0;
  outline: 0;
  color: var(--ink-800, #1e293b);
  background: transparent;
  font: inherit;
  font-size: 12px;
  line-height: 1.8;
  resize: vertical;
}

.ai-composer textarea::placeholder {
  color: var(--ink-400, #98a2b3);
}

.ai-composer-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 16px 11px;
  color: var(--ink-400, #98a2b3);
  font-size: 8px;
}

.ai-composer-tools > span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ai-composer-tools kbd {
  padding: 2px 5px;
  border: 1px solid var(--line-strong, #dfe3ee);
  border-bottom-width: 2px;
  border-radius: 5px;
  color: var(--ink-500, #667085);
  background: var(--ink-50, #f8fafc);
  font-size: 7px;
}

.ai-composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px 12px 16px;
  border-top: 1px solid var(--line, #e9ecf3);
  background: var(--surface-soft);
}

.ai-composer-actions > a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-500, #667085);
  font-size: 9px;
}

.ai-composer-actions > a:hover {
  color: var(--brand-600);
}

.ai-composer-actions > button {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  gap: 7px;
  padding: 0 16px;
  border: 0;
  border-radius: var(--radius-button);
  color: #fff;
  background: var(--brand-600);
  box-shadow: 0 10px 20px -14px rgb(45 80 70 / 65%);
  font-size: 10px;
  font-weight: 750;
  cursor: pointer;
}

.ai-composer-actions > button:hover:not(:disabled) {
  background: var(--brand-700);
}

.ai-composer-actions > button:disabled {
  opacity: 0.55;
  cursor: wait;
}

.ai-page-error,
.ai-page-step {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 12px 0 0;
  padding: 9px 11px;
  border-radius: 9px;
  font-size: 10px;
}

.ai-page-error {
  color: #b42318;
  background: var(--rose-50, #fef2f2);
}

.ai-page-step {
  color: #047857;
  background: var(--mint-50, #ecfdf5);
}

.ai-builder-side {
  display: grid;
  gap: 12px;
}

.ai-guide-card {
  padding: 20px;
}

.ai-guide-kicker {
  color: var(--brand-600);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.ai-guide-card h2 {
  margin: 5px 0 17px;
  color: var(--ink-900, #101828);
  font-size: 15px;
}

.ai-guide-card ol {
  display: grid;
  gap: 15px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-guide-card li {
  display: flex;
  gap: 10px;
}

.ai-guide-card li > b {
  display: grid;
  width: 25px;
  height: 25px;
  flex: 0 0 auto;
  border-radius: 8px;
  color: var(--brand-600);
  background: var(--brand-50);
  font-size: 8px;
  place-items: center;
}

.ai-guide-card li > div {
  display: flex;
  padding-top: 1px;
  flex-direction: column;
  gap: 3px;
}

.ai-guide-card li strong {
  color: var(--ink-800, #1e293b);
  font-size: 10px;
}

.ai-guide-card li small {
  color: var(--ink-400, #98a2b3);
  font-size: 8px;
  line-height: 1.55;
}

.ai-guide-note {
  display: flex;
  gap: 10px;
  padding: 17px;
  color: #087f5b;
  background: linear-gradient(135deg, #f0fdf8, #f7fffc);
}

.ai-guide-note > svg {
  flex: 0 0 auto;
}

.ai-guide-note strong {
  color: #056648;
  font-size: 10px;
}

.ai-guide-note p {
  margin-top: 5px;
  color: #41836f;
  font-size: 8px;
  line-height: 1.65;
}

.ai-result {
  margin-top: 16px;
  padding: 20px;
}

.ai-result > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.ai-result > header span {
  color: var(--brand-600);
  font-size: 8px;
  font-weight: 800;
}

.ai-result h2 {
  margin: 5px 0 0;
  color: var(--ink-900, #101828);
  font-size: 17px;
}

.ai-result > header em {
  color: var(--ink-400, #98a2b3);
  font-size: 9px;
  font-style: normal;
}

.ai-result > p {
  margin: 10px 0 14px;
  color: var(--ink-500, #667085);
  font-size: 10px;
  line-height: 1.7;
}

.ai-result-pages {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.ai-result-pages article {
  display: flex;
  min-width: 0;
  padding: 11px;
  border: 1px solid var(--line, #e9ecf3);
  border-radius: 10px;
  background: var(--ink-50, #f8fafc);
  flex-direction: column;
}

.ai-result-pages b {
  color: var(--ink-300, #d0d5dd);
  font-size: 8px;
}

.ai-result-pages strong {
  margin-top: 7px;
  overflow: hidden;
  color: var(--ink-800, #1e293b);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-result-pages span {
  margin-top: 3px;
  color: var(--ink-400, #98a2b3);
  font-size: 8px;
}

.ai-result > footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 15px;
  padding-top: 14px;
  border-top: 1px solid var(--line, #e9ecf3);
}

.ai-result > footer > span {
  flex: 1;
  color: var(--ink-400, #98a2b3);
  font-size: 8px;
}

.ai-result > footer button {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 6px;
  padding: 0 11px;
  border: 1px solid var(--line-strong, #dfe3ee);
  border-radius: 9px;
  color: var(--ink-600, #475467);
  background: #fff;
  font-size: 9px;
  cursor: pointer;
}

.ai-result > footer .ai-result-create {
  border: 0;
  color: #fff;
  background: var(--brand-600);
  font-weight: 750;
}

@media (max-width: 1050px) {
  .ai-builder-layout {
    grid-template-columns: 1fr;
  }

  .ai-builder-side {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .ai-page {
    padding: 20px 16px 32px;
  }

  .ai-page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .ai-builder-card {
    padding: 18px;
  }

  .ai-scene-grid,
  .ai-result-pages,
  .ai-builder-side {
    grid-template-columns: 1fr;
  }

  .ai-section-label {
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;
  }

  .ai-composer-actions,
  .ai-result > footer {
    align-items: stretch;
    flex-direction: column;
  }

  .ai-composer-actions > button {
    justify-content: center;
  }

  .ai-result > footer > span {
    margin-bottom: 2px;
  }
}
</style>
