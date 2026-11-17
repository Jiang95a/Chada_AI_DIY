<script setup lang="ts">
import { RouterLink } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'

const lifeTopics = [
  {
    title: '学业与考试压力',
    kicker: '先处理哪一部分',
    text: '从可执行的小步骤开始：拆任务、短暂休息，或者先找一个同学对一遍。',
    icon: 'file',
    tone: 'paper',
  },
  {
    title: '睡眠与作息',
    kicker: '先观察，不急着贴标签',
    text: '先看最近一周的作息变化，不急着给自己下“失眠”的结论。',
    icon: 'clock',
    tone: 'paper',
  },
  {
    title: '人际关系',
    kicker: '先找到安全的倾听对象',
    text: '关系里的困扰通常不是一句建议能解决，先找一个不需要你解释太多的人。',
    icon: 'users',
    tone: 'sage',
  },
  {
    title: '孤独与适应',
    kicker: '从一场低压力活动开始',
    text: '进入新环境时感到不适应很常见，不必强迫自己马上变外向。',
    icon: 'pin',
    tone: 'sage',
  },
]

const companionTips = [
  '先听对方把话说完，不急着判断对错。',
  '问一句“你希望我听你说，还是一起想办法？”',
  '不替对方贴心理标签，也不强迫分享细节。',
  '鼓励对方联系学校心理健康中心或可信任的老师。',
]

const contacts = [
  { title: '学校心理健康中心', text: '可预约专业咨询，也可以先了解活动信息。', icon: 'pin' },
  { title: '辅导员或信任的老师', text: '适合帮助联系资源、协调现实问题。', icon: 'users' },
  { title: '心屿朋辈活动', text: '参与活动与表达，不等于专业咨询。', icon: 'help' },
]

const journey = [
  { title: '心理活动报名', text: '从一场低压力活动开始', to: '/campus' },
  { title: '心理知识科普', text: '先理解，再决定要不要参与', to: null },
  { title: '匿名参与', text: '不需要留下姓名', to: null },
  { title: '自愿参与，随时离开', text: '不分享也可以', to: null },
]
</script>

<template>
  <main class="resources-page">
    <section class="resources-hero">
      <span>CAMPUS WELLBEING RESOURCES</span>
      <h1>不知道从哪里开始时，<br />可以先看看这里。</h1>
      <p>这里提供参与校园心理活动的信息和求助方向，不做心理诊断，也不替代专业帮助。</p>
    </section>

    <section class="resources-collage" aria-label="校园心理资源">
      <div class="topic-grid">
        <article
          v-for="(topic, index) in lifeTopics"
          :key="topic.title"
          class="topic-card"
          :class="`topic-card--${topic.tone}`"
        >
          <div class="topic-top">
            <span class="topic-number">{{ String(index + 1).padStart(2, '0') }}</span>
            <Icon :name="topic.icon" :size="20" />
          </div>
          <p class="topic-kicker">{{ topic.kicker }}</p>
          <h2>{{ topic.title }}</h2>
          <p class="topic-text">{{ topic.text }}</p>
        </article>
      </div>

      <div class="resource-side">
        <article class="support-card">
          <span>如何陪伴一个正在难受的朋友</span>
          <h2>不是马上把他“修好”</h2>
          <ul>
            <li v-for="tip in companionTips" :key="tip"><Icon name="check" :size="14" />{{ tip }}</li>
          </ul>
        </article>

        <article class="where-card">
          <span>校内可以联系谁</span>
          <h2>优先选择真实、可信任的支持</h2>
          <div>
            <p v-for="contact in contacts" :key="contact.title">
              <Icon :name="contact.icon" :size="15" />
              <span><b>{{ contact.title }}</b><small>{{ contact.text }}</small></span>
            </p>
          </div>
        </article>
      </div>
    </section>

    <section class="journey" aria-label="参与流程">
      <div v-for="(step, index) in journey" :key="step.title" class="journey-step">
        <div class="journey-line" :class="{ 'is-before': index === 0 }" aria-hidden="true" />
        <span class="journey-number">{{ index + 1 }}</span>
        <div>
          <RouterLink v-if="step.to" :to="step.to">{{ step.title }}</RouterLink>
          <strong v-else>{{ step.title }}</strong>
          <small>{{ step.text }}</small>
        </div>
      </div>
    </section>

    <section class="emergency-strip">
      <Icon name="alert" :size="17" />
      <p><strong>紧急危机：</strong>立刻联系身边可信任的人，拨打 <b>120 / 110</b>，或前往校医院、医院急诊。</p>
      <RouterLink to="/help">查看求助方式 <Icon name="arrow-right" :size="13" /></RouterLink>
    </section>

    <p class="resource-note">平台边界：不做心理诊断，不输出风险等级；参与活动可以随时退出，不分享也可以。</p>
  </main>
</template>

<style scoped>
.resources-page {
  width: min(1180px, calc(100% - 56px));
  margin: 0 auto;
  padding: 46px 0 54px;
}

.resources-hero {
  padding-bottom: 30px;
  border-bottom: 1px solid var(--line);
}

.resources-hero > span {
  color: var(--brand-600);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.resources-hero h1 {
  max-width: 860px;
  margin-top: 14px;
  color: var(--ink-900);
  font-family: var(--font);
  font-size: clamp(40px, 5vw, 64px);
  font-weight: 800;
  letter-spacing: -0.055em;
  line-height: 1.12;
  text-wrap: balance;
}

.resources-hero > p {
  max-width: 680px;
  margin-top: 16px;
  color: var(--ink-600);
  font-size: 14.5px;
  line-height: 1.8;
}

.resources-collage {
  display: grid;
  align-items: start;
  gap: 14px;
  margin-top: 28px;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
}

.topic-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.topic-card {
  display: flex;
  min-height: 232px;
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
  flex-direction: column;
}

.topic-card--sage {
  border-color: transparent;
  color: #fff;
  background: var(--brand-500);
}

.topic-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.topic-number {
  display: grid;
  width: 30px;
  height: 30px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  color: var(--ink-600);
  font-size: 12px;
  font-weight: 700;
  place-items: center;
}

.topic-card--sage .topic-number {
  border-color: rgb(255 255 255 / 42%);
  color: #fff;
}

.topic-kicker {
  margin-top: 26px;
  color: var(--ink-500);
  font-size: 11px;
  font-weight: 600;
}

.topic-card--sage .topic-kicker {
  color: rgb(255 255 255 / 70%);
}

.topic-card h2 {
  margin-top: 5px;
  color: var(--ink-900);
  font-family: var(--font);
  font-size: 25px;
  font-weight: 800;
  letter-spacing: -0.04em;
  line-height: 1.2;
}

.topic-card--sage h2 {
  color: #fff;
}

.topic-text {
  margin-top: 9px;
  color: var(--ink-600);
  font-size: 12.5px;
  line-height: 1.75;
}

.topic-card--sage .topic-text {
  color: rgb(255 255 255 / 78%);
}

.resource-side {
  display: grid;
  gap: 14px;
}

.support-card {
  padding: 24px;
  border-radius: 18px;
  color: #fff;
  background: var(--brand-900);
}

.support-card > span {
  color: rgb(255 255 255 / 58%);
  font-size: 11px;
}

.support-card h2 {
  margin-top: 10px;
  font-family: var(--font);
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.045em;
  line-height: 1.2;
}

.support-card ul {
  display: grid;
  gap: 10px;
  margin: 22px 0 0;
  padding: 0;
  list-style: none;
}

.support-card li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: rgb(255 255 255 / 76%);
  font-size: 12.5px;
  line-height: 1.7;
}

.support-card li svg {
  flex: 0 0 auto;
  margin-top: 3px;
  color: var(--brand-300);
}

.where-card {
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
}

.where-card > span {
  color: var(--ink-500);
  font-size: 11px;
}

.where-card h2 {
  margin-top: 8px;
  color: var(--ink-900);
  font-family: var(--font);
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.04em;
  line-height: 1.25;
}

.where-card > div {
  display: grid;
  gap: 0;
  margin-top: 14px;
}

.where-card p {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 0;
  border-top: 1px solid var(--line);
}

.where-card p > svg {
  flex: 0 0 auto;
  margin-top: 3px;
  color: var(--brand-500);
}

.where-card p span {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.where-card b {
  color: var(--ink-900);
  font-size: 12.5px;
  font-weight: 700;
}

.where-card small {
  color: var(--ink-500);
  font-size: 11.5px;
  line-height: 1.6;
}

.journey {
  display: grid;
  gap: 0;
  margin-top: 28px;
  padding: 22px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.journey-step {
  position: relative;
  display: grid;
  align-items: start;
  gap: 10px;
  padding-right: 18px;
  grid-template-columns: auto 1fr;
}

.journey-line {
  position: absolute;
  top: 15px;
  right: 0;
  left: 34px;
  height: 1px;
  background: var(--line-strong);
}

.journey-line.is-before {
  display: none;
}

.journey-number {
  position: relative;
  z-index: 1;
  display: grid;
  width: 30px;
  height: 30px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  color: var(--ink-600);
  background: var(--surface);
  font-size: 12px;
  font-weight: 700;
  place-items: center;
}

.journey-step > div:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  padding-top: 3px;
}

.journey-step strong,
.journey-step a {
  color: var(--ink-900);
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.journey-step a:hover {
  color: var(--brand-700);
  text-decoration: underline;
}

.journey-step small {
  color: var(--ink-500);
  font-size: 11.5px;
  line-height: 1.55;
}

.emergency-strip {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 18px;
  padding: 12px 14px;
  border: 1px solid #e7d8c7;
  border-radius: 10px;
  color: #8a4d40;
  background: #fcf7ee;
  font-size: 12px;
  line-height: 1.6;
}

.emergency-strip p {
  flex: 1;
}

.emergency-strip b {
  color: #a14b3f;
}

.emergency-strip a {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 4px;
  color: #8a4d40;
  font-size: 11.5px;
  font-weight: 700;
  text-decoration: none;
}

.emergency-strip a:hover {
  text-decoration: underline;
}

.resource-note {
  margin-top: 14px;
  color: var(--ink-500);
  font-size: 11.5px;
  line-height: 1.7;
}

@media (max-width: 960px) {
  .resources-collage {
    grid-template-columns: 1fr;
  }

  .resource-side {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 700px) {
  .resources-page {
    width: calc(100% - 36px);
    padding-top: 28px;
  }

  .resources-hero h1 {
    font-size: 34px;
  }

  .topic-grid,
  .resource-side {
    grid-template-columns: 1fr;
  }

  .topic-card {
    min-height: 210px;
  }

  .journey {
    grid-template-columns: 1fr;
    gap: 14px;
    padding: 18px 0;
  }

  .journey-step {
    grid-template-columns: auto 1fr;
    padding-right: 0;
  }

  .journey-line {
    display: none;
  }

  .emergency-strip {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .emergency-strip p {
    flex-basis: calc(100% - 28px);
  }

  .emergency-strip a {
    margin-left: 26px;
  }
}
</style>
