<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import Icon from '@/components/ui/Icon.vue'

const moods = [
  { id: 'calm', label: '平静', color: '#9fbea8', message: '让这份平静多停留一会儿。' },
  { id: 'warm', label: '开心', color: '#e0b58f', message: '把今天的好心情记在心里。' },
  { id: 'tired', label: '疲惫', color: '#9aaeb8', message: '累了就慢一点，不必马上恢复。' },
  { id: 'heavy', label: '有点沉', color: '#8d9ba3', message: '可以暂停其他任务，先确认自己当下的状态。' },
  { id: 'unsure', label: '说不清', color: '#b9b39a', message: '说不清也没关系，感受本来就复杂。' },
]

const prompts = [
  '今天可以少做一件不必要的事。',
  '喝水、伸展、看向远处，都是照顾自己。',
  '不想分享的时候，保持沉默也是一种边界。',
  '不需要每一天都积极，平稳度过也算很好。',
  '如果今天很难，先联系一个让你感到安全的人。',
]

const breathing = ref(false)
const phase = ref<'吸气' | '呼气'>('吸气')
const selectedMood = ref('')
const promptIndex = ref(0)
let breathTimer: number | undefined

function toggleBreathing(): void {
  if (breathing.value) {
    breathing.value = false
    if (breathTimer !== undefined) window.clearInterval(breathTimer)
    breathTimer = undefined
    return
  }
  breathing.value = true
  phase.value = '吸气'
  breathTimer = window.setInterval(() => {
    phase.value = phase.value === '吸气' ? '呼气' : '吸气'
  }, 4000)
}

function chooseMood(id: string): void {
  selectedMood.value = id
  try {
    localStorage.setItem('diy-campus:wellbeing-mood', id)
  } catch {
    // 本机记录失败不影响体验。
  }
}

function nextPrompt(): void {
  promptIndex.value = (promptIndex.value + 1) % prompts.length
}

onBeforeUnmount(() => {
  if (breathTimer !== undefined) window.clearInterval(breathTimer)
})

onMounted(() => {
  try {
    const saved = localStorage.getItem('diy-campus:wellbeing-mood') ?? ''
    if (moods.some((mood) => mood.id === saved)) selectedMood.value = saved
  } catch {
    selectedMood.value = ''
  }
})
</script>

<template>
  <section id="today" class="toolkit">
    <header>
      <div>
        <span>QUICK TOOLS</span>
        <h2>快速工具</h2>
      </div>
      <p>呼吸计时、情绪记录和随机提示均只在当前浏览器中运行。</p>
    </header>

    <div class="toolkit-grid">
      <article class="breathing-card">
        <div class="toolkit-label"><Icon name="clock" :size="16" />呼吸计时器</div>
        <button class="breathing-circle" :class="{ 'is-running': breathing }" type="button" @click="toggleBreathing">
          <i /><i /><strong>{{ breathing ? phase : '开始' }}</strong>
        </button>
        <p>{{ breathing ? '跟随节奏：吸气 4 秒，呼气 4 秒。' : '点击开始四秒吸气、四秒呼气计时。' }}</p>
      </article>

      <article class="mood-card">
        <div class="toolkit-label"><Icon name="palette" :size="16" />情绪记录</div>
        <div class="mood-options">
          <button
            v-for="mood in moods"
            :key="mood.id"
            type="button"
            :class="{ 'is-active': selectedMood === mood.id }"
            @click="chooseMood(mood.id)"
          >
            <i :style="{ background: mood.color }" />{{ mood.label }}
          </button>
        </div>
        <p>{{ moods.find((item) => item.id === selectedMood)?.message || '选择一项记录今天的状态，数据只保存在本机。' }}</p>
      </article>

      <article class="prompt-card">
        <div class="toolkit-label"><Icon name="spark" :size="16" />随机提示</div>
        <strong>{{ prompts[promptIndex] }}</strong>
        <button type="button" @click="nextPrompt">换一句 <Icon name="refresh" :size="13" /></button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.toolkit {
  width: min(1320px, calc(100% - 10vw));
  margin: 0 auto;
  padding: 10px 0 42px;
}

.toolkit > header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
}

.toolkit > header span {
  color: var(--brand-500);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.toolkit > header h2 {
  margin-top: 7px;
  font-family: var(--font-serif);
  font-size: 30px;
  letter-spacing: -0.04em;
}

.toolkit > header p {
  max-width: 430px;
  color: var(--ink-400);
  font-size: 9px;
  line-height: 1.7;
  text-align: right;
}

.toolkit-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: 0.9fr 1.2fr 0.9fr;
}

.toolkit-grid article {
  min-height: 200px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 0;
  background: var(--surface);
}

.toolkit-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--brand-600);
  font-size: 9px;
  font-weight: 700;
}

.breathing-card {
  display: flex;
  align-items: center;
  flex-direction: column;
  text-align: center;
}

.breathing-circle {
  position: relative;
  display: grid;
  width: 104px;
  height: 104px;
  margin: 18px 0 13px;
  border: 0;
  border-radius: 50%;
  color: var(--brand-600);
  background: #edf5ef;
  cursor: pointer;
  place-items: center;
}

.breathing-circle i {
  position: absolute;
  inset: 12px;
  border: 1px solid rgb(65 117 87 / 18%);
  border-radius: 50%;
}

.breathing-circle i:nth-child(2) {
  inset: 25px;
}

.breathing-circle strong {
  font-size: 12px;
}

.breathing-circle.is-running {
  animation: breathe 8s ease-in-out infinite;
}

.breathing-card p,
.mood-card p {
  color: var(--ink-400);
  font-size: 8px;
  line-height: 1.7;
}

.mood-options {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin: 22px 0 18px;
}

.mood-options button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-500);
  background: #fff;
  font-size: 9px;
  cursor: pointer;
}

.mood-options button.is-active {
  border-color: var(--brand-300);
  color: var(--brand-700);
  background: var(--brand-50);
}

.mood-options button i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.prompt-card {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
}

.prompt-card > strong {
  margin-top: 30px;
  font-family: var(--font-serif);
  font-size: 22px;
  letter-spacing: -0.035em;
  line-height: 1.45;
}

.prompt-card > button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: auto;
  padding: 0;
  border: 0;
  color: var(--brand-600);
  background: transparent;
  font-size: 9px;
  cursor: pointer;
}

@keyframes breathe {
  0%,
  100% {
    transform: scale(0.92);
    background: #edf5ef;
  }
  50% {
    transform: scale(1.08);
    background: #deede2;
  }
}

@media (max-width: 900px) {
  .toolkit > header {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolkit > header p {
    text-align: left;
  }

  .toolkit-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .toolkit {
    width: calc(100% - 36px);
  }
}
</style>
