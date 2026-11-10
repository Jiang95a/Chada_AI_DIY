<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { listPublicActivities, type PublicActivitySummary } from '@/services/public'
import { useProjectStore } from '@/stores/projects'

const projectStore = useProjectStore()
const toast = useToast()
const activities = ref<PublicActivitySummary[]>([])
const loading = ref(true)
const error = ref('')
const search = ref('')
const type = ref('全部')

const types = computed(() => [
  '全部',
  ...new Set(activities.value.map((item) => item.activityType).filter((value): value is string => Boolean(value))),
])
const visibleActivities = computed(() => {
  const query = search.value.trim().toLowerCase()
  return activities.value.filter((item) => {
    const typeMatched = type.value === '全部' || item.activityType === type.value
    const searchMatched =
      !query || `${item.title} ${item.description ?? ''} ${item.activityType ?? ''}`.toLowerCase().includes(query)
    return typeMatched && searchMatched
  })
})
const featured = computed(() => visibleActivities.value[0])
const remaining = computed(() => visibleActivities.value.slice(1))

function coverTone(item: PublicActivitySummary): string {
  return item.coverTone || 'read'
}

function updatedLabel(value?: string): string {
  if (!value) return '最近更新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '最近更新'
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日更新`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const remote = await listPublicActivities({ pageSize: 50 })
    const wellbeing = remote.filter((item) =>
      /心理|心晴|关怀|情绪|正念|匿名|团辅|游园/.test(
        `${item.title} ${item.description ?? ''} ${item.activityType ?? ''}`
      )
    )
    activities.value = wellbeing
    if (!wellbeing.length) throw new Error('当前没有心理主题活动')
  } catch (err) {
    const fallback = projectStore.projects.filter((project) => project.status === 'published')
    activities.value = fallback.map((project) => ({
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
    if (!activities.value.length) error.value = err instanceof Error ? err.message : '心理活动加载失败'
  } finally {
    loading.value = false
  }
}

function copyLink(slug: string): void {
  const url = `${window.location.origin}/p/${slug}`
  void navigator.clipboard
    .writeText(url)
    .then(() => toast.pushToast('活动链接已复制', 'success'))
    .catch(() => toast.pushToast('复制失败，请手动打开活动', 'warn'))
}

onMounted(load)
</script>

<template>
  <main class="campus-page">
    <section class="campus-hero">
      <div class="hero-copy">
        <span>CAMPUS WELLBEING CALENDAR</span>
        <h1>发现一场，<br />适合此刻的活动。</h1>
        <p>不需要做很多准备，也不用强迫自己表现积极。看看最近有哪些心理社团活动，按自己的节奏参与。</p>
        <div>
          <RouterLink to="/resources"><Icon name="help" :size="15" />先看看心理资源</RouterLink>
          <span><i />本周持续更新</span>
        </div>
      </div>
      <div class="hero-motif" aria-hidden="true">
        <span class="motif-ring motif-ring--one" />
        <span class="motif-ring motif-ring--two" />
        <span class="motif-island">屿</span>
        <small>PAUSE · BREATHE · CONNECT</small>
      </div>
    </section>

    <section class="discover-panel">
      <label class="campus-search">
        <Icon name="search" :size="16" />
        <input v-model="search" placeholder="搜索活动、心理社团或关键词" aria-label="搜索心理活动" />
      </label>
      <div class="campus-types">
        <button
          v-for="item in types"
          :key="item"
          type="button"
          :class="{ 'is-active': type === item }"
          @click="type = item"
        >
          {{ item }}
        </button>
      </div>
    </section>

    <section v-if="loading" class="campus-state">
      <Icon name="refresh" :size="24" />
      <strong>正在整理近期心理活动</strong>
    </section>
    <section v-else-if="error" class="campus-state">
      <Icon name="alert" :size="24" />
      <strong>心理活动暂时不可用</strong>
      <p>{{ error }}</p>
      <button type="button" @click="load">重新加载</button>
    </section>
    <section v-else-if="!visibleActivities.length" class="campus-state">
      <Icon name="search" :size="24" />
      <strong>没有找到匹配活动</strong>
      <p>换一个关键词或活动分类试试。</p>
    </section>
    <template v-else>
      <section v-if="featured" class="featured-section">
        <RouterLink :to="`/p/${featured.slug}`" class="featured-event" :class="`tone-${coverTone(featured)}`">
          <span class="featured-label">本周心晴推荐</span>
          <div class="featured-art" aria-hidden="true"><i /><i /><i /></div>
          <div class="featured-copy">
            <em>{{ featured.activityType || '心理活动' }}</em>
            <h2>{{ featured.title }}</h2>
            <p>{{ featured.description || '点击查看活动详情与参与方式。' }}</p>
            <span>查看活动 <Icon name="arrow-right" :size="15" /></span>
          </div>
          <small>{{ updatedLabel(featured.updatedAt) }}</small>
        </RouterLink>
      </section>

      <section v-if="remaining.length" class="activity-feed">
        <header>
          <span>MORE TOGETHER</span>
          <h2>更多可以参与的活动</h2>
        </header>
        <div class="activity-feed-grid">
          <article v-for="(item, index) in remaining" :key="item.slug" class="campus-card">
            <RouterLink :to="`/p/${item.slug}`" class="campus-card-art" :class="`tone-${coverTone(item)}`">
              <span>{{ item.coverCode || 'XINYU' }}</span>
              <strong>{{ item.coverTitle || item.title }}</strong>
              <b>0{{ index + 2 }}</b>
            </RouterLink>
            <div class="campus-card-body">
              <span class="campus-card-type">{{ item.activityType || '心理活动' }}</span>
              <RouterLink :to="`/p/${item.slug}`"
                ><h3>{{ item.title }}</h3></RouterLink
              >
              <p>{{ item.description || '点击查看活动详情与安心参与方式。' }}</p>
              <footer>
                <span><Icon name="clock" :size="12" />{{ updatedLabel(item.updatedAt) }}</span>
                <button type="button" @click="copyLink(item.slug)"><Icon name="copy" :size="13" />分享</button>
              </footer>
            </div>
          </article>
        </div>
      </section>
    </template>
  </main>
</template>

<style scoped>
.campus-page {
  width: min(1240px, calc(100% - 10vw));
  margin: 0 auto;
  padding: 46px 0 82px;
}

.campus-hero {
  display: grid;
  align-items: center;
  gap: 30px;
  min-height: 390px;
  padding: 44px 50px;
  border-radius: 30px;
  color: #fff;
  background:
    radial-gradient(circle at 76% 36%, rgb(255 255 255 / 13%), transparent 17%),
    linear-gradient(128deg, #183126, #466f58 62%, #b18469);
  grid-template-columns: 1.1fr 0.9fr;
}

.hero-copy > span {
  color: #bad8c4;
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.15em;
}

.hero-copy h1 {
  margin: 15px 0 16px;
  font-family: var(--font-serif);
  font-size: clamp(40px, 5vw, 66px);
  letter-spacing: -0.07em;
  line-height: 1.08;
}

.hero-copy > p {
  max-width: 610px;
  color: rgb(255 255 255 / 67%);
  font-size: 11px;
  line-height: 1.9;
}

.hero-copy > div {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 25px;
}

.hero-copy > div > a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 39px;
  padding: 0 13px;
  border: 1px solid rgb(255 255 255 / 23%);
  border-radius: 999px;
  color: #fff;
  background: rgb(255 255 255 / 9%);
  font-size: 9px;
}

.hero-copy > div > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgb(255 255 255 / 48%);
  font-size: 8px;
}

.hero-copy > div > span i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9ee3ba;
}

.hero-motif {
  position: relative;
  display: grid;
  width: min(330px, 100%);
  aspect-ratio: 1;
  justify-self: center;
  place-items: center;
}

.motif-ring {
  position: absolute;
  border: 1px solid rgb(255 255 255 / 16%);
  border-radius: 50%;
}

.motif-ring--one {
  inset: 10%;
}

.motif-ring--two {
  inset: 1%;
  animation: breatheRing 7s ease-in-out infinite;
}

.motif-island {
  position: relative;
  display: grid;
  width: 33%;
  height: 33%;
  border-radius: 50% 50% 46% 54%;
  color: #c9dfd0;
  background: rgb(255 255 255 / 9%);
  font-family: var(--font-serif);
  font-size: 36px;
  place-items: center;
}

.hero-motif small {
  position: absolute;
  bottom: 40%;
  color: rgb(255 255 255 / 30%);
  font-size: 7px;
  letter-spacing: 0.15em;
}

.discover-panel {
  display: flex;
  align-items: center;
  gap: 18px;
  margin: 20px 0 0;
  padding: 13px;
  border: 1px solid var(--line);
  border-radius: 17px;
  background: #fff;
}

.campus-search {
  display: flex;
  width: min(340px, 100%);
  height: 38px;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border-radius: 10px;
  color: var(--ink-400);
  background: var(--ink-50);
}

.campus-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 9px;
}

.campus-types {
  display: flex;
  gap: 5px;
  overflow-x: auto;
}

.campus-types button {
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid transparent;
  border-radius: 999px;
  color: var(--ink-500);
  background: transparent;
  font-size: 8px;
  white-space: nowrap;
  cursor: pointer;
}

.campus-types button.is-active {
  color: var(--brand-700);
  background: var(--brand-50);
}

.featured-section {
  margin-top: 22px;
}

.featured-event {
  position: relative;
  display: grid;
  min-height: 430px;
  align-items: end;
  overflow: hidden;
  padding: 34px;
  border-radius: 26px;
  color: #fff;
  grid-template-columns: 1fr 0.9fr;
}

.featured-event.tone-read {
  background: linear-gradient(130deg, #274a39, #6d9879);
}

.featured-event.tone-photo {
  background: linear-gradient(130deg, #745e52, #c49e86);
}

.featured-event.tone-volunteer {
  background: linear-gradient(130deg, #496a6a, #acc6c4);
}

.featured-label {
  position: absolute;
  top: 25px;
  left: 30px;
  z-index: 2;
  padding: 5px 9px;
  border-radius: 999px;
  background: rgb(255 255 255 / 13%);
  font-size: 8px;
}

.featured-art {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.featured-art i {
  position: absolute;
  border: 1px solid rgb(255 255 255 / 17%);
  border-radius: 50%;
}

.featured-art i:nth-child(1) {
  right: -8%;
  top: -20%;
  width: 530px;
  height: 530px;
}

.featured-art i:nth-child(2) {
  right: 8%;
  top: -4%;
  width: 360px;
  height: 360px;
}

.featured-art i:nth-child(3) {
  right: 20%;
  bottom: -12%;
  width: 210px;
  height: 210px;
}

.featured-copy {
  position: relative;
  z-index: 1;
  max-width: 570px;
  padding-top: 120px;
}

.featured-copy em {
  color: rgb(255 255 255 / 58%);
  font-size: 8px;
  font-style: normal;
  font-weight: 750;
}

.featured-copy h2 {
  margin-top: 9px;
  font-family: var(--font-serif);
  font-size: clamp(36px, 5vw, 58px);
  letter-spacing: -0.065em;
  line-height: 1.12;
}

.featured-copy p {
  max-width: 520px;
  margin-top: 14px;
  color: rgb(255 255 255 / 63%);
  font-size: 10px;
  line-height: 1.8;
}

.featured-copy > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 23px;
  color: rgb(255 255 255 / 78%);
  font-size: 9px;
}

.featured-event > small {
  position: absolute;
  right: 30px;
  bottom: 28px;
  z-index: 1;
  color: rgb(255 255 255 / 45%);
  font-size: 8px;
}

.activity-feed {
  margin-top: 58px;
}

.activity-feed > header > span,
.activity-feed > header > h2 {
  display: block;
}

.activity-feed > header > span {
  color: var(--brand-500);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.activity-feed > header > h2 {
  margin-top: 8px;
  font-family: var(--font-serif);
  font-size: 32px;
  letter-spacing: -0.045em;
}

.activity-feed-grid {
  display: grid;
  gap: 13px;
  margin-top: 22px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.campus-card {
  display: grid;
  grid-template-columns: minmax(150px, 0.7fr) 1fr;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: #fff;
}

.campus-card-art {
  position: relative;
  display: flex;
  min-height: 260px;
  justify-content: space-between;
  padding: 17px;
  color: #fff;
  flex-direction: column;
}

.campus-card-art.tone-read {
  background: linear-gradient(145deg, #456d53, #a4bb86);
}

.campus-card-art.tone-photo {
  background: linear-gradient(145deg, #896756, #d0ab91);
}

.campus-card-art.tone-volunteer {
  background: linear-gradient(145deg, #526e6d, #a8c0bd);
}

.campus-card-art > span {
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.campus-card-art strong {
  max-width: 180px;
  font-family: var(--font-serif);
  font-size: 21px;
  letter-spacing: -0.035em;
  white-space: pre-line;
}

.campus-card-art b {
  position: absolute;
  right: 13px;
  bottom: 11px;
  color: rgb(255 255 255 / 22%);
  font-family: var(--font-serif);
  font-size: 38px;
}

.campus-card-body {
  display: flex;
  padding: 20px;
  flex-direction: column;
}

.campus-card-type {
  color: var(--brand-600);
  font-size: 8px;
  font-weight: 750;
}

.campus-card h3 {
  margin-top: 7px;
  font-family: var(--font-serif);
  font-size: 21px;
  letter-spacing: -0.035em;
}

.campus-card p {
  margin-top: 8px;
  color: var(--ink-500);
  font-size: 9px;
  line-height: 1.75;
}

.campus-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 15px;
  border-top: 1px solid var(--line);
}

.campus-card footer span,
.campus-card footer button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ink-400);
  background: transparent;
  font-size: 8px;
}

.campus-card footer button {
  border: 0;
  color: var(--brand-600);
  cursor: pointer;
}

.campus-state {
  display: flex;
  min-height: 300px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  margin-top: 20px;
  border: 1px dashed var(--line-strong);
  border-radius: 20px;
  color: var(--ink-400);
  text-align: center;
}

.campus-state strong {
  color: var(--ink-700);
  font-size: 11px;
}

.campus-state p {
  font-size: 9px;
}

.campus-state button {
  margin-top: 4px;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  font-size: 8px;
}

@keyframes breatheRing {
  50% {
    opacity: 0.45;
    transform: scale(1.05);
  }
}

@media (max-width: 900px) {
  .campus-hero {
    grid-template-columns: 1fr;
  }

  .hero-motif {
    display: none;
  }

  .featured-event,
  .activity-feed-grid {
    grid-template-columns: 1fr;
  }

  .campus-card {
    grid-template-columns: 160px 1fr;
  }
}

@media (max-width: 600px) {
  .campus-page {
    width: calc(100% - 28px);
    padding-top: 22px;
  }

  .campus-hero {
    min-height: 360px;
    padding: 30px 22px;
  }

  .discover-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .campus-types {
    width: 100%;
  }

  .campus-card {
    grid-template-columns: 1fr;
  }

  .campus-card-art {
    min-height: 210px;
  }
}
</style>
