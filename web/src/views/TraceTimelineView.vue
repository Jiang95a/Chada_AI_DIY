<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import TracePulseCard from '@/components/public/TracePulseCard.vue'
import { useToast } from '@/composables/useToast'
import { useTraceStore } from '@/stores/trace'
import type { ParticipantTrace } from '@/types/trace'

/**
 * 我的轨迹：只显示用户确认过的摘要、标签变化和未决问题。
 * 默认不显示流水；数据只在本机（登录后由 store 尝试同步）。
 */

const traceStore = useTraceStore()
const toast = useToast()
const items = ref<ParticipantTrace[]>([])
const changesOnly = ref(false)
const loading = ref(false)

const filtered = computed(() => (changesOnly.value ? items.value.filter((item) => Boolean(item.change)) : items.value))

function refresh(): void {
  items.value = traceStore.list()
}

function dateLabel(at: number): string {
  const date = new Date(at)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日 ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function domainLabel(trace: ParticipantTrace): string {
  return trace.domain === 'career' ? '职规活动' : '心晴活动'
}

function removeItem(activityId: string): void {
  traceStore.remove(activityId)
  refresh()
  toast.pushToast('这条轨迹已从这台设备删除', 'success')
}

function clearAll(): void {
  if (!window.confirm('清除这台设备上的全部轨迹？这个操作不能撤销。')) return
  traceStore.clearAll()
  refresh()
  toast.pushToast('这台设备上的轨迹已清空', 'success')
}

onMounted(async () => {
  loading.value = true
  try {
    await traceStore.syncAll()
  } finally {
    refresh()
    loading.value = false
  }
})
</script>

<template>
  <main class="trace-page">
    <section class="trace-hero">
      <span class="trace-kicker">MY TRACE · 我的轨迹</span>
      <h1>你是怎么想过来的</h1>
      <p>
        这里只有你自己留下的摘要、标签变化和还没回答的问题。默认不显示流水；没有选择「存进我的轨迹」的匿名便签不会出现在这里。
      </p>
      <div class="trace-hero-actions">
        <RouterLink to="/campus">去看活动 <Icon name="arrow-right" :size="14" /></RouterLink>
        <button v-if="items.length" type="button" :class="{ 'is-on': changesOnly }" @click="changesOnly = !changesOnly">
          {{ changesOnly ? '看全部' : '只看变过主意的' }}
        </button>
      </div>
    </section>

    <TracePulseCard v-if="items.length" :traces="items" :show-link="false" class="trace-pulse" />

    <section v-if="loading && !items.length" class="trace-empty">
      <Icon name="refresh" :size="28" />
      <strong>正在同步你的轨迹</strong>
      <p>如果你登录过，其它设备上留下的记录会在这里出现。</p>
    </section>

    <section v-else-if="!items.length" class="trace-empty">
      <Icon name="layers" :size="28" />
      <strong>这里还没有东西</strong>
      <p>参加一场活动，写下一句话，选择「存进我的轨迹」之后，它会出现在这里。</p>
      <RouterLink to="/campus">找一场活动 <Icon name="arrow-right" :size="14" /></RouterLink>
    </section>

    <section v-else class="trace-list">
      <div class="trace-list-head">
        <h2>{{ changesOnly ? '你改过主意的地方' : '按时间留下的话' }}</h2>
        <span>{{ filtered.length }} 条</span>
      </div>

      <p v-if="!filtered.length" class="trace-none">还没有「改主意」的记录。不是每场活动都要改变什么。</p>

      <article v-for="item in filtered" :key="item.activityId" class="trace-item">
        <header>
          <time>{{ dateLabel(item.updatedAt) }}</time>
          <span>{{ domainLabel(item) }}</span>
          <button type="button" aria-label="删除这条轨迹" @click="removeItem(item.activityId)">删除</button>
        </header>

        <p class="trace-summary">{{ item.summary || '你还没给这段时间留下摘要。' }}</p>

        <div v-if="item.tags.length" class="trace-tags">
          <span v-for="tag in item.tags" :key="`${item.activityId}-${tag.content}`">{{ tag.content }}</span>
        </div>

        <div v-if="item.notes?.length" class="trace-notes">
          <span>你主动留下的话</span>
          <p v-for="note in item.notes" :key="note.candidateTraceId ?? note.at">{{ note.text }}</p>
        </div>

        <dl class="trace-meta">
          <template v-if="item.change">
            <dt>你变过一次</dt>
            <dd>{{ item.change }}</dd>
          </template>
          <template v-if="item.openQuestion">
            <dt>还没回答</dt>
            <dd>{{ item.openQuestion }}</dd>
          </template>
          <template v-if="item.promise">
            <dt>你答应过</dt>
            <dd>{{ item.promise }}</dd>
          </template>
        </dl>

        <RouterLink :to="`/p/${item.activityId}`" class="trace-back">
          回到这场活动 <Icon name="arrow-right" :size="13" />
        </RouterLink>
      </article>

      <button type="button" class="trace-clear" @click="clearAll">
        <Icon name="trash" :size="13" />清除这台设备上的全部轨迹
      </button>
    </section>
  </main>
</template>

<style scoped>
.trace-page {
  width: min(1120px, calc(100% - 36px));
  margin: 0 auto;
  padding: 34px 0 48px;
}

.trace-hero {
  padding: 30px 0 26px;
  border-bottom: 1px solid var(--line);
}

.trace-pulse {
  margin-top: 22px;
}

.trace-kicker {
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.trace-hero h1 {
  margin: 12px 0 10px;
  font-family: var(--font-serif);
  font-size: clamp(30px, 4vw, 44px);
  line-height: 1.2;
  color: var(--ink-900);
}

.trace-hero p {
  max-width: 760px;
  margin: 0;
  font-size: 14.5px;
  line-height: 1.85;
  color: var(--ink-600);
}

.trace-hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.trace-hero-actions a,
.trace-hero-actions button,
.trace-empty a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-button);
  background: #fff;
  color: var(--ink-700);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
}

.trace-hero-actions a,
.trace-empty a {
  border-color: var(--brand-600);
  background: var(--brand-600);
  color: #fff;
}

.trace-hero-actions button.is-on {
  border-color: var(--brand-400);
  background: var(--brand-50);
  color: var(--brand-700);
}

.trace-empty {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
  margin-top: 26px;
  padding: 34px;
  border: 1px dashed var(--line-strong);
  border-radius: 10px;
  background: #fff;
  color: var(--ink-500);
}

.trace-empty strong {
  margin-top: 4px;
  color: var(--ink-900);
  font-size: 18px;
}

.trace-empty p {
  max-width: 480px;
  margin: 0 0 8px;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--ink-600);
}

.trace-list {
  margin-top: 24px;
}

.trace-list-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.trace-list-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 800;
  color: var(--ink-900);
}

.trace-list-head span {
  color: var(--ink-500);
  font-size: 12px;
}

.trace-none {
  padding: 18px;
  border-radius: 8px;
  background: var(--surface-soft);
  color: var(--ink-600);
  font-size: 13px;
}

.trace-item {
  margin-bottom: 12px;
  padding: 18px 20px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
}

.trace-item header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.trace-item time {
  color: var(--ink-900);
  font-size: 12.5px;
  font-weight: 700;
}

.trace-item header > span {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 11px;
  font-weight: 600;
}

.trace-item header > button {
  margin-left: auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-400);
  font-size: 11.5px;
  cursor: pointer;
}

.trace-item header > button:hover {
  color: var(--danger);
  text-decoration: underline;
}

.trace-summary {
  max-width: 860px;
  margin: 12px 0 0;
  font-size: 15.5px;
  line-height: 1.75;
  color: var(--ink-900);
}

.trace-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.trace-tags span {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--ink-50);
  color: var(--ink-600);
  font-size: 11.5px;
}

.trace-notes {
  margin-top: 11px;
  padding: 10px 12px;
  border-left: 3px solid var(--brand-300);
  border-radius: 0 6px 6px 0;
  background: var(--brand-50);
}

.trace-notes > span {
  color: var(--brand-700);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.trace-notes p {
  margin: 5px 0 0;
  color: var(--ink-800);
  font-size: 13px;
  line-height: 1.7;
}

.trace-meta {
  display: grid;
  grid-template-columns: 86px 1fr;
  gap: 6px 12px;
  margin: 13px 0 0;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.trace-meta dt {
  color: var(--ink-500);
  font-size: 12px;
}

.trace-meta dd {
  margin: 0;
  color: var(--ink-700);
  font-size: 12.5px;
  line-height: 1.7;
}

.trace-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 14px;
  color: var(--brand-700);
  font-size: 12.5px;
  font-weight: 700;
  text-decoration: none;
}

.trace-back:hover {
  text-decoration: underline;
}

.trace-clear {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 18px;
  padding: 8px 12px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-button);
  background: transparent;
  color: var(--ink-600);
  font-size: 12px;
  cursor: pointer;
}

.trace-clear:hover {
  border-color: var(--danger);
  color: var(--danger);
}

@media (max-width: 640px) {
  .trace-page {
    padding-top: 22px;
  }

  .trace-meta {
    grid-template-columns: 1fr;
    gap: 3px;
  }

  .trace-meta dd + dt {
    margin-top: 8px;
  }
}
</style>
