<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import WellbeingToolkit from '@/components/public/WellbeingToolkit.vue'
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
    title: '21 天自我关怀打卡',
    description: '每天完成一个很小的照顾自己的动作。',
    activityType: '自我关怀',
    coverTone: 'photo',
    coverCode: 'SELF CARE',
    coverTitle: '把照顾自己\n变成一件小事',
    status: 'published',
  },
  {
    slug: 'psychology-club',
    title: '心屿心理协会秋季招新',
    description: '和一群愿意倾听、也愿意行动的同学相遇。',
    activityType: '心理社团',
    coverTone: 'volunteer',
    coverCode: 'JOIN XINYU',
    coverTitle: '一起做有温度\n的校园心理活动',
    status: 'published',
  },
]

const visibleActivities = computed(() => activities.value.slice(0, 3))

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
    // 展示本地演示内容，避免公开首页在断网时变成空白。
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
  <main class="wellbeing-home">
    <section class="home-hero">
      <div class="hero-copy">
        <span class="hero-kicker"><i />CAMPUS MENTAL WELLBEING</span>
        <h1>让每一份心事，<br />都有<span>温柔的回响</span>。</h1>
        <p>心屿同行服务心理社团、班级心理委员和校园心理活动，让表达更安心，让组织更轻松。</p>
        <div class="hero-actions">
          <button class="hero-primary" type="button" @click="router.push('/campus')">
            发现心晴活动 <Icon name="arrow-right" :size="17" />
          </button>
          <button class="hero-secondary" type="button" @click="goProtected('/projects')">
            <Icon name="grid" :size="16" />进入运营台
          </button>
        </div>
        <div class="hero-trust">
          <span><Icon name="check" :size="13" />最小必要信息</span>
          <span><Icon name="check" :size="13" />匿名参与可选</span>
          <span><Icon name="check" :size="13" />不做心理诊断</span>
        </div>
      </div>

      <div class="hero-notes" aria-label="心屿活动与工具预览">
        <article class="note-card note-card--today">
          <span>今天，也照顾一下自己</span>
          <strong>给情绪留一点呼吸的空间</strong>
          <div class="breath-ring"><i /><b>慢慢来</b></div>
          <small>一分钟呼吸练习 · 不记录隐私</small>
        </article>
        <article class="note-card note-card--event">
          <span>本周心晴活动</span>
          <strong>考试周解压游园会</strong>
          <p>5 个轻量互动摊位，可以匿名报名。</p>
          <div><b>28</b> 人已经参加 <Icon name="arrow-right" :size="14" /></div>
        </article>
        <article class="note-card note-card--maker">
          <span>运营台 · 创作工具</span>
          <strong>把活动想法做成页面</strong>
          <div class="maker-mini"><i /><i /><i /></div>
          <small>AI 生成 + 可视化调整</small>
        </article>
      </div>
    </section>

    <WellbeingToolkit />
    <div class="toolkit-more">
      <RouterLink to="/tools">打开完整心晴工具箱 <Icon name="arrow-right" :size="14" /></RouterLink>
    </div>

    <section id="services" class="home-services">
      <header>
        <span>我们陪伴的方式</span>
        <h2>不急着给答案，先把参与和表达变得更容易。</h2>
      </header>
      <div class="service-list">
        <RouterLink to="/about">
          <span class="service-index">01</span>
          <i class="service-icon is-green"><Icon name="users" :size="20" /></i>
          <div>
            <h3>心理社团连接</h3>
            <p>招新、部门介绍与成员活动报名。</p>
          </div>
          <Icon name="arrow-right" :size="16" />
        </RouterLink>
        <RouterLink to="/campus">
          <span class="service-index">02</span>
          <i class="service-icon is-clay"><Icon name="calendar" :size="20" /></i>
          <div>
            <h3>心晴活动组织</h3>
            <p>游园会、团体辅导与主题分享。</p>
          </div>
          <Icon name="arrow-right" :size="16" />
        </RouterLink>
        <RouterLink to="/tools">
          <span class="service-index">03</span>
          <i class="service-icon is-blue"><Icon name="clock" :size="20" /></i>
          <div>
            <h3>日常自我关怀</h3>
            <p>轻量打卡、练习与温柔的日常提醒。</p>
          </div>
          <Icon name="arrow-right" :size="16" />
        </RouterLink>
        <RouterLink to="/resources">
          <span class="service-index">04</span>
          <i class="service-icon is-cream"><Icon name="send" :size="20" /></i>
          <div>
            <h3>安心表达入口</h3>
            <p>匿名提问、活动反馈与保密说明。</p>
          </div>
          <Icon name="arrow-right" :size="16" />
        </RouterLink>
      </div>
    </section>

    <section id="activities" class="home-activities">
      <header>
        <div>
          <span>正在发生</span>
          <h2>最近的心晴活动</h2>
        </div>
        <button type="button" @click="router.push('/campus')">查看全部 <Icon name="arrow-right" :size="15" /></button>
      </header>
      <div class="activity-list">
        <RouterLink
          v-for="(activity, index) in visibleActivities"
          :key="activity.slug"
          :to="`/p/${activity.slug}`"
          class="activity-row"
        >
          <span class="activity-date"
            ><b>{{ String(index + 5).padStart(2, '0') }}</b
            ><small>NOV</small></span
          >
          <span class="activity-copy">
            <em>{{ activity.activityType || '心晴活动' }}</em>
            <strong>{{ activity.title }}</strong>
            <p>{{ activity.description || '查看活动详情并了解参与方式。' }}</p>
          </span>
          <span class="activity-meta">
            <i :class="`tone-${activity.coverTone || 'read'}`" />
            <small>{{ activity.status === 'published' ? '报名中' : '活动回顾' }}</small>
          </span>
          <Icon name="arrow-right" :size="17" />
        </RouterLink>
      </div>
    </section>

    <section id="maker" class="home-maker">
      <div class="maker-copy">
        <span>藏在运营台里的创作工具</span>
        <h2>需要自己设计时，<br />页面工坊随时在。</h2>
        <p>活动介绍、报名表、匿名提问、打卡页都可以从心晴模板开始，也可以让 AI 先生成初稿，再自由调整。</p>
        <button type="button" @click="goProtected('/projects/new')">
          打开页面工坊 <Icon name="arrow-right" :size="16" />
        </button>
      </div>
      <div class="maker-steps">
        <article>
          <b>01</b><Icon name="spark" :size="20" /><strong>生成初稿</strong><small>描述活动，AI 规划页面</small>
        </article>
        <article>
          <b>02</b><Icon name="cursor" :size="20" /><strong>调整内容</strong><small>组件、文案、颜色自由修改</small>
        </article>
        <article>
          <b>03</b><Icon name="qr" :size="20" /><strong>发布收集</strong><small>分享活动，查看参与数据</small>
        </article>
      </div>
    </section>

    <section id="safety" class="home-safety">
      <div>
        <span>WELLBEING IS NOT A SCORE</span>
        <h2>关注参与，不评判内心。</h2>
      </div>
      <ul>
        <li>
          <b>01</b><span><strong>不做心理诊断</strong><small>平台只支持活动组织，不提供诊断或治疗结论。</small></span>
        </li>
        <li>
          <b>02</b><span><strong>只收集必要信息</strong><small>活动组织者应清楚说明信息用途和查看范围。</small></span>
        </li>
        <li>
          <b>03</b><span><strong>保留求助边界</strong><small>遇到紧急情况，应联系学校心理中心或专业机构。</small></span>
        </li>
      </ul>
    </section>
  </main>
</template>

<style scoped>
.wellbeing-home {
  min-height: 100vh;
  color: var(--ink-900);
  background:
    radial-gradient(circle at 6% 8%, rgb(188 217 198 / 28%), transparent 24%),
    radial-gradient(circle at 94% 4%, rgb(238 218 203 / 40%), transparent 26%), #f6f8f3;
}

.home-nav {
  position: sticky;
  z-index: 50;
  top: 0;
  display: grid;
  min-height: 76px;
  align-items: center;
  gap: 24px;
  padding: 0 6vw;
  border-bottom: 1px solid rgb(219 229 221 / 85%);
  background: rgb(248 250 245 / 90%);
  backdrop-filter: blur(18px);
  grid-template-columns: auto 1fr auto;
}

.home-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.home-brand-mark {
  position: relative;
  display: grid;
  width: 40px;
  height: 40px;
  overflow: hidden;
  border-radius: 50% 50% 46% 54%;
  background: linear-gradient(145deg, #9ac8a9, #2f634b);
  box-shadow: 0 8px 22px rgb(47 99 75 / 18%);
  place-items: center;
}

.home-brand-mark i,
.home-brand-mark b {
  position: absolute;
  width: 17px;
  height: 8px;
  border: 1.5px solid #fff;
  border-radius: 50%;
}

.home-brand-mark i {
  transform: translate(-5px, -2px) rotate(-28deg);
}

.home-brand-mark b {
  transform: translate(5px, 3px) rotate(-28deg);
}

.home-brand > span:last-child {
  display: flex;
  flex-direction: column;
}

.home-brand strong {
  font-size: 16px;
  letter-spacing: -0.02em;
}

.home-brand small {
  margin-top: 2px;
  color: var(--ink-400);
  font-size: 8px;
  letter-spacing: 0.04em;
}

.home-nav nav {
  display: flex;
  justify-content: center;
  gap: 28px;
}

.home-nav nav button {
  padding: 7px 0;
  border: 0;
  color: var(--ink-600);
  background: transparent;
  font-size: 11px;
  font-weight: 650;
  cursor: pointer;
}

.home-nav nav button:hover {
  color: var(--brand-600);
}

.home-nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.home-login,
.home-join {
  min-height: 37px;
  padding: 0 13px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}

.home-login {
  border: 1px solid var(--line);
  color: var(--ink-600);
  background: #fff;
}

.home-join {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  color: #fff;
  background: var(--brand-600);
}

.home-hero {
  display: grid;
  width: min(1320px, calc(100% - 10vw));
  align-items: center;
  gap: 7vw;
  margin: 0 auto;
  padding: 82px 0 72px;
  grid-template-columns: minmax(0, 1.08fr) minmax(360px, 0.92fr);
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--brand-600);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.hero-kicker i {
  width: 22px;
  height: 1px;
  background: var(--brand-500);
}

.hero-copy h1 {
  margin: 20px 0 18px;
  font-family: var(--font-serif);
  font-size: clamp(46px, 6vw, 80px);
  font-weight: 650;
  letter-spacing: -0.075em;
  line-height: 1.08;
}

.hero-copy h1 span {
  color: var(--brand-600);
}

.hero-copy > p {
  max-width: 620px;
  color: var(--ink-500);
  font-size: 14px;
  line-height: 1.9;
}

.hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 28px;
}

.hero-primary,
.hero-secondary {
  display: inline-flex;
  min-height: 46px;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 750;
  cursor: pointer;
}

.hero-primary {
  border: 0;
  color: #fff;
  background: var(--brand-600);
  box-shadow: 0 16px 30px -18px rgb(47 99 75 / 74%);
}

.hero-secondary {
  border: 1px solid var(--line-strong);
  color: var(--ink-600);
  background: rgb(255 255 255 / 75%);
}

.hero-trust {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  margin-top: 30px;
}

.hero-trust span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--ink-400);
  font-size: 9px;
}

.hero-trust svg {
  color: var(--brand-500);
}

.hero-notes {
  position: relative;
  min-height: 540px;
}

.hero-notes::before {
  position: absolute;
  inset: 10% 5% 4%;
  border-radius: 45% 55% 52% 48%;
  background: linear-gradient(145deg, rgb(221 236 225 / 80%), rgb(246 233 221 / 72%));
  content: '';
  filter: blur(1px);
  transform: rotate(-5deg);
}

.note-card {
  position: absolute;
  border: 1px solid rgb(255 255 255 / 65%);
  border-radius: 25px;
  box-shadow: 0 30px 65px -38px rgb(42 66 51 / 44%);
  backdrop-filter: blur(14px);
}

.note-card > span {
  color: var(--ink-400);
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.note-card strong {
  display: block;
  margin-top: 8px;
  font-family: var(--font-serif);
  letter-spacing: -0.035em;
}

.note-card--today {
  top: 5%;
  left: 1%;
  width: 68%;
  padding: 24px;
  background: rgb(255 255 255 / 88%);
  transform: rotate(-2deg);
}

.note-card--today strong {
  max-width: 270px;
  font-size: 22px;
  line-height: 1.35;
}

.breath-ring {
  position: relative;
  display: grid;
  width: 128px;
  height: 128px;
  margin: 24px auto 14px;
  border: 1px solid rgb(66 124 91 / 18%);
  border-radius: 50%;
  background: radial-gradient(circle, rgb(224 239 228 / 70%), transparent 68%);
  place-items: center;
}

.breath-ring::before,
.breath-ring::after {
  position: absolute;
  border: 1px solid rgb(66 124 91 / 15%);
  border-radius: 50%;
  content: '';
}

.breath-ring::before {
  inset: 14px;
}

.breath-ring::after {
  inset: 29px;
}

.breath-ring b {
  color: var(--brand-600);
  font-size: 12px;
}

.note-card--today small,
.note-card--maker small {
  display: block;
  color: var(--ink-400);
  font-size: 8px;
  text-align: center;
}

.note-card--event {
  right: 0;
  bottom: 24%;
  width: 68%;
  padding: 22px;
  background: rgb(250 245 238 / 92%);
  transform: rotate(2.5deg);
}

.note-card--event strong {
  font-size: 20px;
}

.note-card--event p {
  margin: 10px 0 18px;
  color: var(--ink-500);
  font-size: 9px;
  line-height: 1.7;
}

.note-card--event > div {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--ink-500);
  font-size: 8px;
}

.note-card--event > div b {
  color: var(--brand-600);
  font-size: 15px;
}

.note-card--event > div svg {
  margin-left: auto;
  color: var(--brand-600);
}

.note-card--maker {
  bottom: 0;
  left: 0;
  width: 46%;
  padding: 18px;
  background: rgb(238 246 240 / 92%);
  transform: rotate(-1deg);
}

.note-card--maker strong {
  font-size: 15px;
}

.maker-mini {
  display: flex;
  height: 58px;
  align-items: flex-end;
  gap: 4px;
  margin: 16px 0 10px;
  padding: 10px;
  border-radius: 12px;
  background: #fff;
}

.maker-mini i {
  height: 70%;
  flex: 1;
  border-radius: 6px;
  background: linear-gradient(180deg, #b9d8c3, #edf5ef);
}

.maker-mini i:nth-child(2) {
  height: 100%;
  background: linear-gradient(180deg, #8fbaa0, #e5f0e8);
}

.maker-mini i:nth-child(3) {
  height: 46%;
  background: linear-gradient(180deg, #ebcdbf, #faf2ed);
}

.home-services,
.home-activities,
.home-maker,
.home-safety,
.home-footer {
  width: min(1320px, calc(100% - 10vw));
  margin: 0 auto;
}

.toolkit-more {
  width: min(1320px, calc(100% - 10vw));
  margin: -48px auto 54px;
  text-align: right;
}

.toolkit-more a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--brand-600);
  font-size: 10px;
  font-weight: 700;
}

.home-services {
  padding: 54px 0 62px;
  border-top: 1px solid var(--line);
}

.home-services > header span,
.home-activities > header span,
.home-maker-copy > span,
.home-safety > div > span {
  color: var(--brand-500);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.home-services > header h2 {
  max-width: 680px;
  margin-top: 12px;
  font-family: var(--font-serif);
  font-size: clamp(27px, 3.3vw, 42px);
  letter-spacing: -0.045em;
  line-height: 1.3;
}

.service-list {
  display: grid;
  gap: 1px;
  margin-top: 34px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--line);
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
}

.service-list > a {
  position: relative;
  display: flex;
  min-height: 210px;
  justify-content: flex-end;
  padding: 22px;
  background: #fff;
  flex-direction: column;
}

.service-list > a:hover {
  background: #fbfdfa;
}

.service-index {
  position: absolute;
  top: 18px;
  left: 20px;
  color: var(--ink-300);
  font-family: var(--font-serif);
  font-size: 24px;
}

.service-icon {
  display: grid;
  width: 40px;
  height: 40px;
  margin-bottom: 18px;
  border-radius: 13px;
  place-items: center;
}

.service-icon.is-green {
  color: #2f634b;
  background: #e5f0e8;
}

.service-icon.is-clay {
  color: #9b5e49;
  background: #f7eae3;
}

.service-icon.is-blue {
  color: #567f88;
  background: #e8f1f2;
}

.service-icon.is-cream {
  color: #8a713f;
  background: #f7f1df;
}

.service-list h3 {
  font-size: 15px;
}

.service-list p {
  margin-top: 8px;
  color: var(--ink-400);
  font-size: 10px;
  line-height: 1.7;
}

.service-list > a > svg {
  position: absolute;
  right: 20px;
  bottom: 22px;
  color: var(--ink-300);
}

.home-activities {
  padding: 58px 0 66px;
}

.home-activities > header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.home-activities h2 {
  margin-top: 8px;
  font-family: var(--font-serif);
  font-size: 34px;
  letter-spacing: -0.04em;
}

.home-activities > header button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 0;
  border: 0;
  color: var(--brand-600);
  background: transparent;
  font-size: 10px;
  cursor: pointer;
}

.activity-list {
  display: grid;
  gap: 9px;
  margin-top: 24px;
}

.activity-row {
  display: grid;
  min-height: 112px;
  align-items: center;
  gap: 20px;
  padding: 18px 22px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: rgb(255 255 255 / 72%);
  grid-template-columns: 64px minmax(0, 1fr) auto auto;
  transition:
    transform 160ms ease,
    border-color 160ms ease,
    background 160ms ease;
}

.activity-row:hover {
  border-color: var(--brand-200);
  background: #fff;
  transform: translateX(4px);
}

.activity-date {
  display: flex;
  align-items: center;
  flex-direction: column;
}

.activity-date b {
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 500;
}

.activity-date small {
  color: var(--ink-400);
  font-size: 8px;
  letter-spacing: 0.12em;
}

.activity-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.activity-copy em {
  color: var(--brand-600);
  font-size: 8px;
  font-style: normal;
  font-weight: 750;
}

.activity-copy strong {
  margin-top: 5px;
  font-size: 16px;
}

.activity-copy p {
  margin-top: 6px;
  color: var(--ink-400);
  font-size: 9px;
}

.activity-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--ink-500);
  font-size: 8px;
}

.activity-meta i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #86b394;
}

.activity-meta i.tone-photo {
  background: #d9a58e;
}

.activity-meta i.tone-volunteer {
  background: #87a9b3;
}

.activity-row > svg {
  color: var(--ink-300);
}

.home-maker {
  position: relative;
  display: grid;
  gap: 60px;
  overflow: hidden;
  margin-bottom: 68px;
  padding: 54px;
  border-radius: 32px;
  color: #fff;
  background:
    radial-gradient(circle at 88% 12%, rgb(255 255 255 / 12%), transparent 25%),
    linear-gradient(135deg, #213d2f, #416f56 64%, #8b725c);
  grid-template-columns: minmax(0, 0.9fr) minmax(420px, 1.1fr);
}

.home-maker::after {
  position: absolute;
  right: -100px;
  bottom: -180px;
  width: 420px;
  height: 420px;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 50%;
  content: '';
}

.maker-copy {
  position: relative;
  z-index: 1;
}

.maker-copy > span {
  color: #b9dcc7;
}

.maker-copy h2 {
  margin: 14px 0 14px;
  font-family: var(--font-serif);
  font-size: clamp(34px, 4.5vw, 55px);
  letter-spacing: -0.055em;
  line-height: 1.16;
}

.maker-copy p {
  max-width: 540px;
  color: rgb(255 255 255 / 66%);
  font-size: 11px;
  line-height: 1.9;
}

.maker-copy button {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  gap: 7px;
  margin-top: 24px;
  padding: 0 16px;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 999px;
  color: #fff;
  background: rgb(255 255 255 / 10%);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}

.maker-steps {
  position: relative;
  z-index: 1;
  display: grid;
  align-content: center;
  gap: 9px;
}

.maker-steps article {
  display: grid;
  min-height: 92px;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgb(255 255 255 / 14%);
  border-radius: 17px;
  background: rgb(255 255 255 / 8%);
  backdrop-filter: blur(8px);
  grid-template-columns: auto auto minmax(0, 1fr);
}

.maker-steps b {
  color: rgb(255 255 255 / 34%);
  font-size: 9px;
}

.maker-steps svg {
  color: #bfe2cb;
}

.maker-steps strong {
  font-size: 12px;
}

.maker-steps small {
  display: block;
  margin-top: 4px;
  color: rgb(255 255 255 / 48%);
  font-size: 8px;
}

.home-safety {
  display: grid;
  gap: 60px;
  padding: 20px 0 76px;
  grid-template-columns: minmax(260px, 0.7fr) minmax(0, 1.3fr);
}

.home-safety h2 {
  max-width: 420px;
  margin-top: 12px;
  font-family: var(--font-serif);
  font-size: 38px;
  letter-spacing: -0.045em;
  line-height: 1.3;
}

.home-safety ul {
  display: grid;
  gap: 1px;
  margin: 0;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--line);
  list-style: none;
  overflow: hidden;
}

.home-safety li {
  display: grid;
  gap: 16px;
  padding: 20px;
  background: #fff;
  grid-template-columns: auto minmax(0, 1fr);
}

.home-safety li > b {
  color: var(--brand-400);
  font-family: var(--font-serif);
  font-size: 20px;
}

.home-safety li > span {
  display: flex;
  flex-direction: column;
}

.home-safety li strong {
  font-size: 12px;
}

.home-safety li small {
  margin-top: 5px;
  color: var(--ink-400);
  font-size: 9px;
  line-height: 1.7;
}

.home-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 0 36px;
  border-top: 1px solid var(--line);
  color: var(--ink-400);
}

.home-footer p {
  flex: 1;
  font-size: 9px;
}

.footer-stat {
  font-size: 9px;
}

@media (max-width: 1080px) {
  .home-nav nav {
    display: none;
  }

  .home-nav {
    grid-template-columns: 1fr auto;
  }

  .home-hero {
    grid-template-columns: 1fr;
  }

  .hero-notes {
    width: min(600px, 100%);
    margin: 0 auto;
  }

  .service-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .home-maker,
  .home-safety {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .home-nav {
    padding: 0 18px;
  }

  .home-login {
    display: none;
  }

  .home-brand small {
    display: none;
  }

  .home-hero {
    width: calc(100% - 36px);
    padding: 54px 0;
  }

  .hero-copy h1 {
    font-size: 45px;
  }

  .hero-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-primary,
  .hero-secondary {
    justify-content: center;
  }

  .hero-notes {
    min-height: 560px;
  }

  .note-card--today,
  .note-card--event,
  .note-card--maker {
    width: 88%;
  }

  .note-card--event {
    right: 0;
    bottom: 25%;
  }

  .note-card--maker {
    bottom: 0;
  }

  .home-services,
  .home-activities,
  .home-maker,
  .home-safety,
  .home-footer {
    width: calc(100% - 36px);
  }

  .toolkit-more {
    width: calc(100% - 36px);
  }

  .service-list {
    grid-template-columns: 1fr;
  }

  .activity-row {
    grid-template-columns: 50px minmax(0, 1fr) auto;
  }

  .activity-meta {
    display: none;
  }

  .home-maker {
    padding: 30px 22px;
  }

  .home-safety {
    gap: 28px;
  }

  .home-safety h2 {
    font-size: 31px;
  }

  .home-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
