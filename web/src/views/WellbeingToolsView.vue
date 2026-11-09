<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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

const needs = [
  { id: 'stress', label: '压力有点大', tool: '先试试 5-4-3-2-1 感官落地，让注意力回到当下。', target: 'grounding' },
  { id: 'sleep', label: '最近睡不好', tool: '可以先把睡前目标缩小：离开屏幕、调暗灯光、放松五分钟。', target: 'care' },
  {
    id: 'people',
    label: '和别人的关系让我难受',
    tool: '不需要马上解决关系。先写下一句此刻最想表达的话，留在本机。',
    target: 'note',
  },
  {
    id: 'quiet',
    label: '我只想安静一下',
    tool: '不用完成任何任务。可以做一个缓慢呼吸练习后离开页面。',
    target: 'breathing',
  },
]
const selectedNeed = ref('')
const selectedNeedInfo = computed(() => needs.find((item) => item.id === selectedNeed.value))

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
      <span>GENTLE SELF-CARE TOOLS</span>
      <h1>心晴工具箱</h1>
      <p>这些工具用于短时间的自我照顾和活动参与，不做心理测评，也不会把你的内容发送到服务器。</p>
      <div>
        <span><Icon name="check" :size="13" />只保存在本机</span>
        <span><Icon name="check" :size="13" />随时可以停止</span>
        <span><Icon name="check" :size="13" />没有正确完成方式</span>
      </div>
    </section>

    <WellbeingToolkit />

    <section class="tools-section">
      <header>
        <span>从眼前的感受开始</span>
        <h2>选择最接近此刻的需要</h2>
      </header>
      <div class="need-layout">
        <div class="need-options">
          <button
            v-for="need in needs"
            :key="need.id"
            type="button"
            :class="{ 'is-active': selectedNeed === need.id }"
            @click="selectedNeed = need.id"
          >
            {{ need.label }}<Icon name="arrow-right" :size="14" />
          </button>
        </div>
        <article class="need-result">
          <span><Icon name="spark" :size="18" />一个可以尝试的方向</span>
          <strong>{{ selectedNeedInfo?.tool || '不需要马上给感受一个答案，可以先选最接近的一项。' }}</strong>
          <p v-if="selectedNeedInfo">如果它不合适，可以随时换一个工具或离开页面。</p>
        </article>
      </div>
    </section>

    <section id="grounding" class="tools-section">
      <header>
        <span>5-4-3-2-1 感官落地</span>
        <h2>把注意力慢慢带回此刻</h2>
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
          <span>今天的小小照顾</span>
          <h2>完成一件就够了</h2>
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
        <p>提示：这不是任务清单，没有全部完成的要求。</p>
      </article>

      <article id="note" class="simple-tool-card note-tool">
        <header>
          <span>只给自己看的便签</span>
          <h2>把此刻想说的话放在这里</h2>
        </header>
        <textarea
          v-model="privateNote"
          rows="6"
          maxlength="500"
          placeholder="不想发出去的话，也可以只写给自己。内容只保存在当前浏览器。"
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
          <strong>工具不能替代真实支持</strong>
          <p>如果困扰持续影响生活，或你正在经历立即危险，请联系学校心理健康中心、信任的老师，或拨打 120 / 110。</p>
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
  padding-bottom: 80px;
}

.tools-hero {
  padding: 56px 0 32px;
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
  font-size: clamp(42px, 6vw, 68px);
  letter-spacing: -0.06em;
}

.tools-hero > p {
  max-width: 680px;
  margin-top: 13px;
  color: var(--ink-500);
  font-size: 11px;
  line-height: 1.8;
}

.tools-hero > div {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 20px;
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
  margin-top: 44px;
  padding-top: 30px;
  border-top: 1px solid var(--line);
}

.tools-section h2,
.simple-tool-card h2 {
  margin-top: 8px;
  font-family: var(--font-serif);
  font-size: 30px;
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
  border-radius: 22px;
  background: #fff;
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
  margin-top: 44px;
  grid-template-columns: 1fr 1fr;
}

.simple-tool-card {
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: #fff;
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
  margin-top: 44px;
  padding: 20px;
  border: 1px solid #eadfcf;
  border-radius: 17px;
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
