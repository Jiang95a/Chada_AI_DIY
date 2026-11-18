<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import TracePulseCard from '@/components/public/TracePulseCard.vue'
import { listPublicActivities, type PublicActivitySummary } from '@/services/public'
import { useProjectStore } from '@/stores/projects'
import { emptyTrace, type ParticipantTrace } from '@/types/trace'
import campusWalk from '@/assets/photos/hero-campus.jpg'
import { coverForProject } from '@/utils/projectCover'

const router = useRouter()
const projectStore = useProjectStore()
const activities = ref<PublicActivitySummary[]>([])

const demoActivities: PublicActivitySummary[] = [
  {
    slug: 'heart-garden',
    title: '考试周心晴游园会',
    description: '用一场轻松的活动，把压力放下来一点。',
    activityType: '心晴游园会',
    coverTone: 'read',
    coverCode: 'WELLBEING',
    coverTitle: '给忙碌的心\n留一小块空地',
    status: 'published',
  },
  {
    slug: 'self-care-21',
    title: '21 天自我关怀练习',
    description: '每天完成一个很小的照顾自己的动作。',
    activityType: '自我关怀',
    coverTone: 'photo',
    coverCode: 'SELF CARE',
    coverTitle: '把照顾自己\n变成一件小事',
    status: 'published',
  },
  {
    slug: 'mindful-club',
    title: '心屿心理协会秋季招新',
    description: '和愿意倾听、也愿意行动的同学相遇。',
    activityType: '心理社团',
    coverTone: 'volunteer',
    coverCode: 'JOIN XINYU',
    coverTitle: '一起做有温度\n的校园心理活动',
    status: 'published',
  },
]

const visibleActivities = computed(() => activities.value.slice(0, 3))

function keepsFor(activity: PublicActivitySummary): string {
  const text = `${activity.title} ${activity.description ?? ''} ${activity.activityType ?? ''}`
  if (/招新|加入|社团/.test(text)) return '一次被看见的选择'
  if (/打卡|21 天|练习|连续/.test(text)) return '一段连续记录'
  if (/问答|知识|科普|测试/.test(text)) return '一次自我校准'
  if (/游园|压力|放松|心晴/.test(text)) return '一张压力地图'
  if (/职规|生涯|职业|方向/.test(text)) return '一条方向线索'
  return '一段只属于你的记录'
}

const demoNow = Date.now()
const demoTraces: ParticipantTrace[] = [
  {
    ...emptyTrace('demo', '', 'mental'),
    summary: '最近睡眠不太好，想先把自己缓下来。',
    change: '从「压力」到「睡眠」',
    tags: [
      { content: '压力', source: 'auto' as const, createdAt: demoNow - 9 * 86400000 },
      { content: '睡眠', source: 'auto' as const, createdAt: demoNow - 2 * 86400000 },
      { content: '家庭', source: 'auto' as const, createdAt: demoNow - 3600000 },
    ],
    sessionContext: {
      currentHint: '今晚先只改一件事：把手机放到够不着的地方。',
      availablePaths: ['sleep', 'breathe', 'note'],
    },
    history: [
      { at: demoNow - 9 * 86400000, kind: 'input' as const, tags: ['压力'], summary: '期末压力很大' },
      { at: demoNow - 5 * 86400000, kind: 'input' as const, tags: ['压力', '睡眠'], summary: '晚上老是睡不着' },
      { at: demoNow - 2 * 86400000, kind: 'input' as const, tags: ['睡眠'], summary: '睡得不太好' },
      {
        at: demoNow - 3600000,
        kind: 'input' as const,
        tags: ['睡眠', '家庭'],
        summary: '家里问起来有点烦',
        change: '从「压力」到「睡眠」',
      },
    ],
    updatedAt: demoNow,
  },
]

function coverImageFor(activity: PublicActivitySummary): string {
  return coverForProject({
    id: activity.slug,
    title: activity.title,
    description: activity.description,
    activityType: activity.activityType,
    coverTone: activity.coverTone,
  })
}

async function loadActivities(): Promise<void> {
  try {
    const remote = await listPublicActivities({ pageSize: 12 })
    const relevant = remote.filter((item) =>
      /心理|心晴|关怀|情绪|正念|匿名|团辅|游园|职规|生涯|职业|成长/.test(
        `${item.title} ${item.description ?? ''} ${item.activityType ?? ''}`
      )
    )
    if (relevant.length) {
      activities.value = relevant
      return
    }
  } catch {
    // 首页断网时继续展示本地内容。
  }
  const local = projectStore.projects
    .filter((project) => project.status === 'published')
    .map((project) => ({
      slug: project.publicSlug || project.document.slug,
      title: project.document.title,
      description: project.type,
      activityType: project.type,
      coverTone: project.coverTone,
      coverCode: project.coverCode,
      coverTitle: project.coverTitle,
      status: project.status,
      updatedAt: new Date(project.document.updatedAt).toISOString(),
    }))
  activities.value = local.length ? local : demoActivities
}

onMounted(() => {
  activities.value = demoActivities
  void loadActivities()
})
</script>

<template>
  <main class="landing-page">
    <section class="hero">
      <div class="hero-copy">
        <span class="hero-kicker">CAMPUS WELLBEING · 心屿同行</span>
        <h1>让每次参与，<br />都留下一点<span>回响</span>。</h1>
        <p>心屿同行帮社团搭建、发布心理与成长活动，也帮参与者记住自己是怎么想过来的。</p>
        <div class="hero-actions">
          <RouterLink class="hero-primary" to="/campus">
            发现近期活动 <Icon name="arrow-right" :size="17" />
          </RouterLink>
          <button type="button" @click="router.push('/trace')">
            <Icon name="clock" :size="16" />看看我的轨迹
          </button>
        </div>
        <div class="hero-trust">
          <span><Icon name="calendar" :size="13" />一场活动</span>
          <span><Icon name="layers" :size="13" />一条轨迹</span>
          <span><Icon name="lock" :size="13" />匿名可控</span>
        </div>
      </div>

      <figure class="hero-photo">
        <!-- 替换横版图：建议 1600×1000，保持 16:10，页面会按 cover 裁切。 -->
        <img :src="campusWalk" alt="两名学生背着书包走在秋日校园里" />
      </figure>
    </section>

    <section class="activities-section">
      <header>
        <div>
          <span>UPCOMING TOGETHER</span>
          <h2>近期活动</h2>
          <p>先看看这场会留下什么，再决定参不参加。</p>
        </div>
        <RouterLink to="/campus">查看全部 <Icon name="arrow-right" :size="15" /></RouterLink>
      </header>

      <div class="activity-grid" :class="{ 'is-compact': visibleActivities.length < 3 }">
        <RouterLink
          v-for="(activity, index) in visibleActivities"
          :key="activity.slug"
          :to="`/p/${activity.slug}`"
          class="activity-card"
          :class="[`activity-card--${index + 1}`, `tone-${activity.coverTone || 'read'}`]"
        >
          <img class="activity-cover" :src="coverImageFor(activity)" alt="" loading="lazy" />
          <span class="activity-type">{{ activity.activityType || '心晴活动' }}</span>
          <div>
            <small>0{{ index + 1 }}</small>
            <strong>{{ activity.title }}</strong>
            <p>{{ activity.description || '查看活动详情与参与方式。' }}</p>
            <span class="activity-keeps">会留下：{{ keepsFor(activity) }}</span>
          </div>
          <span class="activity-open">了解活动 <Icon name="arrow-right" :size="14" /></span>
        </RouterLink>
      </div>
    </section>

    <section class="trace-section">
      <div class="trace-copy">
        <span>MY TRACE · 个人轨迹</span>
        <h2>你不是每场活动，<br />都从零开始。</h2>
        <p>
          参加过的活动会留下标签、摘要和变化。下次打开，先看到上次那句话；想回看时，也知道自己是怎么走过来的。
        </p>
        <RouterLink to="/trace">看看我的轨迹 <Icon name="arrow-right" :size="15" /></RouterLink>
      </div>
      <div class="trace-demo">
        <TracePulseCard :traces="demoTraces" :show-link="false" />
        <small>示意：一名参与者 14 天的记录，只有本人能看到。</small>
      </div>
    </section>

  </main>
</template>

<style scoped>
.landing-page {
  color: var(--ink-900);
  background: transparent;
}

.hero {
  position: relative;
  display: grid;
  min-height: min(760px, calc(100vh - 70px));
  align-items: center;
  overflow: hidden;
  padding: 72px max(7vw, 34px);
  color: #fff;
  grid-template-columns: minmax(0, 1.05fr) minmax(280px, 0.55fr);
}

.hero-copy {
  position: relative;
  z-index: 1;
  max-width: 760px;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: rgb(229 245 235 / 82%);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.hero-kicker i {
  width: 26px;
  height: 1px;
  background: var(--brand-300);
}

.hero h1 {
  margin: 20px 0 18px;
  max-width: 760px;
  font-family: var(--font-serif);
  font-size: clamp(48px, 5.6vw, 72px);
  font-weight: 650;
  letter-spacing: -0.075em;
  line-height: 1.1;
  text-wrap: balance;
}

.hero h1 span {
  color: #b6dfc4;
}

.hero-copy > p {
  max-width: 620px;
  color: rgb(255 255 255 / 82%);
  font-size: 15px;
  line-height: 1.9;
}

.hero-actions {
  display: flex;
  gap: 9px;
  margin-top: 26px;
}

.hero-primary,
.hero-actions button {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  gap: 7px;
  padding: 0 17px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 750;
}

.hero-primary {
  color: #173429;
  background: var(--brand-100);
}

.hero-actions button {
  border: 1px solid rgb(255 255 255 / 30%);
  color: #fff;
  background: rgb(255 255 255 / 9%);
  cursor: pointer;
}

.hero-trust {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 26px;
}

.hero-trust span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: rgb(255 255 255 / 74%);
  font-size: 12px;
}

.hero-invite {
  position: relative;
  z-index: 1;
  align-self: end;
  justify-self: end;
  width: min(310px, 100%);
  padding: 22px;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 10px;
  color: #fff;
  background: rgb(227 237 226 / 13%);
  backdrop-filter: blur(16px);
}

.hero-invite > span {
  color: rgb(255 255 255 / 72%);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.hero-invite strong {
  display: block;
  margin-top: 13px;
  font-family: var(--font-serif);
  font-size: 23px;
  letter-spacing: -0.04em;
}

.hero-invite p {
  margin-top: 8px;
  color: rgb(255 255 255 / 82%);
  font-size: 13px;
  line-height: 1.7;
}

.hero-invite > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
}

.hero-invite em {
  padding: 4px 8px;
  border-radius: 999px;
  color: #d7eadc;
  background: rgb(255 255 255 / 10%);
  font-size: 11px;
  font-style: normal;
}

.hero-trace-spark {
  display: block;
  width: 100%;
  height: 40px;
  margin-top: 14px;
}

.intro-strip,
.activities-section,
.trace-section,
.nearby-section,
.maker-section,
.safety-section {
  width: min(1220px, calc(100% - 10vw));
  margin: 0 auto;
}

.intro-strip {
  width: 100%;
  padding: 54px max(5vw, calc((100vw - 1220px) / 2)) 46px;
  background: var(--surface-warm);
  text-align: center;
}

.intro-strip > span,
.activities-section > header span,
.nearby-copy > span,
.trace-copy > span,
.maker-section > div:first-child > span,
.safety-section > div > span {
  color: var(--brand-500);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.intro-strip h2 {
  max-width: 780px;
  margin: 13px auto 34px;
  font-family: var(--font-serif);
  font-size: clamp(31px, 4vw, 46px);
  letter-spacing: -0.055em;
  line-height: 1.35;
  text-wrap: balance;
}

.intro-points {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--line);
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
}

.intro-points article {
  display: flex;
  min-height: 150px;
  align-items: flex-start;
  gap: 12px;
  padding: 22px;
  background: var(--surface);
  text-align: left;
}

.intro-points i {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 8px;
  color: var(--brand-600);
  background: var(--brand-50);
  place-items: center;
}

.intro-points strong {
  display: block;
  font-size: 14px;
}

.intro-points small {
  display: block;
  margin-top: 7px;
  color: var(--ink-600);
  font-size: 12.5px;
  line-height: 1.7;
}

.activities-section {
  padding: 44px 0 56px;
  border-top: 1px solid var(--line);
}

.activities-section > header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.activities-section h2 {
  margin-top: 8px;
  font-family: var(--font-serif);
  font-size: 35px;
  letter-spacing: -0.045em;
}

.activities-section header p {
  margin-top: 7px;
  color: var(--ink-600);
  font-size: 13px;
}

.activities-section > header > a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--brand-600);
  font-size: 12px;
}

.activity-grid {
  display: grid;
  gap: 12px;
  margin-top: 24px;
  grid-auto-rows: 190px;
  grid-template-columns: repeat(12, minmax(0, 1fr));
}

.activity-grid.is-compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: 330px;
}

.activity-grid.is-compact .activity-card--1,
.activity-grid.is-compact .activity-card--2 {
  grid-column: auto;
  grid-row: auto;
}

.activity-card {
  position: relative;
  display: flex;
  min-height: 0;
  justify-content: space-between;
  overflow: hidden;
  padding: 22px;
  border-radius: 0;
  color: #fff;
  flex-direction: column;
}

.activity-card--1 {
  grid-column: span 7;
  grid-row: span 2;
}

.activity-card--2,
.activity-card--3 {
  grid-column: span 5;
}

.activity-card--2,
.activity-card--3 {
  grid-row: span 1;
}

.activity-cover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.activity-card::before {
  position: absolute;
  inset: 0;
  background: rgb(20 42 33 / 28%);
  content: '';
}

.activity-card::after {
  position: absolute;
  right: -80px;
  bottom: -100px;
  width: 250px;
  height: 250px;
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 50%;
  content: '';
}

.activity-type,
.activity-card > div,
.activity-open {
  position: relative;
  z-index: 1;
}

.activity-type {
  align-self: flex-start;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgb(255 255 255 / 15%);
  font-size: 11px;
  backdrop-filter: blur(6px);
}

.activity-card > div {
  max-width: 500px;
  padding: 13px 15px;
  color: var(--brand-900);
  background: rgb(255 253 249 / 90%);
}

.activity-card > div > small {
  color: var(--ink-500);
  font-family: var(--font-serif);
  font-size: 22px;
}

.activity-card > div > strong {
  display: block;
  margin-top: 10px;
  font-family: var(--font-serif);
  font-size: 20px;
  letter-spacing: -0.045em;
  line-height: 1.22;
}

.activity-card--1 > div > strong {
  font-size: clamp(33px, 4vw, 48px);
}

.activity-card > div > p {
  max-width: 420px;
  margin-top: 10px;
  color: var(--ink-600);
  font-size: 13px;
  line-height: 1.75;
}

.activity-keeps {
  display: inline-flex;
  margin-top: 9px;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 11.5px;
  font-weight: 600;
}

.activity-card:not(.activity-card--1) > div > p {
  display: none;
}

.activity-open {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  align-self: flex-start;
  margin-top: 18px;
  padding: 5px 8px;
  color: #fff;
  background: rgb(20 42 33 / 76%);
  font-size: 11.5px;
}

.nearby-section {
  display: grid;
  gap: 50px;
  padding: 48px 0;
  border-top: 1px solid var(--line);
  grid-template-columns: 0.8fr 1.2fr;
}

.trace-section {
  display: grid;
  align-items: center;
  gap: 46px;
  margin-top: 46px;
  padding: 44px 0 48px;
  border-top: 1px solid var(--line);
  grid-template-columns: 0.72fr 1.28fr;
}

.trace-copy h2 {
  margin: 12px 0 0;
  font-family: var(--font-serif);
  font-size: clamp(30px, 3.4vw, 42px);
  line-height: 1.3;
  letter-spacing: -0.05em;
}

.trace-copy p {
  max-width: 440px;
  margin: 15px 0 0;
  color: var(--ink-600);
  font-size: 13.5px;
  line-height: 1.85;
}

.trace-copy > a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 20px;
  padding: 9px 14px;
  border-radius: var(--radius-button);
  background: var(--brand-600);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.trace-copy > a:hover {
  background: var(--brand-700);
}

.trace-demo {
  min-width: 0;
}

.trace-demo > small {
  display: block;
  margin-top: 8px;
  color: var(--ink-500);
  font-size: 11.5px;
  text-align: right;
}

.nearby-copy h2,
.trace-copy h2,
.maker-section h2,
.safety-section h2 {
  margin-top: 10px;
  font-family: var(--font-serif);
  font-size: 37px;
  letter-spacing: -0.05em;
  line-height: 1.3;
  text-wrap: balance;
}

.nearby-copy p,
.maker-section p {
  margin-top: 13px;
  color: var(--ink-600);
  font-size: 13.5px;
  line-height: 1.8;
}

.nearby-links {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  border-radius: 0;
  background: var(--line);
  overflow: hidden;
}

.nearby-links a {
  display: grid;
  align-items: center;
  gap: 13px;
  padding: 18px;
  background: var(--surface);
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.nearby-links a > i {
  display: grid;
  width: 40px;
  height: 40px;
  border-radius: 0;
  color: var(--brand-600);
  background: var(--brand-50);
  place-items: center;
}

.nearby-links a > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.nearby-links strong {
  font-size: 13.5px;
}

.nearby-links small {
  margin-top: 4px;
  color: var(--ink-500);
  font-size: 12px;
}

.nearby-links a > svg {
  color: var(--ink-300);
}

.maker-section {
  display: grid;
  align-items: center;
  gap: 45px;
  padding: 34px;
  border: 1px solid var(--line);
  border-radius: 0;
  color: var(--ink-900);
  background: var(--surface-mist);
  grid-template-columns: 1fr 0.9fr;
}

.maker-section > div:first-child > span {
  color: var(--brand-700);
}

.maker-section p {
  color: var(--ink-600);
}

.maker-section button {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  gap: 6px;
  margin-top: 20px;
  padding: 0 15px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  color: var(--brand-700);
  background: var(--surface);
  font-size: 13px;
  cursor: pointer;
}

.maker-flow {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.maker-flow article {
  display: flex;
  align-items: flex-start;
  gap: 13px;
  padding: 15px 16px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
}

.maker-flow b {
  color: var(--brand-600);
  font-family: var(--font-serif);
  font-size: 13px;
}

.maker-flow strong {
  display: block;
  color: var(--ink-900);
  font-size: 13.5px;
}

.maker-flow small {
  display: block;
  margin-top: 3px;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.65;
}

.safety-section {
  display: grid;
  gap: 55px;
  margin-top: 46px;
  margin-bottom: 54px;
  padding-top: 40px;
  border-top: 1px solid var(--line);
  grid-template-columns: 0.8fr 1.2fr;
}

.safety-list {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--line);
  overflow: hidden;
}

.safety-list article {
  display: grid;
  gap: 4px 12px;
  padding: 17px;
  background: var(--surface);
  grid-template-columns: auto 1fr;
}

.safety-list b {
  grid-row: 1 / 3;
  color: var(--brand-400);
  font-family: var(--font-serif);
}

.safety-list span {
  display: contents;
}

.safety-list strong {
  font-size: 13px;
}

.safety-list small {
  color: var(--ink-600);
  font-size: 12px;
  line-height: 1.65;
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .hero-invite {
    justify-self: start;
    margin-top: 36px;
  }

  .intro-points {
    grid-template-columns: 1fr;
  }

  .activity-grid {
    grid-auto-rows: auto;
    grid-template-columns: 1fr;
  }

  .activity-card--1 {
    min-height: 360px;
    grid-column: auto;
    grid-row: auto;
  }

  .activity-card--2,
  .activity-card--3 {
    min-height: 250px;
    grid-column: auto;
    grid-row: auto;
  }

  .activity-grid.is-compact {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
  }

  .activity-grid.is-compact .activity-card--1,
  .activity-grid.is-compact .activity-card--2 {
    grid-column: auto;
    grid-row: auto;
  }

  .trace-section {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .trace-demo > small {
    text-align: left;
  }

  .activity-grid.is-compact {
    grid-template-rows: none;
  }

  .activity-grid.is-compact .activity-card--1,
  .activity-grid.is-compact .activity-card--2 {
    grid-row: auto;
  }

  .nearby-section,
  .maker-section,
  .safety-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .hero {
    min-height: 680px;
    padding: 56px 18px;
  }

  .hero h1 {
    font-size: 40px;
    letter-spacing: -0.06em;
  }

  .nearby-copy h2,
  .trace-copy h2,
  .maker-section h2,
  .safety-section h2 {
    font-size: 30px;
  }

  .maker-section h2 {
    font-size: 28px;
  }

  .hero-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-primary,
  .hero-actions button {
    justify-content: center;
  }

  .intro-strip,
  .activities-section,
  .nearby-section,
  .maker-section,
  .safety-section {
    width: calc(100% - 36px);
  }

  .intro-strip {
    padding: 42px 18px 36px;
  }

  .activities-section > header {
    align-items: flex-start;
    flex-direction: column;
  }

  .maker-section {
    padding: 26px 20px;
  }
}
.landing-page {
  width: 100%;
  max-width: none;
  margin: 0;
  color: var(--ink-900);
  background: transparent;
}

.landing-page .hero {
  width: min(1280px, calc(100% - 36px));
  min-height: 0;
  align-items: center;
  gap: 42px;
  margin: 0 auto;
  padding: 54px clamp(28px, 4vw, 56px);
  border-bottom: 0;
  color: var(--ink-900);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg));
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
}

.landing-page .hero-copy {
  display: flex;
  max-width: none;
  justify-content: center;
  padding: 32px 0;
  flex-direction: column;
}

.hero-kicker {
  color: var(--brand-600);
  font-size: 11.5px;
  letter-spacing: 0.12em;
}

.hero h1 {
  max-width: 660px;
  margin: 18px 0 16px;
  color: var(--ink-900);
  font-family: var(--font);
  font-size: clamp(42px, 4.6vw, 64px);
  font-weight: 800;
  letter-spacing: -0.055em;
  line-height: 1.08;
}

.hero h1 span {
  color: var(--brand-600);
  white-space: nowrap;
}

.hero-copy > p {
  max-width: 520px;
  color: var(--ink-600);
  font-size: 15px;
  line-height: 1.85;
}

.hero-primary,
.hero-actions button {
  min-height: 44px;
  border-radius: 2px;
  font-size: 13px;
  font-weight: 700;
}

.hero-primary {
  background: var(--brand-700);
  color: #fff;
}

.hero-actions button {
  border: 1px solid var(--ink-300);
  background: #fff;
  color: var(--ink-900);
}

.hero-trust span {
  color: var(--ink-500);
  font-size: 12px;
}

.hero-photo {
  width: 100%;
  aspect-ratio: 16 / 10;
  margin: 0;
  padding: 8px;
  border: 1px solid var(--line-strong);
  background: var(--surface-soft);
}

.hero-photo img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.85) contrast(0.98);
}

.hero-visual,
.hero-shade,
.hero-invite {
  display: none;
}

.intro-strip {
  padding: 46px calc(max(24px, (100vw - 1240px) / 2) + 44px);
  border: 0;
  border-bottom: 1px solid var(--line);
  background: var(--surface-soft);
  text-align: left;
}

.intro-strip h2 {
  max-width: 760px;
  margin: 12px 0 26px;
  font-family: var(--font);
  font-size: clamp(28px, 3.2vw, 38px);
  font-weight: 800;
  letter-spacing: -0.045em;
  line-height: 1.25;
}

.intro-points {
  gap: 0;
  border: 0;
  background: transparent;
}

.intro-points article {
  min-height: 0;
  padding: 18px 24px 18px 0;
  border-top: 2px solid var(--ink-900);
  background: transparent;
}

.intro-points i {
  width: auto;
  height: auto;
  border-radius: 0;
  background: transparent;
  color: var(--brand-600);
}

.intro-points strong {
  font-family: var(--font);
  font-size: 15px;
  font-weight: 800;
}

.intro-points small {
  color: var(--ink-600);
  font-size: 13px;
}

.activities-section {
  width: 100%;
  margin: 0;
  padding: 50px calc(max(24px, (100vw - 1240px) / 2) + 44px) 54px;
  border: 0;
  background: transparent;
}

.activities-section h2 {
  font-family: var(--font);
  font-size: 34px;
  font-weight: 800;
}

.activity-grid.is-compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: auto;
}

.activity-card {
  min-height: 0;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 0;
  background: color-mix(in srgb, var(--surface) 88%, var(--brand-50));
  color: var(--ink-900);
}

.activity-cover {
  position: static;
  width: 100%;
  height: 190px;
  object-fit: cover;
}

.activity-card::before,
.activity-card::after {
  display: none;
}

.activity-type {
  margin-top: 14px;
  border-radius: 2px;
  background: var(--brand-50);
  color: var(--brand-700);
  backdrop-filter: none;
}

.activity-card > div {
  max-width: none;
  padding: 12px 0 0;
  background: transparent;
}

.activity-card > div > small {
  color: var(--ink-500);
  font-family: var(--font);
  font-size: 12px;
  font-weight: 700;
}

.activity-card > div > strong {
  margin-top: 5px;
  font-family: var(--font);
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.035em;
}

.activity-card--1 > div > strong {
  font-size: 24px;
}

.activity-card > div > p {
  margin-top: 7px;
  font-size: 13px;
}

.activity-keeps {
  margin-top: 8px;
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: var(--brand-700);
  font-size: 12px;
}

.activity-card:not(.activity-card--1) > div > p {
  display: block;
}

.activity-open {
  margin-top: 12px;
  padding: 0;
  background: transparent;
  color: var(--brand-700);
  font-size: 12px;
}

.nearby-section {
  width: 100%;
  gap: 40px;
  margin: 0;
  padding: 46px calc(max(24px, (100vw - 1240px) / 2) + 44px);
  border: 0;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}

.nearby-copy h2,
.trace-copy h2,
.maker-section h2,
.safety-section h2 {
  font-family: var(--font);
  font-size: 34px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.nearby-links {
  gap: 0;
  border: 0;
  background: transparent;
}

.nearby-links a {
  padding: 15px 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
}

.nearby-links a > i {
  width: auto;
  height: auto;
  border-radius: 0;
  background: transparent;
}

.nearby-links strong {
  font-size: 14px;
}

.nearby-links small {
  color: var(--ink-600);
  font-size: 12px;
}

.trace-section {
  width: 100%;
  margin: 0;
  padding: 48px calc(max(24px, (100vw - 1240px) / 2) + 44px);
  border: 0;
  background: transparent;
}

.trace-demo :deep(.pulse) {
  border-color: var(--line-strong);
  border-radius: 0;
  box-shadow: none;
}

.trace-demo > small {
  color: var(--ink-500);
}

.safety-section {
  width: 100%;
  margin: 0;
  padding: 46px calc(max(24px, (100vw - 1240px) / 2) + 44px);
  border: 0;
  background: transparent;
}

.safety-list {
  gap: 0;
  border: 0;
  background: transparent;
}

.safety-list article {
  padding: 15px 0;
  border-top: 1px solid var(--line);
  background: transparent;
}

.safety-list b {
  color: var(--brand-500);
  font-family: var(--font);
  font-size: 12px;
}

.safety-list strong {
  font-size: 14px;
}

.safety-list small {
  color: var(--ink-600);
  font-size: 12.5px;
}

.maker-section {
  border-radius: 0;
  background: var(--surface-mist);
}

.maker-section button {
  border-radius: 2px;
}

.maker-flow {
  gap: 0;
}

.maker-flow article {
  padding: 14px 0;
  border: 0;
  border-top: 1px solid var(--line);
  border-radius: 0;
  background: transparent;
}

@media (max-width: 900px) {
  .landing-page .hero {
    gap: 28px;
    padding: 36px 24px 42px;
    grid-template-columns: 1fr;
  }

  .landing-page .hero-copy {
    padding: 0;
  }

  .intro-strip,
  .activities-section,
  .trace-section,
  .safety-section,
  .nearby-section {
    padding-right: 32px;
    padding-left: 32px;
  }

  .hero-photo {
    min-height: 0;
  }

  .intro-points {
    grid-template-columns: 1fr;
  }

  .intro-points article {
    padding-right: 0;
  }

  .activity-grid.is-compact {
    grid-template-columns: 1fr;
  }

  .activity-cover {
    height: 210px;
  }
}

@media (max-width: 600px) {
  .hero h1 {
    font-size: 40px;
  }

  .hero-photo {
    min-height: 0;
  }

  .intro-strip,
  .activities-section,
  .trace-section,
  .safety-section,
  .nearby-section {
    padding-right: 18px;
    padding-left: 18px;
  }

  .nearby-copy h2,
  .trace-copy h2,
  .maker-section h2,
  .safety-section h2 {
    font-size: 28px;
  }
}

</style>
