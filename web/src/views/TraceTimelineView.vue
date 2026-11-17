<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import TracePulseCard from '@/components/public/TracePulseCard.vue'
import { useToast } from '@/composables/useToast'
import { useTraceStore } from '@/stores/trace'
import type { ParticipantTrace } from '@/types/trace'
import { fetchInsightFacts, analyzeInsight } from '@/services/insights'
import { getAccessToken } from '@/services/http'
import type { InsightAnalyzeResponse, InsightFacts } from '@/types/insight'

/**
 * 我的轨迹：只显示用户确认过的摘要、标签变化和未决问题。
 * 默认不显示流水；数据只在本机（登录后由 store 尝试同步）。
 */

const traceStore = useTraceStore()
const toast = useToast()
const items = ref<ParticipantTrace[]>([])
const facts = ref<InsightFacts | null>(null)
const changesOnly = ref(false)
const loading = ref(false)

/* ------------------------------ 让 AI 回看 ------------------------------ */
const analyzing = ref(false)
const analysis = ref<InsightAnalyzeResponse | null>(null)
const analysisFailed = ref(false)

const loggedIn = computed(() => Boolean(getAccessToken()))

/**
 * 让 AI 回看这段时间。
 *
 * 服务端对这四种结局都返回 200，所以这里不靠 HTTP 状态码分支，
 * 一律看 `analysis.status`：ok / insufficient_data / crisis / analysis_error。
 * 只有网络层失败（连不上、401）才走 analysisFailed。
 */
async function runAnalyze(): Promise<void> {
  if (analyzing.value) return
  if (!loggedIn.value) {
    toast.pushToast('登录后可以跨设备回看', 'warn')
    return
  }
  analyzing.value = true
  analysisFailed.value = false
  try {
    analysis.value = await analyzeInsight(30, { allowQuote: true })
  } catch {
    analysisFailed.value = true
  } finally {
    analyzing.value = false
  }
}

/** 复制回看正文。回看卡不走图片模型——中文交给图片模型必然糊，这里直接给纯文本。 */
async function copyReview(): Promise<void> {
  const body = analysis.value?.insight
  if (!body) return
  const lines: string[] = [body.title]
  for (const finding of body.findings) {
    lines.push(`· ${finding.text}`)
    for (const evidence of finding.evidence) {
      lines.push(`  ${evidence.at} · ${evidence.activityTitle}${evidence.quote ? `：${evidence.quote}` : ''}`)
    }
  }
  if (body.change) lines.push(`变化：${body.change}`)
  lines.push(`下一步：${body.nextStep}`)
  lines.push(`问题：${body.question}`)
  lines.push(body.limits)
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    toast.pushToast('已复制这段回看')
  } catch {
    toast.pushToast('复制失败，可以手动选中文字', 'warn')
  }
}

const filtered = computed(() => (changesOnly.value ? items.value.filter((item) => Boolean(item.change)) : items.value))
const topFactTags = computed(() =>
  Object.entries(facts.value?.tagCounts ?? {})
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
)
const factCount = computed(() => {
  const value = facts.value
  if (!value) return 0
  return value.activityCount + value.formCount + (value.toolRecordCount ?? 0)
})

function refresh(): void {
  items.value = traceStore.list()
}

function dateLabel(at: number): string {
  const date = new Date(at)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日 ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function domainLabel(trace: ParticipantTrace): string {
  return trace.domain === 'career' ? '职规活动' : '心晴活动'
}

function removeItem(activityId: string): void {
  traceStore.remove(activityId)
  refresh()
  toast.pushToast('这条轨迹已从这台设备删除', 'success')
}

function clearAll(): void {
  if (!window.confirm('清除这台设备上的全部轨迹？这个操作不能撤销。')) return
  traceStore.clearAll()
  refresh()
  toast.pushToast('这台设备上的轨迹已清空', 'success')
}

onMounted(async () => {
  loading.value = true
  try {
    await traceStore.syncAll()
    if (getAccessToken()) {
      facts.value = await fetchInsightFacts(30)
    }
  } finally {
    refresh()
    loading.value = false
  }
})
</script>

<template>
  <main class="trace-page">
    <section class="trace-hero">
      <span class="trace-kicker">MY TRACE · 我的轨迹</span>
      <h1>你是怎么想过来的</h1>
      <p>
        这里只有你自己留下的摘要、标签变化和还没回答的问题。默认不显示流水；没有选择「存进我的轨迹」的匿名便签不会出现在这里。
      </p>
      <div class="trace-hero-actions">
        <RouterLink to="/campus">去看活动 <Icon name="arrow-right" :size="14" /></RouterLink>
        <button v-if="items.length" type="button" :class="{ 'is-on': changesOnly }" @click="changesOnly = !changesOnly">
          {{ changesOnly ? '看全部' : '只看变过主意的' }}
        </button>
      </div>
    </section>

    <TracePulseCard v-if="items.length" :traces="items" :show-link="false" class="trace-pulse" />

    <section v-if="facts && factCount" class="trace-facts" aria-label="回看数据底稿">
      <div>
        <p class="trace-kicker">PERIOD FACTS · 最近 30 天</p>
        <h2>这段时间留下的底稿</h2>
        <p>
          {{ facts.activityCount }} 场活动 · {{ facts.formCount }} 次表单 ·
          {{ facts.toolRecordCount ?? 0 }} 条工具记录
        </p>
      </div>
      <div v-if="topFactTags.length" class="trace-fact-tags">
        <span v-for="[tag, count] in topFactTags" :key="tag">{{ tag }} <b>{{ count }}</b></span>
      </div>
    </section>

    <!-- 让 AI 回看：入口状态严格按产品文档（未登录 / 0 条 / 1-2 条 / 正常） -->
    <section v-if="items.length || facts" class="trace-review" aria-label="让 AI 回看">
      <div class="trace-review-head">
        <div>
          <p class="trace-kicker">REVIEW · 让 AI 回看</p>
          <h2>把这段时间读一遍</h2>
          <p class="trace-review-hint">只看你留下的记录，不做评估，也不替你下结论。</p>
        </div>
        <button v-if="!loggedIn" type="button" class="trace-review-btn" disabled>
          登录后可跨设备回看
        </button>
        <button v-else-if="!factCount" type="button" class="trace-review-btn" disabled>
          先留下一条记录
        </button>
        <button v-else type="button" class="trace-review-btn" :disabled="analyzing" @click="runAnalyze">
          {{ analyzing ? '正在回看…' : '让 AI 回看这段时间' }}
        </button>
      </div>

      <!-- ok：回看正文 -->
      <article v-if="analysis && analysis.status === 'ok' && analysis.insight" class="trace-review-card">
        <h3>{{ analysis.insight.title }}</h3>
        <ul class="trace-review-findings">
          <li v-for="(finding, index) in analysis.insight.findings" :key="index">
            <p>{{ finding.text }}</p>
            <div class="trace-review-evidence">
              <span v-for="(evidence, eIndex) in finding.evidence" :key="eIndex">
                {{ evidence.at }} · {{ evidence.activityTitle }}
              </span>
            </div>
          </li>
        </ul>
        <dl class="trace-review-meta">
          <template v-if="analysis.insight.change">
            <dt>这段时间的变化</dt>
            <dd>{{ analysis.insight.change }}</dd>
          </template>
          <dt>下一步</dt>
          <dd>{{ analysis.insight.nextStep }}</dd>
          <dt>留个问题</dt>
          <dd>{{ analysis.insight.question }}</dd>
        </dl>
        <p class="trace-review-limits">{{ analysis.insight.limits }}</p>
        <div class="trace-review-actions">
          <button type="button" @click="copyReview">复制这段回看</button>
        </div>
      </article>

      <!-- insufficient_data -->
      <article v-else-if="analysis && analysis.status === 'insufficient_data'" class="trace-review-note">
        <p>{{ analysis.message }}</p>
      </article>

      <!-- crisis：只给资源，不给任何分析 -->
      <article v-else-if="analysis && analysis.status === 'crisis'" class="trace-review-note is-crisis">
        <p>{{ analysis.message }}</p>
        <ul>
          <li v-for="resource in analysis.resources ?? []" :key="resource.label">
            <b>{{ resource.label }}</b> {{ resource.value }}
          </li>
        </ul>
      </article>

      <!-- analysis_error -->
      <article v-else-if="analysis && analysis.status === 'analysis_error'" class="trace-review-note">
        <p>{{ analysis.message }}</p>
        <button type="button" @click="runAnalyze">再试一次</button>
      </article>

      <!-- 网络层失败（连不上 / 401） -->
      <article v-else-if="analysisFailed" class="trace-review-note">
        <p>这次没连上服务，稍后再试一次。你的记录都还在。</p>
        <button type="button" @click="runAnalyze">再试一次</button>
      </article>
    </section>

    <section v-if="loading && !items.length" class="trace-empty">
      <Icon name="refresh" :size="28" />
      <strong>正在同步你的轨迹</strong>
      <p>如果你登录过，其它设备上留下的记录会在这里出现。</p>
    </section>

    <section v-else-if="!items.length" class="trace-empty">
      <Icon name="layers" :size="28" />
      <strong>这里还没有东西</strong>
      <p>参加一场活动，写下一句话，选择「存进我的轨迹」之后，它会出现在这里。</p>
      <RouterLink to="/campus">找一场活动 <Icon name="arrow-right" :size="14" /></RouterLink>
    </section>

    <section v-else class="trace-list">
      <div class="trace-list-head">
        <h2>{{ changesOnly ? '你改过主意的地方' : '按时间留下的话' }}</h2>
        <span>{{ filtered.length }} 条</span>
      </div>

      <p v-if="!filtered.length" class="trace-none">还没有「改主意」的记录。不是每场活动都要改变什么。</p>

      <article v-for="item in filtered" :key="item.activityId" class="trace-item">
        <header>
          <time>{{ dateLabel(item.updatedAt) }}</time>
          <span>{{ domainLabel(item) }}</span>
          <button type="button" aria-label="删除这条轨迹" @click="removeItem(item.activityId)">删除</button>
        </header>

        <p class="trace-summary">{{ item.summary || '你还没给这段时间留下摘要。' }}</p>

        <div v-if="item.tags.length" class="trace-tags">
          <span v-for="tag in item.tags" :key="`${item.activityId}-${tag.content}`">{{ tag.content }}</span>
        </div>

        <div v-if="item.notes?.length" class="trace-notes">
          <span>你主动留下的话</span>
          <p v-for="note in item.notes" :key="note.candidateTraceId ?? note.at">{{ note.text }}</p>
        </div>

        <dl class="trace-meta">
          <template v-if="item.change">
            <dt>你变过一次</dt>
            <dd>{{ item.change }}</dd>
          </template>
          <template v-if="item.openQuestion">
            <dt>还没回答</dt>
            <dd>{{ item.openQuestion }}</dd>
          </template>
          <template v-if="item.promise">
            <dt>你答应过</dt>
            <dd>{{ item.promise }}</dd>
          </template>
        </dl>

        <RouterLink :to="`/p/${item.activityId}`" class="trace-back">
          回到这场活动 <Icon name="arrow-right" :size="13" />
        </RouterLink>
      </article>

      <button type="button" class="trace-clear" @click="clearAll">
        <Icon name="trash" :size="13" />清除这台设备上的全部轨迹
      </button>
    </section>
  </main>
</template>

<style scoped>
.trace-page {
  width: min(1120px, calc(100% - 36px));
  margin: 0 auto;
  padding: 34px 0 48px;
}

.trace-hero {
  padding: 30px 0 26px;
  border-bottom: 1px solid var(--line);
}

.trace-pulse {
  margin-top: 22px;
}

.trace-facts {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  margin-top: 20px;
  padding: 18px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.trace-facts h2 {
  margin: 5px 0 4px;
  color: var(--ink-900);
  font-family: var(--font);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.trace-facts p:last-child {
  color: var(--ink-500);
  font-size: 12px;
}

.trace-fact-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.trace-fact-tags span {
  padding: 4px 8px;
  border: 1px solid var(--line);
  color: var(--ink-600);
  background: var(--surface);
  font-size: 11px;
}

.trace-fact-tags b {
  margin-left: 3px;
  color: var(--brand-600);
  font-weight: 700;
}

.trace-kicker {
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.trace-hero h1 {
  margin: 12px 0 10px;
  font-family: var(--font-serif);
  font-size: clamp(30px, 4vw, 44px);
  line-height: 1.2;
  color: var(--ink-900);
}

.trace-hero p {
  max-width: 760px;
  margin: 0;
  font-size: 14.5px;
  line-height: 1.85;
  color: var(--ink-600);
}

.trace-hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.trace-hero-actions a,
.trace-hero-actions button,
.trace-empty a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-button);
  background: #fff;
  color: var(--ink-700);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
}

.trace-hero-actions a,
.trace-empty a {
  border-color: var(--brand-600);
  background: var(--brand-600);
  color: #fff;
}

.trace-hero-actions button.is-on {
  border-color: var(--brand-400);
  background: var(--brand-50);
  color: var(--brand-700);
}

.trace-empty {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
  margin-top: 26px;
  padding: 34px;
  border: 1px dashed var(--line-strong);
  border-radius: 10px;
  background: #fff;
  color: var(--ink-500);
}

.trace-empty strong {
  margin-top: 4px;
  color: var(--ink-900);
  font-size: 18px;
}

.trace-empty p {
  max-width: 480px;
  margin: 0 0 8px;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--ink-600);
}

.trace-list {
  margin-top: 24px;
}

.trace-list-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.trace-list-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 800;
  color: var(--ink-900);
}

.trace-list-head span {
  color: var(--ink-500);
  font-size: 12px;
}

.trace-none {
  padding: 18px;
  border-radius: 8px;
  background: var(--surface-soft);
  color: var(--ink-600);
  font-size: 13px;
}

.trace-item {
  margin-bottom: 12px;
  padding: 18px 20px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
}

.trace-item header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.trace-item time {
  color: var(--ink-900);
  font-size: 12.5px;
  font-weight: 700;
}

.trace-item header > span {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 11px;
  font-weight: 600;
}

.trace-item header > button {
  margin-left: auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-400);
  font-size: 11.5px;
  cursor: pointer;
}

.trace-item header > button:hover {
  color: var(--danger);
  text-decoration: underline;
}

.trace-summary {
  max-width: 860px;
  margin: 12px 0 0;
  font-size: 15.5px;
  line-height: 1.75;
  color: var(--ink-900);
}

.trace-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.trace-tags span {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--ink-50);
  color: var(--ink-600);
  font-size: 11.5px;
}

.trace-notes {
  margin-top: 11px;
  padding: 10px 12px;
  border-left: 3px solid var(--brand-300);
  border-radius: 0 6px 6px 0;
  background: var(--brand-50);
}

.trace-notes > span {
  color: var(--brand-700);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.trace-notes p {
  margin: 5px 0 0;
  color: var(--ink-800);
  font-size: 13px;
  line-height: 1.7;
}

.trace-meta {
  display: grid;
  grid-template-columns: 86px 1fr;
  gap: 6px 12px;
  margin: 13px 0 0;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.trace-meta dt {
  color: var(--ink-500);
  font-size: 12px;
}

.trace-meta dd {
  margin: 0;
  color: var(--ink-700);
  font-size: 12.5px;
  line-height: 1.7;
}

.trace-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 14px;
  color: var(--brand-700);
  font-size: 12.5px;
  font-weight: 700;
  text-decoration: none;
}

.trace-back:hover {
  text-decoration: underline;
}

.trace-clear {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 18px;
  padding: 8px 12px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-button);
  background: transparent;
  color: var(--ink-600);
  font-size: 12px;
  cursor: pointer;
}

.trace-clear:hover {
  border-color: var(--danger);
  color: var(--danger);
}

@media (max-width: 640px) {
  .trace-page {
    padding-top: 22px;
  }

  .trace-facts {
    display: block;
  }

  .trace-fact-tags {
    justify-content: flex-start;
    margin-top: 12px;
  }

  .trace-meta {
    grid-template-columns: 1fr;
    gap: 3px;
  }

  .trace-meta dd + dt {
    margin-top: 8px;
  }
}
</style>

<!-- 让 AI 回看。单独一段是为了不改上面那坨既有样式，
     出问题时也能一眼定位是「新加的那块」。 -->
<style scoped>
.trace-review {
  margin-top: 28px;
  padding: 22px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  background: #fff;
}

.trace-review-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
}

.trace-review-head h2 {
  font-size: 18px;
  margin: 4px 0 6px;
}

.trace-review-hint {
  margin: 0;
  font-size: 13px;
  color: #667085;
}

.trace-review-btn {
  padding: 9px 16px;
  border-radius: 999px;
  border: 1px solid #bfd1ff;
  background: #eef3ff;
  color: #2542a8;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.trace-review-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.trace-review-card,
.trace-review-note {
  margin-top: 18px;
  padding: 18px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.trace-review-card h3 {
  margin: 0 0 12px;
  font-size: 16px;
  line-height: 1.6;
}

.trace-review-findings {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.trace-review-findings p {
  margin: 0 0 6px;
  line-height: 1.75;
}

.trace-review-evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.trace-review-evidence span {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
}

.trace-review-meta {
  margin: 16px 0 0;
}

.trace-review-meta dt {
  margin-top: 10px;
  font-size: 12px;
  color: #667085;
}

.trace-review-meta dd {
  margin: 2px 0 0;
  line-height: 1.75;
}

.trace-review-limits {
  margin: 14px 0 0;
  font-size: 12px;
  color: #98a2b3;
}

.trace-review-actions {
  margin-top: 12px;
}

.trace-review-actions button,
.trace-review-note button {
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #fff;
  cursor: pointer;
}

.trace-review-note.is-crisis {
  background: #fff7ed;
  border-color: #fed7aa;
}

.trace-review-note ul {
  margin: 10px 0 0;
  padding-left: 18px;
  line-height: 1.9;
}
</style>
