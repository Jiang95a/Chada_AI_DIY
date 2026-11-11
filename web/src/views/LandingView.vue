<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { getAccessToken } from '@/services/http'
import { listPublicActivities, type PublicActivitySummary } from '@/services/public'
import { useProjectStore } from '@/stores/projects'

const router = useRouter()
const projectStore = useProjectStore()
const activities = ref<PublicActivitySummary[]>([])
const authenticated = computed(() => Boolean(getAccessToken()))

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
const featuredActivity = computed(() => visibleActivities.value[0])

async function loadActivities(): Promise<void> {
  try {
    const remote = await listPublicActivities({ pageSize: 12 })
    const wellbeing = remote.filter((item) =>
      /心理|心晴|关怀|情绪|正念|匿名|团辅|游园/.test(
        `${item.title} ${item.description ?? ''} ${item.activityType ?? ''}`
      )
    )
    if (wellbeing.length) {
      activities.value = wellbeing
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

function goProtected(path: string): void {
  void router.push(authenticated.value ? path : { path: '/login', query: { redirect: path } })
}

onMounted(() => {
  activities.value = demoActivities
  void loadActivities()
})
</script>

<template>
  <main class="landing-page">
    <section class="hero">
      <div class="hero-visual" aria-hidden="true" />
      <div class="hero-shade" />
      <div class="hero-copy">
        <span class="hero-kicker"><i />A QUIET PLACE FOR CAMPUS</span>
        <h1>让每一份心事，<br />都有<span>温柔的回响</span>。</h1>
        <p>心屿同行服务校园心理社团和心理委员。发起一场活动，留出一块可以倾听、表达和休息的空间。</p>
        <div class="hero-actions">
          <RouterLink class="hero-primary" to="/campus">
            发现近期活动 <Icon name="arrow-right" :size="17" />
          </RouterLink>
          <button type="button" @click="goProtected('/projects/new')">
            <Icon name="plus" :size="16" />发起心理活动
          </button>
        </div>
        <div class="hero-trust">
          <span><Icon name="lock" :size="13" />隐私优先</span>
          <span><Icon name="check" :size="13" />不做心理诊断</span>
          <span><Icon name="users" :size="13" />支持匿名参与</span>
        </div>
      </div>

      <RouterLink v-if="featuredActivity" :to="`/p/${featuredActivity.slug}`" class="hero-invite">
        <span>WEEKLY INVITATION</span>
        <strong>{{ featuredActivity.title }}</strong>
        <p>{{ featuredActivity.description }}</p>
        <div>
          <em>{{ featuredActivity.activityType || '心晴活动' }}</em
          ><Icon name="arrow-right" :size="16" />
        </div>
      </RouterLink>
    </section>

    <section class="intro-strip">
      <span>心屿的方式</span>
      <h2>不急着给答案，先把参与和表达变得更容易。</h2>
      <div class="intro-points">
        <article>
          <i><Icon name="calendar" :size="19" /></i>
          <div><strong>活动更容易被看见</strong><small>近期可报名活动集中展示，减少寻找和犹豫。</small></div>
        </article>
        <article>
          <i><Icon name="lock" :size="19" /></i>
          <div><strong>参与边界更清楚</strong><small>匿名、同意、隐私与退出说明随活动一起出现。</small></div>
        </article>
        <article>
          <i><Icon name="users" :size="19" /></i>
          <div><strong>组织者更容易上手</strong><small>从活动信息到页面和海报，都能在页面工坊完成。</small></div>
        </article>
      </div>
    </section>

    <section class="activities-section">
      <header>
        <div>
          <span>UPCOMING TOGETHER</span>
          <h2>最近可以参与的心晴活动</h2>
          <p>按自己的节奏来，不需要发言，也不需要完成任务。</p>
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
          <span class="activity-type">{{ activity.activityType || '心晴活动' }}</span>
          <div>
            <small>0{{ index + 1 }}</small>
            <strong>{{ activity.title }}</strong>
            <p>{{ activity.description || '查看活动详情与参与方式。' }}</p>
          </div>
          <span class="activity-open">了解活动 <Icon name="arrow-right" :size="14" /></span>
        </RouterLink>
      </div>
    </section>

    <section class="nearby-section">
      <div class="nearby-copy">
        <span>你在心屿可以做什么</span>
        <h2>不只找活动，也可以停一下。</h2>
        <p>心晴工具、心理资源和心屿社团介绍，都在同一个安静的空间里。</p>
      </div>
      <div class="nearby-links">
        <RouterLink to="/tools">
          <i><Icon name="spark" :size="20" /></i>
          <div><strong>心晴工具</strong><small>呼吸、感受和片刻自我照顾</small></div>
          <Icon name="arrow-right" :size="16" />
        </RouterLink>
        <RouterLink to="/resources">
          <i><Icon name="help" :size="20" /></i>
          <div><strong>心理资源</strong><small>科普、校内支持与求助方向</small></div>
          <Icon name="arrow-right" :size="16" />
        </RouterLink>
        <RouterLink to="/about">
          <i><Icon name="users" :size="20" /></i>
          <div><strong>关于心屿</strong><small>社团理念、活动方式和边界</small></div>
          <Icon name="arrow-right" :size="16" />
        </RouterLink>
      </div>
    </section>

    <section class="maker-section">
      <div>
        <span>FOR ORGANIZERS</span>
        <h2>一场活动，从想法到分享。</h2>
        <p>组织者只需要填写活动信息。页面工坊会帮助生成 H5 和分享海报，隐私说明也随内容一起准备。</p>
        <button type="button" @click="goProtected('/projects/new')">
          打开页面工坊 <Icon name="arrow-right" :size="16" />
        </button>
      </div>
      <div class="maker-preview">
        <div class="maker-window-bar"><i /><i /><i /></div>
        <span>活动草稿</span>
        <strong>考试周心晴游园会</strong>
        <div class="maker-blocks"><i /><i /><i /></div>
        <small>页面说明、报名与隐私提示已准备</small>
      </div>
    </section>

    <section class="safety-section">
      <div>
        <span>WELLBEING IS NOT A SCORE</span>
        <h2>关注参与，不评判内心。</h2>
      </div>
      <div class="safety-list">
        <article>
          <b>01</b><span><strong>不做心理诊断</strong><small>平台只支持活动组织，不提供诊断或治疗结论。</small></span>
        </article>
        <article><b>02</b><strong>只收集必要信息</strong><small>活动开始前就能看到信息用途和查看范围。</small></article>
        <article><b>03</b><strong>匿名不等于失联</strong><small>参与者可以主动选择“希望有人联系我”。</small></article>
      </div>
    </section>
  </main>
</template>

<style scoped>
.landing-page {
  color: var(--ink-900);
  background: #f7f5f0;
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

.hero-visual {
  position: absolute;
  inset: 0;
  background: url('@/assets/heart-island-hero.svg') center / cover no-repeat;
  transform: scale(1.015);
}

.hero-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgb(18 41 32 / 88%) 0%, rgb(22 50 39 / 68%) 46%, rgb(24 51 42 / 18%) 78%);
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
  color: rgb(229 245 235 / 72%);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.hero-kicker i {
  width: 26px;
  height: 1px;
  background: #9ac8aa;
}

.hero h1 {
  margin: 20px 0 18px;
  font-family: var(--font-serif);
  font-size: clamp(48px, 6.2vw, 84px);
  font-weight: 650;
  letter-spacing: -0.075em;
  line-height: 1.08;
}

.hero h1 span {
  color: #b6dfc4;
}

.hero-copy > p {
  max-width: 620px;
  color: rgb(255 255 255 / 72%);
  font-size: 14px;
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
  background: #d7eadc;
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
  color: rgb(255 255 255 / 58%);
  font-size: 10px;
}

.hero-invite {
  position: relative;
  z-index: 1;
  align-self: end;
  justify-self: end;
  width: min(310px, 100%);
  padding: 22px;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 20px;
  color: #fff;
  background: rgb(227 237 226 / 13%);
  box-shadow: 0 20px 50px -35px rgb(0 0 0 / 55%);
  backdrop-filter: blur(16px);
}

.hero-invite > span {
  color: rgb(255 255 255 / 48%);
  font-size: 7px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.hero-invite strong {
  display: block;
  margin-top: 13px;
  font-family: var(--font-serif);
  font-size: 22px;
  letter-spacing: -0.04em;
}

.hero-invite p {
  margin-top: 8px;
  color: rgb(255 255 255 / 58%);
  font-size: 9px;
  line-height: 1.7;
}

.hero-invite > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
}

.hero-invite em {
  padding: 4px 7px;
  border-radius: 999px;
  color: #d7eadc;
  background: rgb(255 255 255 / 10%);
  font-size: 7px;
  font-style: normal;
}

.intro-strip,
.activities-section,
.nearby-section,
.maker-section,
.safety-section {
  width: min(1220px, calc(100% - 10vw));
  margin: 0 auto;
}

.intro-strip {
  padding: 74px 0 62px;
  text-align: center;
}

.intro-strip > span,
.activities-section > header span,
.nearby-copy > span,
.maker-section > div:first-child > span,
.safety-section > div > span {
  color: var(--brand-500);
  font-size: 8px;
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
}

.intro-points {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  border-radius: 20px;
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
  background: #fbfaf6;
  text-align: left;
}

.intro-points i {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 12px;
  color: var(--brand-600);
  background: var(--brand-50);
  place-items: center;
}

.intro-points strong {
  display: block;
  font-size: 12px;
}

.intro-points small {
  display: block;
  margin-top: 7px;
  color: var(--ink-400);
  font-size: 9px;
  line-height: 1.7;
}

.activities-section {
  padding: 56px 0 74px;
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
  color: var(--ink-400);
  font-size: 9px;
}

.activities-section > header > a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--brand-600);
  font-size: 9px;
}

.activity-grid {
  display: grid;
  gap: 12px;
  margin-top: 24px;
  grid-template-columns: 1.1fr 0.9fr;
}

.activity-grid.is-compact {
  grid-template-rows: minmax(390px, auto);
}

.activity-grid.is-compact .activity-card--1 {
  min-height: 390px;
  grid-row: auto;
}

.activity-card {
  position: relative;
  display: flex;
  min-height: 270px;
  justify-content: space-between;
  overflow: hidden;
  padding: 22px;
  border-radius: 21px;
  color: #fff;
  flex-direction: column;
}

.activity-card--1 {
  min-height: 554px;
  grid-row: span 2;
}

.activity-card.tone-read {
  background: #547d63;
}

.activity-card.tone-photo {
  background: #967965;
}

.activity-card.tone-volunteer {
  background: #527d78;
}

.activity-card::after {
  position: absolute;
  right: -80px;
  bottom: -100px;
  width: 250px;
  height: 250px;
  border: 1px solid rgb(255 255 255 / 24%);
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
  font-size: 10px;
  backdrop-filter: blur(6px);
}

.activity-card > div {
  max-width: 500px;
}

.activity-card > div > small {
  color: rgb(255 255 255 / 38%);
  font-family: var(--font-serif);
  font-size: 22px;
}

.activity-card > div > strong {
  display: block;
  margin-top: 10px;
  font-family: var(--font-serif);
  font-size: 27px;
  letter-spacing: -0.045em;
  line-height: 1.22;
}

.activity-card--1 > div > strong {
  font-size: clamp(33px, 4vw, 48px);
}

.activity-card > div > p {
  max-width: 420px;
  margin-top: 10px;
  color: rgb(255 255 255 / 62%);
  font-size: 12px;
  line-height: 1.75;
}

.activity-open {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  align-self: flex-start;
  margin-top: 18px;
  color: rgb(255 255 255 / 72%);
  font-size: 10px;
}

.nearby-section {
  display: grid;
  gap: 50px;
  padding: 66px 0;
  border-top: 1px solid var(--line);
  grid-template-columns: 0.8fr 1.2fr;
}

.nearby-copy h2,
.maker-section h2,
.safety-section h2 {
  margin-top: 10px;
  font-family: var(--font-serif);
  font-size: 37px;
  letter-spacing: -0.05em;
  line-height: 1.3;
}

.nearby-copy p,
.maker-section p {
  margin-top: 13px;
  color: var(--ink-500);
  font-size: 10px;
  line-height: 1.8;
}

.nearby-links {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--line);
  overflow: hidden;
}

.nearby-links a {
  display: grid;
  align-items: center;
  gap: 13px;
  padding: 18px;
  background: #fff;
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.nearby-links a > i {
  display: grid;
  width: 40px;
  height: 40px;
  border-radius: 12px;
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
  font-size: 11px;
}

.nearby-links small {
  margin-top: 4px;
  color: var(--ink-400);
  font-size: 8px;
}

.nearby-links a > svg {
  color: var(--ink-300);
}

.maker-section {
  display: grid;
  align-items: center;
  gap: 45px;
  padding: 44px;
  border-radius: 28px;
  color: #fff;
  background: #294b3d;
  grid-template-columns: 1fr 0.9fr;
}

.maker-section > div:first-child > span {
  color: #b8d8c2;
}

.maker-section p {
  color: rgb(255 255 255 / 62%);
}

.maker-section button {
  display: inline-flex;
  min-height: 39px;
  align-items: center;
  gap: 6px;
  margin-top: 20px;
  padding: 0 14px;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 999px;
  color: #fff;
  background: rgb(255 255 255 / 9%);
  font-size: 9px;
  cursor: pointer;
}

.maker-preview {
  padding: 18px;
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 16px;
  background: rgb(255 255 255 / 10%);
  backdrop-filter: blur(10px);
}

.maker-window-bar {
  display: flex;
  gap: 4px;
  margin-bottom: 18px;
}

.maker-window-bar i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgb(255 255 255 / 32%);
}

.maker-preview > span {
  color: rgb(255 255 255 / 40%);
  font-size: 7px;
}

.maker-preview > strong {
  display: block;
  margin-top: 6px;
  font-family: var(--font-serif);
  font-size: 20px;
}

.maker-blocks {
  display: grid;
  gap: 5px;
  margin: 16px 0;
}

.maker-blocks i {
  height: 24px;
  border-radius: 6px;
  background: rgb(255 255 255 / 9%);
}

.maker-blocks i:nth-child(2) {
  height: 42px;
}

.maker-preview > small {
  color: rgb(255 255 255 / 44%);
  font-size: 7px;
}

.safety-section {
  display: grid;
  gap: 55px;
  margin-top: 64px;
  margin-bottom: 70px;
  padding-top: 52px;
  border-top: 1px solid var(--line);
  grid-template-columns: 0.8fr 1.2fr;
}

.safety-list {
  display: grid;
  gap: 1px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--line);
  overflow: hidden;
}

.safety-list article {
  display: grid;
  gap: 4px 12px;
  padding: 17px;
  background: #fff;
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
  font-size: 10px;
}

.safety-list small {
  color: var(--ink-400);
  font-size: 8px;
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
    grid-template-columns: 1fr;
  }

  .activity-card--1 {
    min-height: 360px;
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
    font-size: 46px;
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
    padding-top: 50px;
  }

  .activities-section > header {
    align-items: flex-start;
    flex-direction: column;
  }

  .maker-section {
    padding: 26px 20px;
  }
}
</style>
