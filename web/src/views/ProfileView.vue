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
</script>

<template>
  <WorkspaceShell>
    <main class="account-page">
      <header class="account-heading">
        <div>
          <p class="eyebrow">Account</p>
          <h1>个人资料</h1>
          <p>管理公开昵称和账号信息，了解你的心晴活动组织与参与数据。</p>
        </div>
        <div class="account-heading-actions">
          <RouterLink class="app-button app-button--secondary" to="/settings">
            <Icon name="sliders" :size="15" />偏好设置
          </RouterLink>
          <RouterLink class="app-button app-button--secondary" to="/help">
            <Icon name="help" :size="15" />帮助与反馈
          </RouterLink>
        </div>
      </header>

      <section class="profile-hero">
        <div class="profile-avatar">{{ initials }}</div>
        <div class="profile-identity">
          <span class="profile-status"><i />{{ auth.user?.status === 'active' ? '账号正常' : '账号受限' }}</span>
          <h2>{{ auth.user?.displayName || '未命名用户' }}</h2>
          <p>{{ auth.user?.email || '正在加载账号信息…' }}</p>
        </div>
        <div class="profile-completion">
          <span>资料完整度</span>
          <strong>{{ completion }}%</strong>
          <i><b :style="{ width: `${completion}%` }" /></i>
        </div>
      </section>

      <div class="account-grid">
        <section class="account-card">
          <div class="account-card-head">
            <span><Icon name="users" :size="17" /></span>
            <div>
              <h2>基本信息</h2>
              <p>昵称会在工作台和团队协作中显示。</p>
            </div>
          </div>
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
            <div class="profile-form-actions">
              <button class="app-button app-button--primary" type="submit" :disabled="saving">
                <Icon name="check" :size="15" />{{ saving ? '保存中…' : '保存资料' }}
              </button>
            </div>
          </form>
        </section>

        <aside class="profile-side">
          <section class="account-card">
            <div class="account-card-head">
              <span><Icon name="chart" :size="17" /></span>
              <div>
                <h2>创作概览</h2>
                <p>当前账号的心晴活动数据。</p>
              </div>
            </div>
            <div class="profile-metrics">
              <div>
                <strong>{{ projectStore.projectCount }}</strong
                ><span>全部心晴活动</span>
              </div>
              <div>
                <strong>{{ publishedCount }}</strong
                ><span>已发布</span>
              </div>
              <div>
                <strong>{{ submissionCount }}</strong
                ><span>累计提交</span>
              </div>
            </div>
          </section>
          <section class="account-tip">
            <Icon name="info" :size="18" />
            <div>
              <strong>账号安全提示</strong>
              <p>心理活动页面应只收集必要信息，不要在公开页面暴露密码或其他同学的敏感资料。</p>
            </div>
          </section>
        </aside>
      </div>

      <section class="portfolio-card">
        <div class="account-card-head">
          <span><Icon name="layers" :size="17" /></span>
          <div>
            <h2>我的参与轨迹</h2>
            <p>只展示你本人参与过或组织过的已发布活动，匿名参与不会显示活动回答。</p>
          </div>
        </div>
        <div v-if="portfolioLoading" class="portfolio-state">
          <Icon name="refresh" :size="20" />
          <strong>正在加载参与轨迹</strong>
        </div>
        <div v-else-if="portfolioError" class="portfolio-state is-error">
          <Icon name="alert" :size="20" />
          <strong>参与轨迹暂时不可用</strong>
          <p>{{ portfolioError }}</p>
        </div>
        <div v-else-if="portfolio" class="portfolio-body">
          <div class="portfolio-metrics">
            <div>
              <strong>{{ portfolio.totalActivities }}</strong
              ><span>总场次</span>
            </div>
            <div>
              <strong>{{ portfolio.wellbeingCount }}</strong
              ><span>心理活动</span>
            </div>
            <div>
              <strong>{{ portfolio.careerCount }}</strong
              ><span>生涯活动</span>
            </div>
          </div>
          <div v-if="portfolio.items.length" class="portfolio-list">
            <article v-for="item in portfolio.items" :key="item.projectId">
              <span class="portfolio-role" :class="`is-${item.role}`">{{ roleLabel(item.role) }}</span>
              <div>
                <strong>{{ item.title }}</strong>
                <small>{{ item.activityType }} · {{ monthLabel(item.firstJoinedAt) }}</small>
              </div>
              <RouterLink :to="`/p/${item.slug}`" target="_blank"><Icon name="arrow-right" :size="15" /></RouterLink>
            </article>
          </div>
          <p v-else class="portfolio-empty">还没有参与轨迹，先去参加一场心晴活动吧。</p>
        </div>
      </section>
    </main>
  </WorkspaceShell>
</template>

<style scoped>
.account-page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 34px 34px 60px;
}
.account-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}
.account-heading h1 {
  margin: 0;
  font-size: clamp(30px, 4vw, 42px);
  letter-spacing: -0.05em;
}
.account-heading > div > p:last-child {
  margin: 9px 0 0;
  color: var(--muted);
  font-size: 11px;
}
.account-heading-actions {
  display: flex;
  gap: 8px;
}
.account-heading-actions .app-button {
  min-height: 39px;
  font-size: 10px;
}
.profile-hero {
  display: grid;
  align-items: center;
  gap: 18px;
  margin-top: 26px;
  padding: 24px;
  border: 1px solid #dbe4f4;
  border-radius: 20px;
  background:
    radial-gradient(circle at 85% 15%, rgb(36 87 214 / 12%), transparent 30%), linear-gradient(135deg, #f7f9ff, #fff);
  grid-template-columns: auto minmax(0, 1fr) minmax(180px, 260px);
}
.profile-avatar {
  display: grid;
  width: 74px;
  height: 74px;
  border-radius: 23px;
  color: #fff;
  background: linear-gradient(145deg, #f28b65, #e95867);
  font-size: 29px;
  font-weight: 800;
  box-shadow: 0 15px 28px -18px rgb(233 88 103 / 80%);
  place-items: center;
}
.profile-identity {
  min-width: 0;
}
.profile-identity h2 {
  margin: 7px 0 4px;
  font-size: 22px;
}
.profile-identity p {
  margin: 0;
  color: var(--muted);
  font-size: 10px;
}
.profile-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--mint);
  font-size: 8px;
  font-weight: 750;
}
.profile-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentcolor;
}
.profile-completion {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.profile-completion span {
  color: var(--muted);
  font-size: 9px;
}
.profile-completion strong {
  font-size: 23px;
}
.profile-completion > i {
  height: 5px;
  overflow: hidden;
  margin-top: 3px;
  border-radius: 99px;
  background: #e8edf5;
}
.profile-completion > i b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--primary), var(--mint));
}
.account-grid {
  display: grid;
  align-items: start;
  gap: 14px;
  margin-top: 14px;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.75fr);
}
.profile-side {
  display: grid;
  gap: 14px;
}
.account-card {
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}
.account-card-head {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding-bottom: 17px;
  border-bottom: 1px solid var(--line);
}
.account-card-head > span {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 11px;
  color: var(--primary);
  background: var(--primary-soft);
  place-items: center;
}
.account-card-head h2 {
  margin: 0;
  font-size: 14px;
}
.account-card-head p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 9px;
}
.profile-form {
  display: grid;
  gap: 15px;
  margin-top: 18px;
}
.profile-form label {
  display: grid;
  gap: 7px;
}
.profile-form label > span {
  color: var(--ink-soft);
  font-size: 9px;
  font-weight: 750;
}
.profile-form input {
  width: 100%;
  height: 41px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  outline: none;
  background: var(--surface-soft);
  font-size: 11px;
}
.profile-form input:focus {
  border-color: #9db5f2;
  background: #fff;
  box-shadow: 0 0 0 3px rgb(36 87 214 / 8%);
}
.profile-form small {
  color: var(--muted);
  font-size: 8px;
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
.profile-form-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 3px;
}
.profile-metrics {
  display: grid;
  gap: 8px;
  margin-top: 18px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.profile-metrics div {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 4px;
  padding: 13px 5px;
  border-radius: 12px;
  background: var(--surface-soft);
}
.profile-metrics strong {
  font-size: 22px;
}
.profile-metrics span {
  color: var(--muted);
  font-size: 8px;
}
.account-tip {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px;
  border-radius: 15px;
  color: #8a5b0c;
  background: var(--amber-soft);
}
.account-tip strong {
  font-size: 10px;
}
.account-tip p {
  margin: 5px 0 0;
  font-size: 9px;
  line-height: 1.7;
}

.portfolio-card {
  margin-top: 14px;
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}

.portfolio-metrics {
  display: grid;
  gap: 8px;
  margin-top: 18px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.portfolio-metrics div {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 4px;
  padding: 15px 5px;
  border-radius: 12px;
  background: var(--surface-soft);
}

.portfolio-metrics strong {
  font-size: 23px;
}

.portfolio-metrics span {
  color: var(--muted);
  font-size: 8px;
}

.portfolio-list {
  display: grid;
  gap: 7px;
  margin-top: 18px;
}

.portfolio-list article {
  display: grid;
  align-items: center;
  gap: 11px;
  padding: 11px 12px;
  border: 1px solid var(--line);
  border-radius: 11px;
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.portfolio-role {
  padding: 4px 7px;
  border-radius: 999px;
  color: var(--primary);
  background: var(--primary-soft);
  font-size: 7px;
  font-weight: 700;
}

.portfolio-role.is-anonymous {
  color: var(--ink-500);
  background: var(--ink-100);
}

.portfolio-list article > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.portfolio-list strong {
  font-size: 11px;
}

.portfolio-list small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 8px;
}

.portfolio-list a {
  color: var(--ink-400);
}

.portfolio-state,
.portfolio-empty {
  display: flex;
  min-height: 150px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  color: var(--muted);
  text-align: center;
}

.portfolio-state strong {
  color: var(--ink-700);
  font-size: 10px;
}

.portfolio-state p,
.portfolio-empty {
  font-size: 9px;
}

.portfolio-state.is-error {
  color: #b5473d;
}

@media (max-width: 850px) {
  .account-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .account-grid {
    grid-template-columns: 1fr;
  }
  .portfolio-metrics {
    grid-template-columns: 1fr 1fr 1fr;
  }
  .profile-hero {
    grid-template-columns: auto 1fr;
  }
  .profile-completion {
    grid-column: 1 / -1;
  }
}
@media (max-width: 600px) {
  .account-page {
    padding: 22px 18px 44px;
  }
  .account-heading-actions {
    width: 100%;
    flex-wrap: wrap;
  }
  .account-heading-actions .app-button {
    flex: 1;
  }
  .profile-avatar {
    width: 60px;
    height: 60px;
    border-radius: 19px;
  }
}
</style>
