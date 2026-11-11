<script setup lang="ts">
import { onMounted, ref } from 'vue'
import WellbeingToolkit from '@/components/public/WellbeingToolkit.vue'
import Icon from '@/components/ui/Icon.vue'

const groundingSteps = [
  { title: '看见 5 样东西', text: '慢慢看看周围，找到五件你看得见的东西。', icon: 'eye' },
  { title: '听见 4 种声音', text: '停一下，听听四个来自周围或身体的声音。', icon: 'bell' },
  { title: '触碰 3 个物体', text: '感受三样东西的触感，比如桌面、衣角或水杯。', icon: 'cursor' },
  { title: '找到 2 种气味', text: '不一定真的闻到，想一想此刻能闻到什么。', icon: 'spark' },
  { title: '说出 1 件当下的事', text: '告诉自己：“此刻，我在这里。”', icon: 'check-circle' },
]
const groundingStep = ref(0)

const selfCareItems = [
  { id: 'water', label: '喝一点水' },
  { id: 'stand', label: '站起来伸展一下' },
  { id: 'light', label: '离开屏幕几分钟' },
  { id: 'eat', label: '吃一点东西' },
  { id: 'clean', label: '整理身边一小块空间' },
  { id: 'pause', label: '允许自己什么都不做一会儿' },
]
const completedCare = ref<string[]>([])

const privateNote = ref('')
const noteSaved = ref(false)

function toggleCare(id: string): void {
  completedCare.value = completedCare.value.includes(id)
    ? completedCare.value.filter((item) => item !== id)
    : [...completedCare.value, id]
  try {
    localStorage.setItem('diy-campus:self-care-today', JSON.stringify(completedCare.value))
  } catch {
    // 只保存在本机，写入失败不影响勾选。
  }
}

function saveNote(): void {
  try {
    localStorage.setItem('diy-campus:private-note', privateNote.value.trim())
    noteSaved.value = true
    window.setTimeout(() => {
      noteSaved.value = false
    }, 1800)
  } catch {
    noteSaved.value = false
  }
}

function clearNote(): void {
  privateNote.value = ''
  try {
    localStorage.removeItem('diy-campus:private-note')
  } catch {
    // 本地存储不可用时仍然清空当前输入。
  }
  noteSaved.value = false
}

onMounted(() => {
  try {
    const savedCare = JSON.parse(localStorage.getItem('diy-campus:self-care-today') ?? '[]') as unknown
    if (Array.isArray(savedCare)) {
      completedCare.value = savedCare.filter((item): item is string => typeof item === 'string')
    }
    privateNote.value = localStorage.getItem('diy-campus:private-note') ?? ''
  } catch {
    completedCare.value = []
    privateNote.value = ''
  }
})
</script>

<template>
  <main class="tools-page">
    <section class="tools-hero">
      <span>WELLBEING TOOLS</span>
      <h1>心晴工具</h1>
      <p>呼吸计时、情绪记录、感官定位和本地便签。内容仅保存在当前浏览器，不做心理测评。</p>
      <div>
        <span><Icon name="check" :size="13" />只保存在本机</span>
        <span><Icon name="check" :size="13" />随时停止</span>
        <span><Icon name="check" :size="13" />不提交服务器</span>
      </div>
    </section>

    <WellbeingToolkit />

    <section id="grounding" class="tools-section">
      <header>
        <span>5-4-3-2-1 感官落地</span>
        <h2>感官定位练习</h2>
      </header>
      <div class="grounding-card">
        <div class="grounding-progress">
          <i v-for="(_, index) in groundingSteps" :key="index" :class="{ 'is-active': index <= groundingStep }" />
        </div>
        <span class="grounding-count">0{{ groundingStep + 1 }} / 05</span>
        <div class="grounding-icon"><Icon :name="groundingSteps[groundingStep]?.icon || 'spark'" :size="24" /></div>
        <h3>{{ groundingSteps[groundingStep]?.title }}</h3>
        <p>{{ groundingSteps[groundingStep]?.text }}</p>
        <button
          type="button"
          @click="groundingStep = groundingStep >= groundingSteps.length - 1 ? 0 : groundingStep + 1"
        >
          {{ groundingStep >= groundingSteps.length - 1 ? '重新开始' : '完成这一步' }}
          <Icon name="arrow-right" :size="14" />
        </button>
      </div>
    </section>

    <section class="tools-two-column">
      <article id="care" class="simple-tool-card">
        <header>
          <span>今日清单</span>
          <h2>自我照料清单</h2>
        </header>
        <div class="self-care-list">
          <button
            v-for="item in selfCareItems"
            :key="item.id"
            type="button"
            :class="{ 'is-done': completedCare.includes(item.id) }"
            @click="toggleCare(item.id)"
          >
            <i><Icon v-if="completedCare.includes(item.id)" name="check" :size="12" /></i>
            {{ item.label }}
          </button>
        </div>
        <p>勾选今天完成的事项，不需要全部完成。</p>
      </article>

      <article id="note" class="simple-tool-card note-tool">
        <header>
          <span>浏览器本地存储</span>
          <h2>临时便签</h2>
        </header>
        <textarea
          v-model="privateNote"
          rows="6"
          maxlength="500"
          placeholder="记录需要暂时放下的信息。内容只保存在当前浏览器。"
        />
        <div>
          <button type="button" @click="clearNote">清空</button>
          <button class="note-save" type="button" @click="saveNote">
            <Icon name="check" :size="13" />{{ noteSaved ? '已保存在本机' : '只在本地保存' }}
          </button>
        </div>
      </article>
    </section>

    <section class="tools-boundary">
      <div>
        <Icon name="help" :size="21" />
        <div>
          <strong>需要人工支持时</strong>
          <p>如需专业帮助，请查看心理资源；如存在立即危险，请拨打 120 / 110。</p>
        </div>
      </div>
      <RouterLink to="/resources">查看心理资源与求助方向 <Icon name="arrow-right" :size="14" /></RouterLink>
    </section>
  </main>
</template>

<style scoped>
.tools-page {
  width: min(1240px, calc(100% - 56px));
  margin: 0 auto;
  padding-bottom: 56px;
}

.tools-hero {
  padding: 34px 0 22px;
}

.tools-hero > span,
.tools-section > header > span,
.simple-tool-card header > span {
  color: var(--brand-500);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.tools-hero h1 {
  margin-top: 11px;
  font-family: var(--font-serif);
  font-size: 48px;
  letter-spacing: -0.06em;
}

.tools-hero > p {
  max-width: 680px;
  margin-top: 10px;
  color: var(--ink-500);
  font-size: 11px;
  line-height: 1.8;
}

.tools-hero > div {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 14px;
}

.tools-hero > div span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--ink-400);
  font-size: 9px;
}

.tools-hero > div svg {
  color: var(--brand-500);
}

.tools-section {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.tools-section h2,
.simple-tool-card h2 {
  margin-top: 8px;
  font-family: var(--font-serif);
  font-size: 26px;
  letter-spacing: -0.04em;
}

.need-layout {
  display: grid;
  gap: 13px;
  margin-top: 22px;
  grid-template-columns: 1fr 1fr;
}

.need-options {
  display: grid;
  gap: 8px;
}

.need-options button {
  display: flex;
  min-height: 50px;
  align-items: center;
  justify-content: space-between;
  padding: 0 15px;
  border: 1px solid var(--line);
  border-radius: 13px;
  color: var(--ink-600);
  background: #fff;
  font-size: 10px;
  cursor: pointer;
}

.need-options button.is-active {
  border-color: var(--brand-300);
  color: var(--brand-700);
  background: var(--brand-50);
}

.need-result {
  display: flex;
  min-height: 210px;
  justify-content: center;
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: linear-gradient(145deg, #f8fbf7, #f3eee7);
  flex-direction: column;
}

.need-result > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--brand-600);
  font-size: 8px;
  font-weight: 700;
}

.need-result strong {
  margin-top: 17px;
  font-family: var(--font-serif);
  font-size: 20px;
  line-height: 1.55;
}

.need-result p {
  margin-top: 11px;
  color: var(--ink-400);
  font-size: 9px;
}

.grounding-card {
  position: relative;
  display: flex;
  align-items: center;
  max-width: 760px;
  margin: 22px auto 0;
  padding: 27px;
  border: 1px solid var(--line);
  border-radius: 0;
  background: var(--surface);
  flex-direction: column;
  text-align: center;
}

.grounding-progress {
  display: flex;
  gap: 6px;
}

.grounding-progress i {
  width: 32px;
  height: 3px;
  border-radius: 99px;
  background: var(--ink-100);
}

.grounding-progress i.is-active {
  background: var(--brand-400);
}

.grounding-count {
  position: absolute;
  top: 22px;
  right: 24px;
  color: var(--ink-300);
  font-family: var(--font-serif);
}

.grounding-icon {
  display: grid;
  width: 55px;
  height: 55px;
  margin-top: 22px;
  border-radius: 18px;
  color: var(--brand-600);
  background: var(--brand-50);
  place-items: center;
}

.grounding-card h3 {
  margin-top: 17px;
  font-size: 18px;
}

.grounding-card > p {
  max-width: 480px;
  margin-top: 9px;
  color: var(--ink-400);
  font-size: 10px;
  line-height: 1.8;
}

.grounding-card > button {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  gap: 6px;
  margin-top: 20px;
  padding: 0 13px;
  border: 0;
  border-radius: 999px;
  color: #fff;
  background: var(--brand-600);
  font-size: 9px;
  cursor: pointer;
}

.tools-two-column {
  display: grid;
  gap: 13px;
  margin-top: 26px;
  grid-template-columns: 1fr 1fr;
}

.simple-tool-card {
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 0;
  background: var(--surface);
}

.self-care-list {
  display: grid;
  gap: 7px;
  margin-top: 20px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.self-care-list button {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 39px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--ink-500);
  background: #fff;
  font-size: 9px;
  text-align: left;
  cursor: pointer;
}

.self-care-list button i {
  display: grid;
  width: 17px;
  height: 17px;
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  place-items: center;
}

.self-care-list button.is-done {
  border-color: var(--brand-200);
  color: var(--brand-700);
  background: var(--brand-50);
}

.self-care-list button.is-done i {
  border-color: var(--brand-400);
  color: #fff;
  background: var(--brand-500);
}

.simple-tool-card > p {
  margin-top: 13px;
  color: var(--ink-400);
  font-size: 8px;
}

.note-tool textarea {
  width: 100%;
  margin-top: 20px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  outline: 0;
  color: var(--ink-700);
  background: #fbfcfa;
  font: inherit;
  font-size: 10px;
  line-height: 1.7;
  resize: vertical;
}

.note-tool textarea:focus {
  border-color: var(--brand-300);
}

.note-tool > div {
  display: flex;
  justify-content: space-between;
  margin-top: 9px;
}

.note-tool button {
  padding: 5px 0;
  border: 0;
  color: var(--ink-400);
  background: transparent;
  font-size: 8px;
  cursor: pointer;
}

.note-tool .note-save {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--brand-600);
  font-weight: 700;
}

.tools-boundary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: 26px;
  padding: 20px;
  border: 1px solid #eadfcf;
  border-radius: 0;
  color: #816447;
  background: #fcf8ef;
}

.tools-boundary > div {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.tools-boundary strong {
  font-size: 10px;
}

.tools-boundary p {
  max-width: 700px;
  margin-top: 4px;
  font-size: 9px;
  line-height: 1.7;
}

.tools-boundary a {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  color: #986b3d;
  font-size: 9px;
  font-weight: 700;
}

@media (max-width: 820px) {
  .need-layout,
  .tools-two-column {
    grid-template-columns: 1fr;
  }

  .tools-boundary {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 600px) {
  .tools-page {
    width: calc(100% - 36px);
  }

  .self-care-list {
    grid-template-columns: 1fr;
  }
}
</style>
