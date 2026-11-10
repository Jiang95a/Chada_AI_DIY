<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useAuthStore } from '@/stores/auth'
import { useProjectStore } from '@/stores/projects'
import { useToast } from '@/composables/useToast'
import { getPortfolio, type Portfolio } from '@/services/portfolio'

const auth = useAuthStore()
const projectStore = useProjectStore()
const toast = useToast()
const displayName = ref('')
const saving = ref(false)
const portfolio = ref<Portfolio | null>(null)
const portfolioLoading = ref(false)
const portfolioError = ref('')

const initials = computed(() => (auth.user?.displayName || 'U').slice(0, 1).toUpperCase())
const publishedCount = computed(() => projectStore.projects.filter((project) => project.status === 'published').length)
const submissionCount = computed(() => projectStore.projects.reduce((sum, project) => sum + project.submissionCount, 0))
const completion = computed(() => {
  const nameReady = Boolean(auth.user?.displayName?.trim())
  const emailReady = Boolean(auth.user?.email)
  return nameReady && emailReady ? 100 : emailReady ? 70 : 40
})

watch(
  () => auth.user?.displayName,
  (value) => {
    displayName.value = value || ''
  },
  { immediate: true }
)

async function saveProfile(): Promise<void> {
  const value = displayName.value.trim()
  if (!value) {
    toast.pushToast('昵称不能为空', 'warn')
    return
  }
  if (value.length > 80) {
    toast.pushToast('昵称最多 80 字', 'warn')
    return
  }
  saving.value = true
  try {
    await auth.updateProfile(value)
    toast.pushToast('个人资料已更新', 'success')
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '更新失败', 'error')
  } finally {
    saving.value = false
  }
}

async function copyId(): Promise<void> {
  if (!auth.user?.id) return
  try {
    await navigator.clipboard.writeText(auth.user.id)
    toast.pushToast('用户 ID 已复制', 'success')
  } catch {
    toast.pushToast('复制失败', 'warn')
  }
}

function roleLabel(role: Portfolio['items'][number]['role']): string {
  if (role === 'organizer') return '组织者'
  if (role === 'anonymous') return '匿名参加'
  return '参与者'
}

function monthLabel(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月`
}

onMounted(async () => {
  await auth.fetchMe()
  await projectStore.loadProjects()
  portfolioLoading.value = true
  try {
    portfolio.value = await getPortfolio()
  } catch (error) {
    portfolioError.value = error instanceof Error ? error.message : '成长档案加载失败'
  } finally {
    portfolioLoading.value = false
  }
})
</script>

<template>
  <WorkspaceShell>
    <main class="account-page">
      <header class="account-heading">
        <div>
          <p class="eyebrow">Your place in Xinyu</p>
          <h1>个人资料</h1>
          <p>管理账号信息，也回看你与心屿一起发生过的连接。</p>
        </div>
        <div class="account-heading-actions">
          <RouterLink to="/settings"><Icon name="sliders" :size="15" />偏好设置</RouterLink>
          <RouterLink to="/help"><Icon name="help" :size="15" />帮助与反馈</RouterLink>
        </div>
      </header>

      <section class="profile-hero">
        <div class="profile-orbit">
          <span class="profile-avatar">{{ initials }}</span>
          <i /><i /><i />
        </div>
        <div class="profile-identity">
          <span class="profile-status"><i />{{ auth.user?.status === 'active' ? '账号正常' : '账号受限' }}</span>
          <h2>{{ auth.user?.displayName || '未命名用户' }}</h2>
          <p>{{ auth.user?.email || '正在加载账号信息…' }}</p>
          <blockquote>“在这里，不需要急着成为一个更好的人。”</blockquote>
        </div>
        <div class="profile-progress">
          <span>资料完整度</span>
          <strong>{{ completion }}<small>%</small></strong>
          <i><b :style="{ width: `${completion}%` }" /></i>
        </div>
      </section>

      <div class="account-grid">
        <section class="account-card identity-card">
          <header>
            <span>ACCOUNT</span>
            <h2>基本信息</h2>
            <p>昵称会显示在活动组织与团队协作中。</p>
          </header>
          <form class="profile-form" @submit.prevent="saveProfile">
            <label>
              <span>昵称</span>
              <input v-model="displayName" maxlength="80" placeholder="输入你的昵称" />
            </label>
            <label>
              <span>邮箱</span>
              <input :value="auth.user?.email || ''" readonly />
              <small>邮箱是登录账号，目前不支持直接修改。</small>
            </label>
            <label>
              <span>用户 ID</span>
              <div class="profile-id">
                <input :value="auth.user?.id || ''" readonly />
                <button type="button" @click="copyId"><Icon name="copy" :size="13" />复制</button>
              </div>
            </label>
            <button class="profile-save" type="submit" :disabled="saving">
              <Icon name="check" :size="15" />{{ saving ? '保存中…' : '保存资料' }}
            </button>
          </form>
        </section>

        <aside class="profile-side">
          <section class="overview-card">
            <div>
              <span>组织活动</span>
              <strong>{{ projectStore.projectCount }}</strong>
            </div>
            <div>
              <span>已经发布</span>
              <strong>{{ publishedCount }}</strong>
            </div>
            <div>
              <span>累计参与</span>
              <strong>{{ submissionCount }}</strong>
            </div>
          </section>
          <section class="account-tip">
            <Icon name="lock" :size="18" />
            <div>
              <strong>隐私提示</strong>
              <p>活动页面只应收集必要信息。匿名参与不会在成长档案中暴露活动回答。</p>
            </div>
          </section>
        </aside>
      </div>

      <section class="journey-section">
        <header>
          <div>
            <span>MY JOURNEY</span>
            <h2>我的参与轨迹</h2>
            <p>只展示你本人参与或组织过的已发布活动。</p>
          </div>
          <span class="journey-count">{{ portfolio?.totalActivities || 0 }} 场连接</span>
        </header>

        <div v-if="portfolioLoading" class="journey-state">
          <Icon name="refresh" :size="22" />
          <strong>正在整理参与轨迹</strong>
        </div>
        <div v-else-if="portfolioError" class="journey-state is-error">
          <Icon name="alert" :size="22" />
          <strong>参与轨迹暂时不可用</strong>
          <p>{{ portfolioError }}</p>
        </div>
        <div v-else-if="portfolio" class="journey-content">
          <div class="journey-metrics">
            <div>
              <strong>{{ portfolio.wellbeingCount }}</strong
              ><span>心理活动</span>
            </div>
            <div>
              <strong>{{ portfolio.careerCount }}</strong
              ><span>生涯活动</span>
            </div>
            <div>
              <strong>{{ portfolio.totalActivities }}</strong
              ><span>全部场次</span>
            </div>
          </div>

          <div v-if="portfolio.items.length" class="journey-list">
            <article v-for="(item, index) in portfolio.items" :key="item.projectId">
              <span class="journey-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="journey-line"><i /></span>
              <div class="journey-copy">
                <span class="journey-role" :class="`is-${item.role}`">{{ roleLabel(item.role) }}</span>
                <h3>{{ item.title }}</h3>
                <p>{{ item.activityType }} · {{ monthLabel(item.firstJoinedAt) }}</p>
              </div>
              <RouterLink :to="`/p/${item.slug}`" target="_blank" aria-label="打开活动">
                <Icon name="arrow-right" :size="16" />
              </RouterLink>
            </article>
          </div>
          <p v-else class="journey-state">还没有参与轨迹，先去参加一场心晴活动吧。</p>
        </div>
      </section>
    </main>
  </WorkspaceShell>
</template>

<style scoped>
.account-page {
  width: min(1220px, 100%);
  margin: 0 auto;
  padding: 32px 34px 64px;
}

.account-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.eyebrow {
  color: var(--ink-400);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.account-heading h1 {
  margin: 6px 0 7px;
  font-family: var(--font-serif);
  font-size: clamp(32px, 4.2vw, 48px);
  letter-spacing: -0.055em;
}

.account-heading > div > p:last-child {
  color: var(--ink-500);
  font-size: 10px;
}

.account-heading-actions {
  display: flex;
  gap: 13px;
}

.account-heading-actions a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--ink-500);
  font-size: 9px;
}

.profile-hero {
  position: relative;
  display: grid;
  min-height: 245px;
  align-items: center;
  gap: 28px;
  overflow: hidden;
  margin-top: 24px;
  padding: 34px;
  border-radius: 26px;
  color: #fff;
  background:
    radial-gradient(circle at 90% 10%, rgb(255 255 255 / 16%), transparent 25%),
    linear-gradient(130deg, #1e382b, #406d54 62%, #98765f);
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.profile-hero::after {
  position: absolute;
  right: -90px;
  bottom: -170px;
  width: 420px;
  height: 420px;
  border: 1px solid rgb(255 255 255 / 13%);
  border-radius: 50%;
  content: '';
}

.profile-orbit {
  position: relative;
  display: grid;
  width: 118px;
  height: 118px;
  place-items: center;
}

.profile-avatar {
  position: relative;
  z-index: 1;
  display: grid;
  width: 82px;
  height: 82px;
  border-radius: 50%;
  color: #264331;
  background: #eef0df;
  font-family: var(--font-serif);
  font-size: 34px;
  place-items: center;
}

.profile-orbit i {
  position: absolute;
  border: 1px solid rgb(255 255 255 / 16%);
  border-radius: 50%;
}

.profile-orbit i:nth-child(2) {
  inset: 4px;
}

.profile-orbit i:nth-child(3) {
  inset: -8px;
  animation: orbitPulse 5s ease-in-out infinite;
}

.profile-orbit i:nth-child(4) {
  inset: -22px;
}

.profile-identity {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.profile-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #cbe8d6;
  font-size: 8px;
}

.profile-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8ee0b2;
}

.profile-identity h2 {
  margin-top: 9px;
  font-family: var(--font-serif);
  font-size: 30px;
  letter-spacing: -0.04em;
}

.profile-identity > p {
  margin-top: 4px;
  color: rgb(255 255 255 / 55%);
  font-size: 9px;
}

.profile-identity blockquote {
  margin: 20px 0 0;
  color: rgb(255 255 255 / 70%);
  font-family: var(--font-serif);
  font-size: 15px;
}

.profile-progress {
  position: relative;
  z-index: 1;
  display: flex;
  width: 180px;
  flex-direction: column;
}

.profile-progress > span {
  color: rgb(255 255 255 / 44%);
  font-size: 8px;
}

.profile-progress > strong {
  margin-top: 6px;
  font-family: var(--font-serif);
  font-size: 33px;
}

.profile-progress > strong small {
  font-family: var(--font);
  font-size: 12px;
}

.profile-progress > i {
  height: 4px;
  overflow: hidden;
  margin-top: 7px;
  border-radius: 99px;
  background: rgb(255 255 255 / 14%);
}

.profile-progress > i b {
  display: block;
  height: 100%;
  background: #cfe3d4;
}

.account-grid {
  display: grid;
  align-items: start;
  gap: 14px;
  margin-top: 14px;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.75fr);
}

.account-card,
.overview-card,
.journey-section {
  border: 1px solid var(--line);
  border-radius: 19px;
  background: #fff;
}

.account-card {
  padding: 24px;
}

.identity-card > header span,
.journey-section > header span {
  color: var(--brand-500);
  font-size: 7px;
  font-weight: 800;
  letter-spacing: 0.13em;
}

.identity-card h2,
.journey-section h2 {
  margin-top: 6px;
  font-family: var(--font-serif);
  font-size: 24px;
  letter-spacing: -0.04em;
}

.identity-card header p,
.journey-section header p {
  margin-top: 5px;
  color: var(--ink-400);
  font-size: 8px;
}

.profile-form {
  display: grid;
  gap: 13px;
  margin-top: 22px;
}

.profile-form label {
  display: grid;
  gap: 6px;
}

.profile-form label > span {
  color: var(--ink-600);
  font-size: 9px;
  font-weight: 700;
}

.profile-form input {
  width: 100%;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  outline: 0;
  background: #fbfbf7;
  font-size: 10px;
}

.profile-form input:focus {
  border-color: var(--brand-300);
  background: #fff;
  box-shadow: 0 0 0 3px rgb(69 111 87 / 7%);
}

.profile-form small {
  color: var(--ink-400);
  font-size: 7px;
}

.profile-id {
  display: flex;
  gap: 7px;
}

.profile-id button {
  display: inline-flex;
  min-width: 70px;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  font-size: 9px;
  cursor: pointer;
}

.profile-save {
  display: inline-flex;
  width: max-content;
  min-height: 37px;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  padding: 0 13px;
  border: 0;
  border-radius: 999px;
  color: #fff;
  background: var(--brand-600);
  font-size: 9px;
  cursor: pointer;
}

.profile-save:disabled {
  opacity: 0.55;
  cursor: wait;
}

.profile-side {
  display: grid;
  gap: 14px;
}

.overview-card {
  display: grid;
  border-radius: 19px;
  overflow: hidden;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.overview-card > div {
  display: flex;
  min-height: 120px;
  justify-content: center;
  padding: 14px;
  border-right: 1px solid var(--line);
  flex-direction: column;
}

.overview-card > div:last-child {
  border-right: 0;
}

.overview-card span {
  color: var(--ink-400);
  font-size: 7px;
}

.overview-card strong {
  margin-top: 7px;
  font-family: var(--font-serif);
  font-size: 25px;
}

.account-tip {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 18px;
  border-radius: 17px;
  color: #6f5137;
  background: #f5eee1;
}

.account-tip strong {
  font-size: 10px;
}

.account-tip p {
  margin-top: 5px;
  font-size: 8px;
  line-height: 1.7;
}

.journey-section {
  margin-top: 14px;
  padding: 24px;
}

.journey-section > header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 19px;
  border-bottom: 1px solid var(--line);
}

.journey-count {
  color: var(--brand-600) !important;
  letter-spacing: 0 !important;
  font-size: 9px !important;
}

.journey-content {
  display: grid;
  gap: 24px;
  margin-top: 20px;
  grid-template-columns: 220px minmax(0, 1fr);
}

.journey-metrics {
  display: grid;
  gap: 7px;
}

.journey-metrics div {
  display: flex;
  min-height: 70px;
  justify-content: center;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fbfbf7;
  flex-direction: column;
}

.journey-metrics strong {
  font-family: var(--font-serif);
  font-size: 24px;
}

.journey-metrics span {
  margin-top: 3px;
  color: var(--ink-400);
  font-size: 7px;
}

.journey-list {
  display: grid;
}

.journey-list article {
  display: grid;
  min-height: 88px;
  align-items: center;
  gap: 12px;
  grid-template-columns: 28px 24px minmax(0, 1fr) auto;
}

.journey-index {
  color: var(--ink-300);
  font-family: var(--font-serif);
}

.journey-line {
  position: relative;
  align-self: stretch;
  display: grid;
  place-items: start center;
}

.journey-line::after {
  position: absolute;
  top: 18px;
  bottom: 0;
  width: 1px;
  background: var(--line);
  content: '';
}

.journey-list article:last-child .journey-line::after {
  display: none;
}

.journey-line i {
  position: relative;
  z-index: 1;
  width: 7px;
  height: 7px;
  margin-top: 11px;
  border: 2px solid var(--brand-400);
  border-radius: 50%;
  background: #fff;
}

.journey-copy {
  min-width: 0;
}

.journey-role {
  color: var(--brand-600);
  font-size: 7px;
  font-weight: 750;
}

.journey-role.is-anonymous {
  color: var(--ink-400);
}

.journey-copy h3 {
  margin-top: 4px;
  font-size: 11px;
}

.journey-copy p {
  margin-top: 4px;
  color: var(--ink-400);
  font-size: 8px;
}

.journey-list article > a {
  color: var(--ink-300);
}

.journey-state {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  color: var(--ink-400);
  text-align: center;
}

.journey-state strong {
  color: var(--ink-700);
  font-size: 10px;
}

.journey-state p {
  font-size: 8px;
}

.journey-state.is-error {
  color: #a14b3f;
}

@keyframes orbitPulse {
  50% {
    opacity: 0.45;
    transform: scale(1.05);
  }
}

@media (max-width: 900px) {
  .account-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .account-grid,
  .journey-content {
    grid-template-columns: 1fr;
  }

  .profile-hero {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .profile-progress {
    grid-column: 1 / -1;
    width: 100%;
  }
}

@media (max-width: 600px) {
  .account-page {
    padding: 22px 16px 42px;
  }

  .profile-hero {
    padding: 24px 18px;
    grid-template-columns: 1fr;
  }

  .profile-orbit {
    width: 96px;
    height: 96px;
  }

  .account-heading-actions {
    flex-wrap: wrap;
  }

  .overview-card {
    grid-template-columns: 1fr;
  }

  .overview-card > div {
    min-height: 72px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .overview-card > div:last-child {
    border-bottom: 0;
  }
}
</style>
