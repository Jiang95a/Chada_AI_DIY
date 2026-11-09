<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { listReferrals, updateReferralStatus, type ReferralTodo } from '@/services/referrals'

const toast = useToast()
const items = ref<ReferralTodo[]>([])
const loading = ref(true)
const error = ref('')
const filter = ref<'pending' | 'contacted' | 'closed'>('pending')
const busyId = ref('')

const pendingCount = computed(() => items.value.filter((item) => item.status === 'pending').length)
const overdueCount = computed(
  () => items.value.filter((item) => item.status === 'pending' && item.pendingHours > 24).length
)
const visibleItems = computed(() => items.value.filter((item) => item.status === filter.value))

function overdue(item: ReferralTodo): boolean {
  return item.status === 'pending' && item.pendingHours > 24
}

function timeLabel(value: string): string {
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) return '时间未知'
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    items.value = await listReferrals({ limit: 50 })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '转介待办加载失败'
  } finally {
    loading.value = false
  }
}

async function setStatus(item: ReferralTodo, status: 'contacted' | 'closed'): Promise<void> {
  if (busyId.value) return
  busyId.value = item.id
  try {
    const updated = await updateReferralStatus(item.id, status)
    const target = items.value.find((entry) => entry.id === item.id)
    if (target) {
      target.status = updated.status as ReferralTodo['status']
      target.handledAt = updated.handledAt
    }
    toast.pushToast(status === 'contacted' ? '已标记为联系过' : '工单已关闭', 'success')
  } catch (err) {
    toast.pushToast(err instanceof Error ? err.message : '状态更新失败', 'error')
  } finally {
    busyId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <WorkspaceShell>
    <main class="referrals-page">
      <header class="referrals-heading">
        <div>
          <p class="eyebrow">Referral queue</p>
          <h1>转介待办</h1>
          <p>这里只显示参与者主动提交的联系意愿，不包含他们的活动回答内容。</p>
        </div>
        <button class="app-button app-button--secondary" type="button" :disabled="loading" @click="load">
          <Icon name="refresh" :size="15" />{{ loading ? '加载中' : '刷新' }}
        </button>
      </header>

      <section class="referral-stats">
        <article>
          <span>待处理</span>
          <strong>{{ pendingCount }}</strong>
          <small>需要组织者跟进的联系意愿</small>
        </article>
        <article :class="{ 'is-danger': overdueCount > 0 }">
          <span>超过 24 小时</span>
          <strong>{{ overdueCount }}</strong>
          <small>{{ overdueCount ? '请优先处理这些工单' : '当前没有超时工单' }}</small>
        </article>
      </section>

      <div class="referral-tabs">
        <button
          v-for="tab in [
            { id: 'pending', label: '待处理' },
            { id: 'contacted', label: '已联系' },
            { id: 'closed', label: '已关闭' },
          ] as const"
          :key="tab.id"
          type="button"
          :class="{ 'is-active': filter === tab.id }"
          @click="filter = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>

      <section v-if="loading" class="referral-state">
        <Icon name="refresh" :size="22" />
        <strong>正在加载转介待办</strong>
      </section>
      <section v-else-if="error" class="referral-state is-error">
        <Icon name="alert" :size="22" />
        <strong>转介待办暂时不可用</strong>
        <p>{{ error }}</p>
        <button class="app-button app-button--secondary" type="button" @click="load">重新加载</button>
      </section>
      <section v-else-if="!visibleItems.length" class="referral-state">
        <Icon name="check-circle" :size="22" />
        <strong
          >当前没有{{ filter === 'pending' ? '待处理' : filter === 'contacted' ? '已联系' : '已关闭' }}工单</strong
        >
      </section>
      <section v-else class="referral-list">
        <article
          v-for="item in visibleItems"
          :key="item.id"
          class="referral-card"
          :class="{ 'is-overdue': overdue(item) }"
        >
          <div class="referral-card-head">
            <div>
              <span>来自活动</span>
              <h2>{{ item.activityTitle }}</h2>
            </div>
            <em :class="`is-${item.status}`">{{ overdue(item) ? '已超时' : item.status }}</em>
          </div>
          <div class="referral-card-info">
            <span
              ><Icon name="phone" :size="14" /><strong>{{ item.contact || '未填写联系方式' }}</strong></span
            >
            <span><Icon name="clock" :size="14" />希望时间：{{ item.preferredTime || '未说明' }}</span>
            <span><Icon name="bell" :size="14" />提交于 {{ timeLabel(item.createdAt) }}</span>
          </div>
          <footer>
            <RouterLink :to="`/p/${item.publicSlug}`" target="_blank">
              <Icon name="eye" :size="14" />打开活动
            </RouterLink>
            <div>
              <button
                v-if="item.status === 'pending'"
                type="button"
                :disabled="busyId === item.id"
                @click="setStatus(item, 'contacted')"
              >
                <Icon name="check" :size="14" />标记已联系
              </button>
              <button
                v-if="item.status !== 'closed'"
                class="is-close"
                type="button"
                :disabled="busyId === item.id"
                @click="setStatus(item, 'closed')"
              >
                <Icon name="close" :size="14" />关闭工单
              </button>
            </div>
          </footer>
        </article>
      </section>
    </main>
  </WorkspaceShell>
</template>

<style scoped>
.referrals-page {
  width: min(1080px, 100%);
  margin: 0 auto;
  padding: 30px 34px 56px;
}

.referrals-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.referrals-heading h1 {
  margin: 3px 0 7px;
  font-size: 25px;
}

.referrals-heading p {
  color: var(--ink-500);
  font-size: 10px;
}

.eyebrow {
  color: var(--ink-400);
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.referral-stats {
  display: grid;
  gap: 10px;
  margin-top: 22px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.referral-stats article {
  display: flex;
  min-height: 105px;
  justify-content: center;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  flex-direction: column;
}

.referral-stats span {
  color: var(--ink-500);
  font-size: 9px;
  font-weight: 700;
}

.referral-stats strong {
  margin-top: 5px;
  font-size: 30px;
}

.referral-stats small {
  margin-top: 4px;
  color: var(--ink-400);
  font-size: 8px;
}

.referral-stats article.is-danger {
  border-color: #f2d2cf;
  background: #fff8f7;
}

.referral-stats article.is-danger strong,
.referral-stats article.is-danger span {
  color: #b5473d;
}

.referral-tabs {
  display: flex;
  gap: 5px;
  margin-top: 22px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  width: max-content;
}

.referral-tabs button {
  min-height: 32px;
  padding: 0 13px;
  border: 0;
  border-radius: 8px;
  color: var(--ink-500);
  background: transparent;
  font-size: 9px;
  cursor: pointer;
}

.referral-tabs button.is-active {
  color: var(--brand-700);
  background: var(--brand-50);
}

.referral-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.referral-card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
}

.referral-card.is-overdue {
  border-color: #efb3ad;
  background: #fffafa;
}

.referral-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.referral-card-head span {
  color: var(--ink-400);
  font-size: 8px;
}

.referral-card-head h2 {
  margin-top: 5px;
  font-size: 16px;
}

.referral-card-head em {
  padding: 4px 8px;
  border-radius: 999px;
  color: #8a6541;
  background: #f8f0e4;
  font-size: 8px;
  font-style: normal;
  font-weight: 700;
}

.referral-card-head em.is-overdue,
.referral-card.is-overdue .referral-card-head em {
  color: #b5473d;
  background: #fdecea;
}

.referral-card-info {
  display: grid;
  gap: 7px;
  margin-top: 15px;
}

.referral-card-info span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-500);
  font-size: 9px;
}

.referral-card-info svg {
  color: var(--brand-500);
}

.referral-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 15px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.referral-card footer > a,
.referral-card footer button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  border: 1px solid var(--line);
  border-radius: 7px;
  color: var(--ink-500);
  background: #fff;
  font-size: 8px;
  cursor: pointer;
}

.referral-card footer > div {
  display: flex;
  gap: 6px;
}

.referral-card footer button.is-close {
  color: #b5473d;
}

.referral-state {
  display: flex;
  min-height: 260px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
  border: 1px dashed var(--line-strong);
  border-radius: 16px;
  color: var(--ink-400);
  text-align: center;
}

.referral-state strong {
  color: var(--ink-700);
  font-size: 11px;
}

.referral-state p {
  font-size: 9px;
}

.referral-state.is-error {
  color: #a14b3f;
}

@media (max-width: 720px) {
  .referrals-page {
    padding: 22px 18px 42px;
  }

  .referral-stats {
    grid-template-columns: 1fr;
  }

  .referral-tabs {
    width: 100%;
  }

  .referral-tabs button {
    flex: 1;
  }

  .referral-card footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
