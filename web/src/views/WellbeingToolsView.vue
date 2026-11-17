<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

type ToolName = 'dilemma' | 'thought'

interface ToolRecord {
  type: string
  title: string
  meta: string
  time: string
}

const TRACE_KEY = 'diy-campus:tools-trace:v1'
const activeTool = ref<ToolName>('dilemma')
const leaving = ref(false)
const entrance = ref(false)
const clearing = ref(false)
const toastVisible = ref(false)
const toastMessage = ref('')
const traces = ref<ToolRecord[]>([])
const traceTarget = ref<HTMLElement | null>(null)
const traceRail = ref<HTMLElement | null>(null)

const dilemma = reactive({
  aWant: '',
  aWorry: '',
  bWant: '',
  bWorry: '',
  nonneg: '',
})
const thought = reactive({ fact: '', thought: '', feeling: '' })

const aComplete = computed(() => Boolean(dilemma.aWant.trim() && dilemma.aWorry.trim()))
const bComplete = computed(() => Boolean(dilemma.bWant.trim() && dilemma.bWorry.trim()))
const nonnegComplete = computed(() => Boolean(dilemma.nonneg.trim()))
const thoughtFilled = computed(() => [
  Boolean(thought.fact.trim()),
  Boolean(thought.thought.trim()),
  Boolean(thought.feeling.trim()),
])
const dilemmaStyle = computed(() => {
  const a = dilemma.aWant.length + dilemma.aWorry.length
  const b = dilemma.bWant.length + dilemma.bWorry.length
  const total = a + b
  const ratio = total ? a / total : 0.5
  return {
    '--a-weight': `${(18 + ratio * 44).toFixed(1)}%`,
    '--b-weight': `${(18 + (1 - ratio) * 44).toFixed(1)}%`,
  }
})
const balanceCaption = computed(() =>
  dilemma.aWant || dilemma.aWorry || dilemma.bWant || dilemma.bWorry
    ? '只是示意图，不计分'
    : '写一点，两边的分量会慢慢显出来'
)

let toastTimer: number | undefined
let entranceTimer: number | undefined

function readTraces(): ToolRecord[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(TRACE_KEY) || '[]') as unknown
    return Array.isArray(parsed) ? (parsed.slice(0, 6) as ToolRecord[]) : []
  } catch {
    return []
  }
}

function persistTraces(): void {
  try {
    localStorage.setItem(TRACE_KEY, JSON.stringify(traces.value.slice(0, 6)))
  } catch {
    // 本地存储不可用时只影响刷新后保留。
  }
}

function showToast(message: string): void {
  window.clearTimeout(toastTimer)
  toastMessage.value = message
  toastVisible.value = true
  toastTimer = window.setTimeout(() => {
    toastVisible.value = false
  }, 2400)
}

function triggerEntrance(): void {
  entrance.value = false
  window.clearTimeout(entranceTimer)
  void nextTick(() => {
    entrance.value = true
    entranceTimer = window.setTimeout(() => {
      entrance.value = false
    }, 720)
  })
}

function switchTool(name: ToolName): void {
  if (name === activeTool.value || leaving.value) return
  leaving.value = true
  window.setTimeout(() => {
    activeTool.value = name
    leaving.value = false
    triggerEntrance()
  }, 160)
}

function addTrace(record: ToolRecord): void {
  traces.value = [record, ...traces.value].slice(0, 6)
  persistTraces()
}

function setTargetReceiving(): void {
  traceTarget.value?.classList.add('is-receiving')
  window.setTimeout(() => traceTarget.value?.classList.remove('is-receiving'), 460)
}

function flyToTrace(button: HTMLElement, record: ToolRecord): void {
  const source = button.getBoundingClientRect()
  const target = traceTarget.value?.getBoundingClientRect()
  const fallback = traceRail.value?.querySelector('.rail-head')?.getBoundingClientRect()
  const targetRect = target && target.width ? target : fallback
  if (!targetRect) return

  const ghost = document.createElement('div')
  ghost.className = 'save-ghost'
  const title = document.createElement('strong')
  title.textContent = record.title
  const meta = document.createElement('small')
  meta.textContent = record.type
  ghost.append(title, meta)

  const startX = source.left + source.width / 2 - 66
  const startY = source.top + source.height / 2 - 26
  const flyX = targetRect.left + targetRect.width / 2 - startX - 66
  const flyY = targetRect.top + targetRect.height / 2 - startY - 26

  ghost.style.left = `${startX}px`
  ghost.style.top = `${startY}px`
  ghost.style.setProperty('--fly-x', `${flyX}px`)
  ghost.style.setProperty('--fly-y', `${flyY}px`)
  document.body.append(ghost)
  window.setTimeout(() => ghost.remove(), 760)
  setTargetReceiving()
}

function saveTool(name: ToolName, event: MouseEvent): void {
  const button = event.currentTarget
  if (!(button instanceof HTMLElement)) return
  const isDilemma = name === 'dilemma'
  const hasContent = isDilemma
    ? Boolean(dilemma.aWant || dilemma.aWorry || dilemma.bWant || dilemma.bWorry || dilemma.nonneg)
    : Boolean(thought.fact || thought.thought || thought.feeling)

  if (!hasContent) {
    showToast('先写一点，再保存')
    document.querySelector<HTMLTextAreaElement>('.tool-panel.is-active textarea')?.focus()
    return
  }

  let record: ToolRecord
  const savedAt = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (isDilemma) {
    const a = dilemma.aWant.trim()
    const b = dilemma.bWant.trim()
    record = {
      type: '两难梳理',
      title: a || b ? `${a.slice(0, 12)} / ${b.slice(0, 12)}` : '两难梳理',
      meta: dilemma.nonneg.trim().slice(0, 42) || '两边都写了一点，还没写到最不能接受的地方。',
      time: savedAt,
    }
  } else {
    record = {
      type: '想法捕捉',
      title: (thought.thought || thought.fact || '三栏记录').slice(0, 18),
      meta: thought.feeling.trim() ? `感受：${thought.feeling.trim().slice(0, 36)}` : '事实、想法和感受已经分开。',
      time: savedAt,
    }
  }

  addTrace(record)
  flyToTrace(button, record)
  showToast(isDilemma ? '已收进我的轨迹' : '已归档到我的轨迹')
}

function resetForms(): void {
  dilemma.aWant = ''
  dilemma.aWorry = ''
  dilemma.bWant = ''
  dilemma.bWorry = ''
  dilemma.nonneg = ''
  thought.fact = ''
  thought.thought = ''
  thought.feeling = ''
}

function clearTool(): void {
  clearing.value = true
  window.setTimeout(() => {
    resetForms()
    clearing.value = false
    triggerEntrance()
    showToast('画布已清空')
  }, 290)
}

onMounted(() => {
  traces.value = readTraces()
  triggerEntrance()
})

onBeforeUnmount(() => {
  window.clearTimeout(toastTimer)
  window.clearTimeout(entranceTimer)
})
</script>

<template>
  <div class="tools-page">
    <div class="page">
      <section class="intro">
        <p class="eyebrow">TOOLS FOR THINKING</p>
        <h1>把卡住的地方，<br /><span>摊开来看。</span></h1>
        <p class="lede">
          不用马上做决定。先把两边的分量写下来，或者把事实、想法和感受分开。写下来的东西，只有你自己看得见。
        </p>
        <div class="tool-switch" role="tablist" aria-label="选择工具">
          <button
            id="tools-dilemma-tab"
            class="tool-tab"
            :class="{ 'is-active': activeTool === 'dilemma' }"
            type="button"
            role="tab"
            :aria-selected="activeTool === 'dilemma'"
            aria-controls="panel-dilemma"
            @click="switchTool('dilemma')"
          >
            <span>01</span>
            <strong>两难梳理</strong>
            <small>左右对比</small>
          </button>
          <button
            id="tools-thought-tab"
            class="tool-tab"
            :class="{ 'is-active': activeTool === 'thought' }"
            type="button"
            role="tab"
            :aria-selected="activeTool === 'thought'"
            aria-controls="panel-thought"
            @click="switchTool('thought')"
          >
            <span>02</span>
            <strong>想法捕捉器</strong>
            <small>事实 / 想法 / 感受</small>
          </button>
        </div>
      </section>

      <div class="workspace">
        <div class="tool-area">
          <section
            id="panel-dilemma"
            v-show="activeTool === 'dilemma'"
            class="tool-panel"
            :class="{
              'is-active': activeTool === 'dilemma',
              'is-leaving': leaving && activeTool === 'dilemma',
              'is-clearing': clearing,
              'is-entering': entrance,
            }"
            role="tabpanel"
            aria-labelledby="tools-dilemma-tab"
            :style="dilemmaStyle"
          >
            <header class="panel-head">
              <div>
                <p class="panel-index">01 / DILEMMA CANVAS</p>
                <h2>两难梳理画布</h2>
              </div>
              <p class="panel-note">约 5 分钟 · 不是打分，也不替你做决定</p>
            </header>
            <p class="panel-intro">先把 A 和 B 各自的“想要”和“担心”写完整，再回到最下面那一句。</p>

            <div class="balance-zone" aria-hidden="true">
              <span class="balance-side">A</span>
              <div class="balance-line">
                <span class="balance-block balance-block--a"></span>
                <span class="balance-mark"></span>
                <span class="balance-block balance-block--b"></span>
              </div>
              <span class="balance-side">B</span>
              <small class="balance-caption">{{ balanceCaption }}</small>
            </div>

            <div class="dilemma-grid" :class="{ 'is-entering': entrance }">
              <article class="choice-card" :class="{ 'is-complete': aComplete }">
                <header>
                  <span class="choice-letter">A</span>
                  <div><strong>先选这个</strong><small>选择 A</small></div>
                  <span class="complete-dot" aria-hidden="true"></span>
                </header>
                <label class="field">
                  <span>它吸引我的是</span>
                  <textarea
                    v-model="dilemma.aWant"
                    rows="3"
                    maxlength="240"
                    placeholder="比如：离家近、节奏稳、爸妈会放心…"
                  ></textarea>
                </label>
                <label class="field">
                  <span>我担心的是</span>
                  <textarea
                    v-model="dilemma.aWorry"
                    rows="3"
                    maxlength="240"
                    placeholder="比如：我会不会后悔、是不是把自己关小了…"
                  ></textarea>
                </label>
                <p class="card-state">{{ aComplete ? '这一栏写完了，可以看看另一边' : '写完后，这一栏会亮一下' }}</p>
              </article>

              <article class="choice-card" :class="{ 'is-complete': bComplete }">
                <header>
                  <span class="choice-letter">B</span>
                  <div><strong>先选那个</strong><small>选择 B</small></div>
                  <span class="complete-dot" aria-hidden="true"></span>
                </header>
                <label class="field">
                  <span>它吸引我的是</span>
                  <textarea
                    v-model="dilemma.bWant"
                    rows="3"
                    maxlength="240"
                    placeholder="比如：更像我想成为的人、机会更多…"
                  ></textarea>
                </label>
                <label class="field">
                  <span>我担心的是</span>
                  <textarea
                    v-model="dilemma.bWorry"
                    rows="3"
                    maxlength="240"
                    placeholder="比如：不稳定、会让家里失望…"
                  ></textarea>
                </label>
                <p class="card-state">{{ bComplete ? '这一栏写完了，可以看看另一边' : '写完后，这一栏会亮一下' }}</p>
              </article>
            </div>

            <div class="nonnegotiables" :class="{ 'is-complete': nonnegComplete, 'is-entering': entrance }">
              <label class="field field--wide">
                <span>如果只能选一个，我最不能接受的是…</span>
                <textarea
                  v-model="dilemma.nonneg"
                  rows="3"
                  maxlength="300"
                  placeholder="不是“哪个更好”，而是“哪一种后悔我更承受不了”。"
                ></textarea>
              </label>
            </div>

            <footer class="panel-actions">
              <p class="action-hint">写不完也没关系。先保存，之后回来接着写。</p>
              <div class="action-buttons">
                <button class="btn btn--ghost" type="button" @click="clearTool">清空画布</button>
                <button class="btn btn--primary" type="button" @click="saveTool('dilemma', $event)">
                  保存到我的轨迹
                  <span class="btn-arrow" aria-hidden="true">→</span>
                </button>
              </div>
            </footer>
          </section>

          <section
            id="panel-thought"
            v-show="activeTool === 'thought'"
            class="tool-panel"
            :class="{
              'is-active': activeTool === 'thought',
              'is-leaving': leaving && activeTool === 'thought',
              'is-clearing': clearing,
              'is-entering': entrance,
            }"
            role="tabpanel"
            aria-labelledby="tools-thought-tab"
          >
            <header class="panel-head">
              <div>
                <p class="panel-index">02 / THOUGHT CATCHER</p>
                <h2>想法捕捉器</h2>
              </div>
              <p class="panel-note">约 4 分钟 · 只做觉察，不急着纠正</p>
            </header>
            <p class="panel-intro">把发生过的事、脑子里的解释、身体里的感受，分别放在三栏里。</p>

            <div class="flow-map" aria-hidden="true">
              <span class="flow-node" :class="{ 'is-filled': thoughtFilled[0] }"><i></i>事实</span>
              <span class="flow-link" :class="{ 'is-on': thoughtFilled[0] && thoughtFilled[1] }"><i></i></span>
              <span class="flow-node" :class="{ 'is-filled': thoughtFilled[1] }"><i></i>想法</span>
              <span class="flow-link" :class="{ 'is-on': thoughtFilled[1] && thoughtFilled[2] }"><i></i></span>
              <span class="flow-node" :class="{ 'is-filled': thoughtFilled[2] }"><i></i>感受</span>
            </div>

            <div class="thought-grid">
              <label class="thought-col" :class="{ 'is-filled': thoughtFilled[0] }">
                <header><span>01</span><strong>事实</strong><small>发生了什么</small></header>
                <textarea
                  v-model="thought.fact"
                  rows="7"
                  maxlength="400"
                  placeholder="只写能回放的具体片段。比如：周三小组讨论，我的提案没有被接住。"
                ></textarea>
                <p>先不写“他总是”“我永远”，只写这一次。</p>
              </label>
              <label class="thought-col" :class="{ 'is-filled': thoughtFilled[1] }">
                <header><span>02</span><strong>想法</strong><small>脑子里的解释</small></header>
                <textarea
                  v-model="thought.thought"
                  rows="7"
                  maxlength="400"
                  placeholder="比如：他们觉得我没有能力，我不适合做这件事。"
                ></textarea>
                <p>把想法写成一句话，看看它是不是全部事实。</p>
              </label>
              <label class="thought-col" :class="{ 'is-filled': thoughtFilled[2] }">
                <header><span>03</span><strong>感受</strong><small>身体和情绪</small></header>
                <textarea
                  v-model="thought.feeling"
                  rows="7"
                  maxlength="400"
                  placeholder="比如：脸有点热，胸口紧，想马上离开。"
                ></textarea>
                <p>感受不是需要被修好的问题，它只是信息。</p>
              </label>
            </div>

            <aside class="thought-pause">
              <span aria-hidden="true">“</span>
              <p>想法不是事实。你不需要马上相信它，也不需要马上反驳它。</p>
            </aside>

            <footer class="panel-actions">
              <p class="action-hint">三栏都写完后，虚线会连起来。</p>
              <div class="action-buttons">
                <button class="btn btn--ghost" type="button" @click="clearTool">清空三栏</button>
                <button class="btn btn--primary" type="button" @click="saveTool('thought', $event)">
                  保存到我的轨迹
                  <span class="btn-arrow" aria-hidden="true">→</span>
                </button>
              </div>
            </footer>
          </section>

          <section class="tools-support">
            <div>
              <span>如果这件事已经影响到吃饭、睡觉或安全</span>
              <p>先联系身边可信任的人，或学校心理健康中心。遇到立即危险，请拨打 120 / 110。</p>
            </div>
            <RouterLink to="/resources">查看心理资源 <span aria-hidden="true">→</span></RouterLink>
          </section>
        </div>

        <aside ref="traceRail" class="trace-rail" aria-labelledby="tools-trace-title">
          <header class="rail-head">
            <p class="rail-eyebrow">MY TRACE</p>
            <div><h2 id="tools-trace-title">我的轨迹</h2><span class="trace-count">{{ traces.length }}</span></div>
            <p>写完的两难或三栏记录，会放在这里。</p>
          </header>
          <div ref="traceTarget" class="trace-target" aria-hidden="true">
            <span class="target-mark"><i></i><i></i></span>
          </div>
          <div class="trace-list">
            <p v-if="!traces.length" class="trace-empty">还没有记录。<br />先写下一点，再回来看看。</p>
            <article v-for="(record, index) in traces" :key="`${record.time}-${index}`" class="trace-entry">
              <header><span>{{ record.type }}</span><time>{{ record.time }}</time></header>
              <strong>{{ record.title }}</strong>
              <p>{{ record.meta }}</p>
            </article>
          </div>
          <footer class="rail-foot">工具记录只保存在当前浏览器 · 不上传原文</footer>
        </aside>
      </div>
    </div>
    <div class="toast" :class="{ 'is-visible': toastVisible }" role="status" aria-live="polite">{{ toastMessage }}</div>
  </div>
</template>

<style scoped>
.tools-page {
  --bg: #fafaf9;
  --surface: #ffffff;
  --surface-soft: #f6f6f5;
  --ink: #18181b;
  --muted: #6b6b73;
  --line: #e4e4e7;
  --line-strong: #d4d4d8;
  --primary: #18181b;
  --on-primary: #ffffff;
  --accent: #2563eb;
  --accent-soft: #eff6ff;
  --accent-line: #bfdbfe;
  --radius: 10px;
  --shadow: 0 1px 2px rgb(24 24 27 / 6%);
  --type-caption: 12px;
  --type-body: 14px;
  --type-strong: 16px;
  --type-heading: 24px;
  --type-display: clamp(40px, 5.4vw, 64px);
  --rail: 260px;
  min-height: 100vh;
  color: var(--ink);
  background: var(--bg);
  font-family: var(--font);
  font-size: var(--type-body);
  line-height: 1.7;
}

.tools-page :is(button, textarea, input) {
  font: inherit;
}

.tools-page :is(button, a) {
  -webkit-tap-highlight-color: transparent;
}

.tools-page :is(h1, h2, h3, p) {
  margin: 0;
}

.tools-page :is(h1, h2, h3) {
  font-weight: 600;
  letter-spacing: -0.02em;
}

.tools-page ::selection {
  color: var(--ink);
  background: #dbeafe;
}

.tools-page :focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
}

.page {
  width: min(1120px, calc(100% - 48px));
  margin: 0 auto;
  padding: 44px 0 84px;
}

.intro {
  max-width: 780px;
}

.eyebrow,
.panel-index,
.rail-eyebrow {
  color: var(--accent);
  font-size: var(--type-caption);
  font-weight: 600;
  letter-spacing: 0.12em;
}

.intro h1 {
  margin-top: 16px;
  font-size: var(--type-display);
  line-height: 1.08;
  letter-spacing: -0.02em;
}

.intro h1 span {
  color: var(--muted);
}

.lede {
  max-width: 650px;
  margin-top: 20px;
  color: var(--muted);
  font-size: var(--type-body);
  line-height: 1.9;
}

.tool-switch {
  display: grid;
  margin-top: 42px;
  border-top: 1px solid var(--line-strong);
  border-bottom: 1px solid var(--line);
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.tool-tab {
  position: relative;
  display: grid;
  min-height: 84px;
  align-items: center;
  gap: 4px 14px;
  padding: 16px 22px;
  border: 0;
  border-right: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  grid-template-columns: auto 1fr;
  transition: background-color 180ms ease;
}

.tool-tab:last-child {
  border-right: 0;
}

.tool-tab:hover {
  background: rgb(24 24 27 / 2.5%);
}

.tool-tab.is-active {
  background: var(--surface);
}

.tool-tab.is-active::after {
  position: absolute;
  right: 22px;
  bottom: -1px;
  left: 22px;
  height: 2px;
  background: var(--accent);
  content: '';
}

.tool-tab > span {
  grid-row: 1 / 3;
  color: var(--muted);
  font-size: var(--type-body);
  font-weight: 500;
  letter-spacing: 0.04em;
}

.tool-tab.is-active > span {
  color: var(--accent);
}

.tool-tab strong {
  font-size: var(--type-strong);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.tool-tab small {
  color: var(--muted);
  font-size: var(--type-caption);
}

.workspace {
  display: grid;
  align-items: start;
  gap: 48px;
  margin-top: 42px;
  grid-template-columns: minmax(0, 1fr) var(--rail);
}

.tool-area {
  min-width: 0;
}

.tool-panel {
  padding: 30px 32px 32px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow);
  transition:
    opacity 160ms ease,
    transform 200ms var(--ease);
}

.tool-panel.is-leaving {
  opacity: 0;
  transform: translateY(5px);
}

.tool-panel[hidden] {
  display: none;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.panel-head h2 {
  margin-top: 8px;
  font-size: var(--type-heading);
  line-height: 1.25;
}

.panel-note {
  color: var(--muted);
  font-size: var(--type-caption);
  line-height: 1.6;
  text-align: right;
}

.panel-intro {
  max-width: 560px;
  margin-top: 12px;
  color: var(--muted);
  font-size: var(--type-body);
  line-height: 1.8;
}

.balance-zone {
  display: grid;
  align-items: center;
  gap: 10px;
  margin-top: 28px;
  padding: 12px 0 10px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  grid-template-columns: auto 1fr auto;
}

.balance-side {
  color: var(--muted);
  font-size: var(--type-caption);
  font-weight: 600;
}

.balance-line {
  position: relative;
  display: flex;
  height: 18px;
  align-items: center;
  gap: 0;
}

.balance-line::before {
  position: absolute;
  right: 0;
  left: 0;
  height: 1px;
  background: var(--line-strong);
  content: '';
}

.balance-block {
  position: relative;
  z-index: 1;
  height: 6px;
  border-radius: 99px;
  background: var(--line-strong);
  transition:
    width 640ms var(--ease),
    background-color 320ms ease;
}

.balance-block--a {
  width: var(--a-weight, 25%);
}

.balance-block--b {
  width: var(--b-weight, 25%);
  background: #a1a1aa;
}

.balance-mark {
  position: relative;
  z-index: 2;
  width: 7px;
  height: 7px;
  margin: 0 5px;
  border: 1.5px solid var(--surface);
  border-radius: 50%;
  background: var(--ink);
  box-shadow: 0 0 0 1px var(--ink);
  transition: transform 640ms var(--ease);
}

.balance-caption {
  color: var(--muted);
  font-size: var(--type-caption);
  grid-column: 1 / -1;
  text-align: center;
}

.dilemma-grid {
  display: grid;
  gap: 14px;
  margin-top: 22px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.choice-card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  transition:
    border-color 220ms ease,
    box-shadow 220ms ease,
    opacity 260ms ease,
    transform 260ms ease;
}

.choice-card header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.choice-letter {
  display: grid;
  width: 30px;
  height: 30px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  font-size: var(--type-caption);
  font-weight: 600;
  place-items: center;
}

.choice-card header div {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.choice-card header strong {
  font-size: var(--type-body);
  font-weight: 600;
}

.choice-card header small {
  color: var(--muted);
  font-size: var(--type-caption);
}

.complete-dot {
  width: 6px;
  height: 6px;
  margin-left: auto;
  border-radius: 50%;
  background: var(--line);
  transition:
    background-color 220ms ease,
    box-shadow 220ms ease;
}

.field {
  display: block;
}

.field + .field {
  margin-top: 12px;
}

.field > span {
  display: block;
  margin-bottom: 6px;
  color: var(--muted);
  font-size: var(--type-caption);
  font-weight: 500;
}

.field textarea,
.thought-col textarea {
  display: block;
  width: 100%;
  resize: vertical;
  border: 1px solid var(--line);
  border-radius: 9px;
  outline: none;
  color: var(--ink);
  background: var(--bg);
  font-size: var(--type-body);
  line-height: 1.75;
  transition:
    border-color 200ms ease,
    box-shadow 200ms ease,
    background-color 200ms ease,
    transform 200ms ease;
}

.field textarea {
  min-height: 78px;
  padding: 9px 10px;
}

.field textarea::placeholder,
.thought-col textarea::placeholder {
  color: #a1a1aa;
}

.field textarea:focus,
.thought-col textarea:focus {
  border-color: var(--accent-line);
  background: var(--surface);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.card-state {
  margin-top: 10px;
  color: var(--muted);
  font-size: var(--type-caption);
}

.choice-card.is-complete {
  border-color: var(--accent-line);
  box-shadow:
    0 0 0 3px var(--accent-soft),
    var(--shadow);
}

.choice-card.is-complete .complete-dot {
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.choice-card.is-complete .card-state {
  color: var(--accent);
}

.nonnegotiables {
  margin-top: 14px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-soft);
  transition:
    border-color 220ms ease,
    box-shadow 220ms ease,
    opacity 260ms ease,
    transform 260ms ease;
}

.field--wide > span {
  color: var(--ink);
  font-size: var(--type-caption);
  font-weight: 600;
}

.field--wide textarea {
  min-height: 74px;
  background: var(--surface);
}

.nonnegotiables.is-complete {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.panel-actions {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.action-hint {
  max-width: 360px;
  color: var(--muted);
  font-size: var(--type-caption);
  line-height: 1.65;
}

.action-buttons {
  display: flex;
  flex: none;
  gap: 8px;
}

.btn {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 15px;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: var(--type-body);
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color 180ms ease,
    background-color 180ms ease,
    color 180ms ease,
    transform 180ms ease;
}

.btn:active {
  transform: translateY(1px);
}

.btn--ghost {
  border-color: var(--line);
  color: var(--muted);
  background: transparent;
}

.btn--ghost:hover {
  border-color: var(--line-strong);
  color: var(--ink);
}

.btn--primary {
  color: var(--on-primary);
  background: var(--primary);
}

.btn--primary:hover {
  background: #27272a;
}

.btn-arrow {
  font-size: var(--type-body);
  transition: transform 180ms ease;
}

.btn--primary:hover .btn-arrow {
  transform: translateX(2px);
}

/* 填写完成后，边缘柔和高亮，而不是弹窗打断 */
.choice-card.is-complete {
  animation: complete-glow 600ms var(--ease);
}

@keyframes complete-glow {
  0% {
    box-shadow: 0 0 0 0 var(--accent-soft);
  }
  55% {
    box-shadow: 0 0 0 6px var(--accent-soft);
  }
  100% {
    box-shadow:
      0 0 0 3px var(--accent-soft),
      var(--shadow);
  }
}

/* 进入：A → B → 最不能接受，逐块上浮 */
.dilemma-grid.is-entering .choice-card,
.nonnegotiables.is-entering {
  animation: rise-in 520ms var(--ease) both;
}

.dilemma-grid.is-entering .choice-card:first-child {
  animation-delay: 40ms;
}

.dilemma-grid.is-entering .choice-card:last-child {
  animation-delay: 150ms;
}

.nonnegotiables.is-entering {
  animation-delay: 260ms;
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(9px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 清空：先折叠再复位 */
.tool-panel.is-clearing .dilemma-grid,
.tool-panel.is-clearing .nonnegotiables,
.tool-panel.is-clearing .thought-grid,
.tool-panel.is-clearing .flow-map {
  max-height: 0;
  overflow: hidden;
  opacity: 0;
  transform: translateY(4px);
  transition:
    max-height 280ms var(--ease),
    opacity 180ms ease,
    transform 280ms var(--ease);
}

.dilemma-grid,
.nonnegotiables,
.thought-grid,
.flow-map {
  max-height: 1200px;
  transition:
    max-height 320ms var(--ease),
    opacity 240ms ease,
    transform 320ms var(--ease);
}

/* 想法捕捉器 */
.flow-map {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 26px;
  padding: 12px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.flow-node {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
  color: var(--muted);
  font-size: var(--type-caption);
  font-weight: 500;
  transition: color 200ms ease;
}

.flow-node i {
  width: 6px;
  height: 6px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--surface);
  transition:
    border-color 200ms ease,
    background-color 200ms ease;
}

.flow-node.is-filled {
  color: var(--ink);
}

.flow-node.is-filled i {
  border-color: var(--accent);
  background: var(--accent);
}

.flow-link {
  position: relative;
  flex: 1;
  height: 1px;
  background: repeating-linear-gradient(
    90deg,
    var(--line-strong) 0 4px,
    transparent 4px 8px
  );
  transition: background 300ms ease;
}

.flow-link i {
  position: absolute;
  top: -1px;
  left: 0;
  width: 0;
  height: 2px;
  background: var(--accent);
  transition: width 420ms var(--ease);
}

.flow-link.is-on i {
  width: 100%;
}

.thought-grid {
  display: grid;
  gap: 12px;
  margin-top: 20px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.thought-col {
  display: block;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  transition:
    border-color 200ms ease,
    box-shadow 200ms ease;
}

.thought-col:focus-within {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.thought-col header {
  display: grid;
  align-items: baseline;
  gap: 2px 7px;
  margin-bottom: 12px;
  grid-template-columns: auto 1fr;
}

.thought-col header span {
  color: var(--line-strong);
  font-size: var(--type-caption);
  grid-row: 1 / 3;
}

.thought-col header strong {
  font-size: var(--type-body);
  font-weight: 600;
}

.thought-col header small {
  color: var(--muted);
  font-size: var(--type-caption);
}

.thought-col textarea {
  min-height: 148px;
  padding: 9px 10px;
}

.thought-col > p {
  margin-top: 9px;
  color: var(--muted);
  font-size: var(--type-caption);
  line-height: 1.65;
}

.thought-col.is-filled {
  border-color: var(--accent-line);
}

.thought-pause {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 16px;
  padding: 14px 16px;
  border-left: 2px solid var(--accent);
  background: var(--surface-soft);
}

.thought-pause span {
  color: var(--accent);
  font-size: var(--type-heading);
  line-height: 1;
}

.thought-pause p {
  color: var(--ink);
  font-size: var(--type-caption);
  line-height: 1.7;
}

.tool-panel.is-entering .choice-card,
.tool-panel.is-entering .thought-col {
  animation: rise-in 520ms var(--ease) both;
}

.tool-panel.is-entering .choice-card:nth-child(2),
.tool-panel.is-entering .thought-col:nth-child(2) {
  animation-delay: 90ms;
}

.tool-panel.is-entering .thought-col:nth-child(3) {
  animation-delay: 180ms;
}

/* 轨迹侧栏 */
.trace-rail {
  position: sticky;
  top: 28px;
  min-width: 0;
  padding-top: 2px;
}

.rail-head h2 {
  display: inline;
  font-size: var(--type-heading);
  font-weight: 600;
}

.rail-head > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 7px;
}

.rail-head > p:last-child {
  margin-top: 8px;
  color: var(--muted);
  font-size: var(--type-caption);
  line-height: 1.7;
}

.trace-count {
  color: var(--accent);
  font-size: var(--type-caption);
  font-weight: 600;
}

.trace-target {
  display: grid;
  width: 100%;
  height: 72px;
  margin-top: 18px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius);
  place-items: center;
  transition:
    border-color 200ms ease,
    background-color 200ms ease,
    transform 240ms var(--ease);
}

.trace-target.is-receiving {
  border-color: var(--accent-line);
  background: var(--accent-soft);
  transform: scale(0.985);
}

.target-mark {
  position: relative;
  display: grid;
  width: 26px;
  height: 26px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  place-items: center;
}

.target-mark i {
  position: absolute;
  width: 10px;
  height: 4px;
  border: 1px solid var(--muted);
  border-radius: 50%;
  transform: rotate(-35deg);
}

.target-mark i:first-child {
  translate: -2px -1px;
}

.target-mark i:last-child {
  translate: 2px 1px;
}

.trace-list {
  display: grid;
  gap: 0;
  margin-top: 20px;
}

.trace-empty {
  padding: 18px 0;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: var(--type-caption);
  line-height: 1.8;
}

.trace-entry {
  padding: 13px 0;
  border-top: 1px solid var(--line);
  animation: trace-in 360ms var(--ease) both;
}

.trace-entry header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.trace-entry header span {
  color: var(--accent);
  font-size: var(--type-caption);
  font-weight: 600;
  letter-spacing: 0.04em;
}

.trace-entry header time {
  color: var(--muted);
  font-size: var(--type-caption);
}

.trace-entry strong {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  font-size: var(--type-body);
  font-weight: 500;
  line-height: 1.6;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-entry p {
  margin-top: 3px;
  color: var(--muted);
  font-size: var(--type-caption);
  line-height: 1.6;
}

@keyframes trace-in {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.rail-foot {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: var(--type-caption);
  line-height: 1.6;
}

/* 保存时的小卡片飞行 */
:global(.save-ghost) {
  position: fixed;
  z-index: 80;
  width: 132px;
  padding: 10px 12px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  color: var(--ink);
  background: var(--surface);
  box-shadow: 0 8px 24px rgb(24 24 27 / 12%);
  font-size: var(--type-caption);
  line-height: 1.45;
  pointer-events: none;
  animation: ghost-fly 700ms var(--ease) forwards;
}

:global(.save-ghost strong) {
  display: block;
  overflow: hidden;
  font-size: var(--type-body);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.save-ghost small) {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: var(--type-caption);
}

@keyframes ghost-fly {
  0% {
    opacity: 0;
    transform: translate(0, 0) scale(0.96);
  }
  15% {
    opacity: 1;
    transform: translate(0, 0) scale(1);
  }
  70% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translate(var(--fly-x), var(--fly-y)) scale(0.24);
  }
}

.toast {
  position: fixed;
  z-index: 90;
  right: 24px;
  bottom: 24px;
  max-width: 300px;
  padding: 10px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  color: var(--ink);
  background: var(--surface);
  box-shadow: 0 8px 24px rgb(24 24 27 / 10%);
  font-size: var(--type-caption);
  opacity: 0;
  pointer-events: none;
  transform: translateY(6px);
  transition:
    opacity 200ms ease,
    transform 240ms var(--ease);
}

.toast.is-visible {
  opacity: 1;
  transform: translateY(0);
}

@media (max-width: 960px) {
  .workspace {
    gap: 34px;
    grid-template-columns: 1fr;
  }

  .trace-rail {
    position: static;
    display: grid;
    gap: 0 26px;
    padding-top: 22px;
    border-top: 1px solid var(--line);
    grid-template-columns: 1fr 1fr;
  }

  .rail-head {
    grid-column: 1 / -1;
  }

  .trace-target {
    display: none;
  }

  .trace-list {
    margin-top: 14px;
  }

  .rail-foot {
    grid-column: 1 / -1;
  }
}

@media (max-width: 700px) {
  .page {
    width: calc(100% - 32px);
    padding: 38px 0 60px;
  }

  .intro h1 {
    font-size: var(--type-display);
  }

  .lede {
    font-size: var(--type-body);
  }

  .tool-switch {
    margin-top: 30px;
  }

  .tool-tab {
    min-height: 74px;
    padding: 12px;
    grid-template-columns: 1fr;
  }

  .tool-tab > span {
    grid-row: auto;
    font-size: var(--type-caption);
  }

  .tool-tab strong {
    font-size: var(--type-body);
  }

  .tool-tab small {
    display: none;
  }

  .workspace {
    margin-top: 28px;
  }

  .tool-panel {
    padding: 22px 18px 24px;
  }

  .panel-head {
    display: block;
  }

  .panel-note {
    margin-top: 8px;
    text-align: left;
  }

  .panel-head h2 {
    font-size: var(--type-heading);
  }

  .dilemma-grid,
  .thought-grid {
    grid-template-columns: 1fr;
  }

  .panel-actions {
    display: block;
  }

  .action-buttons {
    margin-top: 14px;
  }

  .action-buttons .btn {
    flex: 1;
  }

  .trace-rail {
    grid-template-columns: 1fr;
  }

  .toast {
    right: 16px;
    bottom: 16px;
    left: 16px;
    max-width: none;
  }
}

.tools-support {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-top: 22px;
  padding: 16px 0 0;
  border-top: 1px solid var(--line);
}

.tools-support > div > span {
  color: var(--ink);
  font-size: var(--type-caption);
  font-weight: 600;
}

.tools-support p {
  max-width: 560px;
  margin-top: 4px;
  color: var(--muted);
  font-size: var(--type-caption);
  line-height: 1.7;
}

.tools-support > a {
  flex: none;
  color: var(--accent);
  font-size: var(--type-caption);
  text-decoration: none;
  white-space: nowrap;
}

.tools-support > a:hover {
  text-decoration: underline;
}

[data-theme='sage'] .tools-page {
  --bg: #fafaf9;
  --surface: #ffffff;
  --surface-soft: #f6f6f5;
  --ink: #18181b;
  --muted: #6b6b73;
  --line: #e4e4e7;
  --line-strong: #d4d4d8;
  --accent: #2563eb;
  --accent-soft: #eff6ff;
  --accent-line: #bfdbfe;
  --primary: #18181b;
  --on-primary: #ffffff;
}

[data-theme='oat'] .tools-page {
  --bg: #f5efe2;
  --surface: #fffaf0;
  --surface-soft: #eee5d4;
  --ink: #27251d;
  --muted: #75695b;
  --line: #dfd3bd;
  --line-strong: #cdbda2;
  --accent: #9e6d4c;
  --accent-soft: #f3e6d6;
  --accent-line: #d9b894;
  --primary: #27251d;
  --on-primary: #fffaf0;
  --shadow: 0 1px 2px rgb(39 37 29 / 7%);
}

[data-theme='mist'] .tools-page {
  --bg: #eef3f2;
  --surface: #fbfdfc;
  --surface-soft: #e5eeeb;
  --ink: #19302e;
  --muted: #5b716e;
  --line: #d4dfdc;
  --line-strong: #bdceca;
  --accent: #2f6b6b;
  --accent-soft: #e3f0ed;
  --accent-line: #a9cbc5;
  --primary: #19302e;
  --on-primary: #fbfdfc;
  --shadow: 0 1px 2px rgb(25 48 46 / 7%);
}

[data-theme='slate'] .tools-page {
  --bg: #eef1f4;
  --surface: #fbfcfe;
  --surface-soft: #e5eaf0;
  --ink: #26323a;
  --muted: #5e6d79;
  --line: #d6dde5;
  --line-strong: #bdc9d4;
  --accent: #3d5a7a;
  --accent-soft: #e8eef6;
  --accent-line: #a9bdd4;
  --primary: #26323a;
  --on-primary: #fbfcfe;
  --shadow: 0 1px 2px rgb(38 50 58 / 7%);
}

[data-theme='dark'] .tools-page {
  --bg: #121815;
  --surface: #1a231e;
  --surface-soft: #202b25;
  --ink: #edf2ee;
  --muted: #aebdb4;
  --line: #324139;
  --line-strong: #46584d;
  --primary: #9fc5ae;
  --on-primary: #10241d;
  --accent: #8ab4ff;
  --accent-soft: #16243a;
  --accent-line: #2f4e7a;
  --shadow: none;
}

[data-theme='dark'] .thought-pause p {
  color: var(--ink);
}

[data-theme='dark'] .tools-page .btn--primary {
  color: #10241d;
}

[data-theme='dark'] .tools-page ::selection {
  color: var(--ink);
  background: #1e3a5f;
}

@media (max-width: 700px) {
  .tools-support {
    display: block;
  }

  .tools-support > a {
    display: inline-flex;
    margin-top: 10px;
  }
}


</style>
