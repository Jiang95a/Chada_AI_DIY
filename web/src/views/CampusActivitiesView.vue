<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { listPublicActivities, type PublicActivitySummary } from '@/services/public'
import { useProjectStore } from '@/stores/projects'
import { useToast } from '@/composables/useToast'

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

const featured = computed(() => activities.value[0])

function coverTone(item: PublicActivitySummary): string {
  return item.coverTone || 'photo'
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
      <div class="campus-hero-copy">
        <p class="eyebrow">Campus wellbeing</p>
        <h1>心理活动广场</h1>
        <p>发现心理社团活动、心晴游园会、自我关怀打卡与安心表达入口，找到适合自己的参与方式。</p>
        <div class="campus-hero-actions">
          <RouterLink class="app-button app-button--primary" to="/projects/new">
            <Icon name="plus" :size="15" />发起心晴活动
          </RouterLink>
          <RouterLink class="app-button app-button--secondary" to="/templates">
            <Icon name="grid" :size="15" />浏览心晴模板
          </RouterLink>
        </div>
      </div>
      <RouterLink v-if="featured" :to="`/p/${featured.slug}`" class="campus-featured">
        <span>{{ featured.coverCode || 'FEATURED' }}</span>
        <strong>{{ featured.coverTitle || featured.title }}</strong>
        <small>本周心晴 · {{ featured.activityType || '心理活动' }}</small>
        <Icon name="arrow-right" :size="18" />
      </RouterLink>
      <div v-else class="campus-featured campus-featured--empty">
        <Icon name="pin" :size="26" />
        <strong>心晴活动正在准备</strong>
        <small>发布第一个心理活动后，它会出现在这里。</small>
      </div>
    </section>

    <section class="campus-toolbar">
      <div class="campus-search">
        <Icon name="search" :size="16" />
        <input v-model="search" placeholder="搜索心晴活动、心理社团或关键词" aria-label="搜索心理活动" />
      </div>
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
      <strong>正在加载心理活动</strong>
    </section>
    <section v-else-if="error" class="campus-state">
      <Icon name="alert" :size="24" />
      <strong>心理活动暂时不可用</strong>
      <p>{{ error }}</p>
      <button class="app-button app-button--secondary" type="button" @click="load">重新加载</button>
    </section>
    <section v-else-if="!visibleActivities.length" class="campus-state">
      <Icon name="search" :size="24" />
      <strong>没有找到匹配活动</strong>
      <p>换一个关键词或活动分类试试。</p>
    </section>
    <section v-else class="campus-grid">
      <article v-for="item in visibleActivities" :key="item.slug" class="campus-card">
        <RouterLink :to="`/p/${item.slug}`" class="campus-card-cover" :class="`cover--${coverTone(item)}`">
          <span>{{ item.coverCode || 'CAMPUS' }}</span>
          <strong>{{ item.coverTitle || item.title }}</strong>
          <i />
        </RouterLink>
        <div class="campus-card-body">
          <span class="campus-card-type">{{ item.activityType || '心理活动' }}</span>
          <RouterLink :to="`/p/${item.slug}`"
            ><h2>{{ item.title }}</h2></RouterLink
          >
          <p>{{ item.description || '点击查看活动详情与安心参与方式。' }}</p>
          <footer>
            <span><Icon name="clock" :size="12" />{{ updatedLabel(item.updatedAt) }}</span>
            <button type="button" @click="copyLink(item.slug)"><Icon name="copy" :size="13" />分享</button>
          </footer>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.campus-page {
  width: min(1440px, 100%);
  margin: 0 auto;
  padding: 28px 34px 60px;
}
.campus-hero {
  position: relative;
  display: grid;
  gap: 26px;
  overflow: hidden;
  padding: 34px;
  border-radius: 24px;
  color: #fff;
  background:
    radial-gradient(circle at 82% 10%, rgb(255 255 255 / 18%), transparent 28%),
    linear-gradient(135deg, #0f172a, #244c9a 58%, #2f9b7a);
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
}
.campus-hero::after {
  position: absolute;
  right: -80px;
  bottom: -130px;
  width: 340px;
  height: 340px;
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 50%;
  content: '';
}
.campus-hero .eyebrow {
  color: #9de7d1;
}
.campus-hero h1 {
  margin: 0;
  font-size: clamp(34px, 5vw, 58px);
  letter-spacing: -0.055em;
}
.campus-hero-copy > p:last-of-type {
  max-width: 560px;
  margin: 14px 0 0;
  color: rgb(255 255 255 / 72%);
  font-size: 13px;
  line-height: 1.8;
}
.campus-hero-actions {
  display: flex;
  gap: 9px;
  margin-top: 24px;
}
.campus-featured {
  position: relative;
  z-index: 1;
  display: flex;
  min-height: 210px;
  justify-content: flex-end;
  flex-direction: column;
  padding: 24px;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 18px;
  background: rgb(255 255 255 / 11%);
  backdrop-filter: blur(12px);
}
.campus-featured > span,
.campus-featured small {
  color: rgb(255 255 255 / 65%);
  font-size: 9px;
}
.campus-featured strong {
  max-width: 260px;
  margin: 14px 0 9px;
  font-size: 24px;
  letter-spacing: -0.04em;
  line-height: 1.18;
  white-space: pre-line;
}
.campus-featured > svg {
  position: absolute;
  right: 22px;
  bottom: 22px;
}
.campus-featured--empty {
  align-items: flex-start;
  justify-content: center;
  gap: 8px;
}
.campus-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 24px;
}
.campus-search {
  display: flex;
  width: min(380px, 100%);
  height: 42px;
  align-items: center;
  gap: 9px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
}
.campus-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 11px;
}
.campus-types {
  display: flex;
  gap: 5px;
  overflow-x: auto;
}
.campus-types button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-soft);
  background: #fff;
  font-size: 9px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}
.campus-types button.is-active {
  border-color: var(--primary);
  color: #fff;
  background: var(--primary);
}
.campus-grid {
  display: grid;
  gap: 16px;
  margin-top: 18px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.campus-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 17px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}
.campus-card-cover {
  position: relative;
  display: flex;
  height: 160px;
  justify-content: flex-end;
  flex-direction: column;
  padding: 18px;
  color: #fff;
}
.campus-card-cover.cover--photo {
  background: linear-gradient(145deg, #88ad94, #365746);
}
.campus-card-cover.cover--music,
.campus-card-cover.cover--vote {
  background: linear-gradient(145deg, #617d84, #b8cbc8);
}
.campus-card-cover.cover--read,
.campus-card-cover.cover--checkin {
  background: linear-gradient(145deg, #3f6b50, #b7c99e);
}
.campus-card-cover.cover--volunteer,
.campus-card-cover.cover--event {
  background: linear-gradient(145deg, #547a73, #d2b5a3);
}
.campus-card-cover > span {
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.12em;
}
.campus-card-cover > strong {
  max-width: 230px;
  margin-top: 13px;
  font-size: 20px;
  letter-spacing: -0.035em;
  line-height: 1.2;
  white-space: pre-line;
}
.campus-card-cover > i {
  position: absolute;
  right: 18px;
  bottom: 18px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
}
.campus-card-body {
  padding: 16px;
}
.campus-card-type {
  color: var(--primary);
  font-size: 9px;
  font-weight: 750;
}
.campus-card h2 {
  margin: 7px 0 0;
  font-size: 15px;
}
.campus-card p {
  min-height: 34px;
  margin: 7px 0 14px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.65;
}
.campus-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.campus-card footer span,
.campus-card footer button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  color: var(--muted);
  background: transparent;
  font-size: 9px;
}
.campus-card footer button {
  color: var(--primary);
  cursor: pointer;
}
.campus-state {
  display: flex;
  min-height: 320px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9px;
  margin-top: 18px;
  border: 1px dashed var(--line-strong);
  border-radius: 18px;
  color: var(--muted);
  text-align: center;
}
.campus-state strong {
  color: var(--ink);
  font-size: 13px;
}
.campus-state p {
  margin: 0;
  font-size: 10px;
}
@media (max-width: 1050px) {
  .campus-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .campus-page {
    padding: 18px;
  }
  .campus-hero,
  .campus-grid {
    grid-template-columns: 1fr;
  }
  .campus-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
