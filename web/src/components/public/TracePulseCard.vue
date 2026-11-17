<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import type { ParticipantTrace, TraceEvent } from '@/types/trace'

/**
 * 变化卡：把散在各场活动里的 TraceEvent 画成一条时间线。
 *
 * 只读摘要和标签，不展示输入原文；没有事件的老轨迹用「最后一次更新时间 + 当前标签」
 * 合成一个点，保证老数据也能看到东西。
 */
const props = withDefaults(
  defineProps<{
    traces: ParticipantTrace[]
    compact?: boolean
    showLink?: boolean
  }>(),
  { compact: false, showLink: true }
)

const TAG_COLORS: Record<string, string> = {
  压力: '#c08a5e',
  睡眠: '#6d8f91',
  人际: '#5f8a73',
  孤独: '#8b7fae',
  家庭: '#b45e55',
  自我评价: '#9a705a',
  情绪: '#b88c72',
  学业: '#486f5e',
  身体: '#7f9a8a',
  想休息: '#94b6a1',
  想表达: '#c5967d',
  想认识人: '#6d937f',
  迷茫: '#6d8f91',
  犹豫: '#b88c72',
  方向: '#486f5e',
  能力: '#9a705a',
  家庭期待: '#b45e55',
  怕选错: '#b45e55',
  兴趣: '#5f8a73',
  想行动: '#c08a5e',
  写下过一句话: '#8b7fae',
}

const events = computed<TraceEvent[]>(() => {
  const list: TraceEvent[] = []
  for (const trace of props.traces) {
    const history = trace.history?.length
      ? trace.history
      : trace.tags.length || trace.summary
        ? [
            {
              at: trace.updatedAt,
              kind: 'input' as const,
              activityId: trace.activityId,
              tags: trace.tags.map((tag) => tag.content),
              summary: trace.summary,
              change: trace.change,
            },
          ]
        : []
    list.push(...history)
  }
  return list.sort((a, b) => a.at - b.at).slice(-60)
})

const eventCount = computed(() => events.value.length)
const lastAt = computed(() => events.value[events.value.length - 1]?.at ?? 0)
const lastActivityId = computed(() => events.value[events.value.length - 1]?.activityId ?? '')

const topTags = computed(() => {
  const counts = new Map<string, number>()
  for (const event of events.value) {
    for (const tag of event.tags) counts.set(tag, (counts.get(tag) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([content, count]) => ({ content, count, color: TAG_COLORS[content] ?? '#94b6a1' }))
    .sort((a, b) => b.count - a.count)
    .slice(0, props.compact ? 3 : 5)
})
const maxTagCount = computed(() => Math.max(1, ...topTags.value.map((tag) => tag.count)))

const latestChange = computed(() => {
  for (let index = events.value.length - 1; index >= 0; index -= 1) {
    if (events.value[index]?.change) return events.value[index]!.change!
  }
  return ''
})

const relativeTime = computed(() => {
  if (!lastAt.value) return '还没有记录'
  const diff = Math.max(0, Date.now() - lastAt.value)
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
})

function nextAction(tag: string): string {
  switch (tag) {
    case '睡眠':
      return '今晚只改一件事：把手机放到够不着的地方，明天回来记一句。'
    case '压力':
      return '挑一件 10 分钟内能做完的事，做完就停，不要先列长清单。'
    case '人际':
      return '给一个你觉得安全的人发条消息，不用解释太多。'
    case '家庭':
      return '写一段不会发送给家里的话，先把想说的说完。'
    case '孤独':
      return '找一个愿意听你说两句的人，聊五分钟就够。'
    case '自我评价':
      return '找一件能反驳那句话的小事，只找一件。'
    default:
      return '回去写一句，看看这次是不是还这么想。'
  }
}

const suggestion = computed(() => {
  if (!events.value.length) {
    return '写下一句之后，这里会开始出现你的变化。不用写完整，几个字也算。'
  }
  if (latestChange.value) {
    const quoted = [...latestChange.value.matchAll(/「([^」]+)」/g)].map((match) => match[1])
    const target = quoted[quoted.length - 1] ?? topTags.value[0]?.content ?? ''
    return `你最近留下了一次变化：${latestChange.value}。下一步：${nextAction(target)}`
  }
  switch (topTags.value[0]?.content) {
    case '睡眠':
      return '最近「睡眠」出现最多。今晚只改一件事：把手机放到够不着的地方，明天回来记一句。'
    case '压力':
      return '「压力」出现最多。挑一件 10 分钟内能做完的事，不要先列一长串清单。'
    case '人际':
      return '「人际」出现最多。先迈半步：给一个你觉得安全的人发条消息，不用解释太多。'
    case '家庭':
      return '「家庭」出现最多。先不急着回应，写一段不会发送的话，把想说的说完。'
    case '孤独':
      return '「孤独」出现最多。不用马上变热闹，先找一个愿意听你说两句的人。'
    case '自我评价':
      return '「自我评价」出现最多。找一件能反驳刚才那句话的小事，只找一件。'
    default:
      return '你已经开始留下记录了。下一次打开，先看看上次写的那句，再决定要不要改。'
  }
})

const suggestionLink = computed(() =>
  lastActivityId.value ? `/p/${lastActivityId.value}` : '/campus'
)
const suggestionLabel = computed(() => (lastActivityId.value ? '回去写一句' : '找一场活动'))

const days = computed(() => {
  const list: Date[] = []
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  for (let index = 13; index >= 0; index -= 1) {
    const day = new Date(today)
    day.setDate(day.getDate() - index)
    list.push(day)
  }
  return list
})

const buckets = computed(() =>
  days.value.map((day) => {
    const next = new Date(day)
    next.setDate(next.getDate() + 1)
    const items = events.value.filter((event) => event.at >= day.getTime() && event.at < next.getTime())
    return {
      day,
      count: items.length,
      color: TAG_COLORS[items[0]?.tags[0] ?? ''] ?? '#94b6a1',
      isToday: day.getTime() === days.value[days.value.length - 1]?.getTime(),
    }
  })
)

const maxDayCount = computed(() => Math.max(1, ...buckets.value.map((bucket) => bucket.count)))
const todayIndex = computed(() => buckets.value.length - 1)
const axisStartLabel = computed(() => {
  const day = days.value[0]
  return day ? `${day.getMonth() + 1} 月 ${day.getDate()} 日` : ''
})
</script>

<template>
  <section class="pulse" :class="{ 'is-compact': compact }">
    <header class="pulse-head">
      <div>
        <span class="pulse-kicker">TRACE PULSE · 变化卡</span>
        <h2>这段时间，你在怎么变</h2>
      </div>
      <span class="pulse-live"><i />{{ eventCount }} 次记录</span>
    </header>

    <p class="pulse-sub">
      最近 {{ relativeTime }}
      <RouterLink v-if="showLink" to="/trace">去轨迹页 <Icon name="arrow-right" :size="12" /></RouterLink>
    </p>

    <div class="pulse-grid">
      <div class="pulse-main">
        <div class="pulse-visual">
          <svg viewBox="0 0 560 140" role="img" aria-label="最近十四天的记录变化">
            <line x1="24" y1="124" x2="531" y2="124" stroke="#e3ece6" stroke-width="1" />
            <line x1="24" y1="96" x2="531" y2="96" stroke="#edf3ef" stroke-width="1" stroke-dasharray="3 5" />
            <g v-for="(bucket, index) in buckets" :key="bucket.day.getTime()">
              <rect
                :x="24 + index * 39 - 7"
                y="50"
                width="14"
                height="62"
                rx="4"
                fill="#f2f7f3"
              />
              <rect
                v-if="bucket.count"
                :x="24 + index * 39 - 7"
                :y="112 - (bucket.count / maxDayCount) * 62"
                width="14"
                :height="(bucket.count / maxDayCount) * 62"
                rx="4"
                :fill="bucket.color"
                fill-opacity="0.82"
              />
              <rect
                v-if="bucket.count && index === todayIndex"
                :x="24 + index * 39 - 9"
                :y="112 - (bucket.count / maxDayCount) * 62 - 2"
                width="18"
                :height="(bucket.count / maxDayCount) * 62 + 4"
                rx="5"
                fill="none"
                stroke="#5f8a73"
                stroke-opacity="0.4"
                stroke-width="2"
              />
            </g>
          </svg>
          <div class="pulse-axis">
            <span>{{ axisStartLabel }}</span>
            <span>7 天前</span>
            <span>今天</span>
          </div>
        </div>

        <div v-if="topTags.length" class="pulse-tags">
          <div v-for="tag in topTags" :key="tag.content" class="pulse-tag-row">
            <span class="pulse-dot" :style="{ background: tag.color }" />
            <span class="pulse-tag-name">{{ tag.content }}</span>
            <span class="pulse-tag-bar"><i :style="{ width: `${(tag.count / maxTagCount) * 100}%`, background: tag.color }" /></span>
            <em>{{ tag.count }}</em>
          </div>
        </div>
      </div>

      <div class="pulse-side">
        <p v-if="latestChange" class="pulse-change">
          <Icon name="refresh" :size="13" />{{ latestChange }}
        </p>

        <div class="pulse-suggest">
          <span class="pulse-suggest-icon"><Icon name="spark" :size="16" /></span>
          <div>
            <strong>下一步</strong>
            <p>{{ suggestion }}</p>
          </div>
          <RouterLink :to="suggestionLink">{{ suggestionLabel }} <Icon name="arrow-right" :size="13" /></RouterLink>
        </div>

        <p class="pulse-record">
          <Icon name="lock" :size="12" />记录保存在「我的轨迹」里，只有你能看到
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.pulse {
  width: 100%;
  padding: 20px 22px 18px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
}

.pulse.is-compact {
  padding: 16px;
}

.pulse-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.pulse-kicker {
  display: block;
  color: var(--brand-600);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.pulse-head h2 {
  margin: 6px 0 0;
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 700;
  color: var(--ink-900);
}

.pulse.is-compact .pulse-head h2 {
  font-size: 18px;
}

.pulse-live {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 11.5px;
  font-weight: 700;
  white-space: nowrap;
}

.pulse-live i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-500);
  box-shadow: 0 0 0 4px rgb(95 138 115 / 12%);
}

.pulse-sub {
  margin: 8px 0 0;
  color: var(--ink-600);
  font-size: 12.5px;
  line-height: 1.65;
}

.pulse-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.8fr);
  gap: 18px 26px;
  align-items: start;
  margin-top: 14px;
}

.pulse-main,
.pulse-side {
  min-width: 0;
}

.pulse-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pulse.is-compact .pulse-grid {
  grid-template-columns: 1fr;
  gap: 12px;
}

.pulse-record {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  color: var(--ink-500);
  font-size: 11.5px;
  line-height: 1.6;
}

.pulse-sub a {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--brand-700);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.pulse-sub a:hover {
  text-decoration: underline;
}

.pulse-visual {
  margin-top: 0;
  padding: 10px 2px 0;
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fbf9, #fff);
}

.pulse-visual svg {
  display: block;
  width: 100%;
  height: auto;
}

.pulse-axis {
  display: flex;
  justify-content: space-between;
  padding: 2px 18px 8px;
  color: var(--ink-500);
  font-size: 11.5px;
}

.pulse-tags {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 12px;
}

.pulse-tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
}

.pulse-tag-name {
  width: 64px;
  flex: none;
  color: var(--ink-700);
  font-size: 12.5px;
  font-weight: 600;
}

.pulse-tag-bar {
  flex: 1;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--ink-50);
}

.pulse-tag-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.pulse-tag-row em {
  width: 20px;
  flex: none;
  color: var(--ink-500);
  font-size: 11.5px;
  font-style: normal;
  text-align: right;
}

.pulse-change {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--surface-warm);
  color: var(--ink-700);
  font-size: 12.5px;
  line-height: 1.6;
}

.pulse-suggest {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 0;
  padding: 12px;
  border-radius: 8px;
  background: var(--brand-50);
}

.pulse-suggest-icon {
  display: grid;
  width: 30px;
  height: 30px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  background: #fff;
  color: var(--brand-600);
}

.pulse-suggest > div {
  flex: 1;
}

.pulse-suggest strong {
  color: var(--ink-900);
  font-size: 12px;
}

.pulse-suggest p {
  margin: 4px 0 0;
  color: var(--ink-700);
  font-size: 12.5px;
  line-height: 1.7;
}

.pulse-suggest > a {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 3px;
  align-self: center;
  padding: 7px 10px;
  border-radius: var(--radius-button);
  background: var(--brand-600);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.pulse-suggest > a:hover {
  background: var(--brand-700);
}

@media (max-width: 760px) {
  .pulse {
    padding: 16px;
  }

  .pulse-head {
    flex-direction: row;
    align-items: flex-start;
    gap: 10px;
  }

  .pulse-head > div {
    min-width: 0;
  }

  .pulse-live {
    margin-top: 3px;
  }

  .pulse-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .pulse-axis {
    padding-right: 4px;
    padding-left: 4px;
  }

  .pulse-suggest {
    flex-wrap: wrap;
  }

  .pulse-suggest > div {
    flex: 1 1 calc(100% - 40px);
    min-width: 0;
  }

  .pulse-suggest > a {
    flex-basis: 100%;
    justify-content: center;
    width: calc(100% - 40px);
    margin: 8px 0 0 40px;
  }
}
</style>
