<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { hasCareWords } from '@/services/traceParser'
import { useTraceStore } from '@/stores/trace'
import type { ActivityDomain, ParticipantTrace } from '@/types/trace'

/**
 * 动态参与面板。
 *
 * 与固定问卷的区别：没有「下一步」和固定题序。
 * 参与者每写一句，规则引擎就重算标签与会话上下文，
 * 提示、可走的路径、记录都在原地变化；快捷入口只是降低门槛。
 *
 * 边界：不做诊断、不替用户下结论；内容只进个人轨迹，公开墙另走 EchoNote。
 */

const props = withDefaults(
  defineProps<{
    slug: string
    activityTitle?: string
    domain?: ActivityDomain
    contentUnavailable?: boolean
  }>(),
  { domain: 'mental', contentUnavailable: false }
)

const traceStore = useTraceStore()
const toast = useToast()

const activityId = computed(() => props.slug)
const trace = computed<ParticipantTrace | undefined>(() => traceStore.get(activityId.value))
const status = computed(() => traceStore.status[activityId.value] ?? 'idle')
const hint = computed(
  () => trace.value?.sessionContext.currentHint || '不用从头填到尾。你现在最想解决哪一件，就先说哪一件。'
)
const tags = computed(() => trace.value?.tags ?? [])
const summary = computed(() => trace.value?.summary ?? '')

const input = ref('')
const busy = ref(false)
const openPath = ref('')
const careVisible = ref(false)
const previous = ref<ParticipantTrace | null>(null)
const editingSummary = ref(false)
const summaryDraft = ref('')
const familyDraft = ref('')
const familySaved = ref(false)
const done = ref<Record<string, boolean>>({})

interface PathCard {
  id: string
  title: string
  desc: string
  kind: 'breathe' | 'note' | 'list' | 'company' | 'family' | 'evidence' | 'help' | 'map' | 'story'
  items?: string[]
}

const QUICK_ENTRIES = [
  { id: 'relax', label: '我想放松', text: '我现在有点累，想先放松一下。' },
  { id: 'express', label: '我想表达', text: '我想说点什么，但还没想好怎么开口。' },
  { id: 'meet', label: '我想认识人', text: '我想认识几个人，但不太会开始。' },
  { id: 'quiet', label: '想安静待着', text: '我想安静待一会儿，不想被问太多。' },
]

const PATH_CATALOG: Record<string, PathCard> = {
  breathe: {
    id: 'breathe',
    title: '两分钟呼吸',
    desc: '4 秒吸气、2 秒停、6 秒呼气，先做四轮。',
    kind: 'breathe',
  },
  sleep: {
    id: 'sleep',
    title: '今晚只改一件事',
    desc: '不要求早睡，只把手机放到够不着的地方。',
    kind: 'list',
    items: ['睡前 30 分钟把手机放到床以外', '关掉一个会响的群通知', '躺下后只做 4 轮慢呼气'],
  },
  quiet: {
    id: 'quiet',
    title: '十分钟安静',
    desc: '不解决问题，先让脑子停一会儿。',
    kind: 'list',
    items: ['坐到一个不用回应任何人的地方', '把注意力放在脚底和椅子的接触上', '如果有人问，就说「我想缓十分钟」'],
  },
  unwind: {
    id: 'unwind',
    title: '低电量休息',
    desc: '三件不费力的事，选一件就够。',
    kind: 'list',
    items: ['洗把脸，开窗站两分钟', '把今天最烦的一件事写在一张纸上，折起来', '听一首完整的歌，什么都不做'],
  },
  note: {
    id: 'note',
    title: '留一句话',
    desc: '写下来，只给你自己看；也可以选择不留。',
    kind: 'note',
  },
  company: {
    id: 'company',
    title: '先迈半步',
    desc: '不用认识很多人，从一个轻问题开始。',
    kind: 'company',
  },
  family: {
    id: 'family',
    title: '写给家里的一段话',
    desc: '不会发送。先让你把想说的说完。',
    kind: 'family',
  },
  evidence: {
    id: 'evidence',
    title: '找一件反例',
    desc: '刚才那句话可能太绝对了，我们找个反例。',
    kind: 'evidence',
  },
  'tiny-step': {
    id: 'tiny-step',
    title: '这周的一小步',
    desc: '选一件 10 分钟内能做完的事。',
    kind: 'list',
    items: [
      '给一个人发一条消息，只问一句「你最近怎么样」',
      '把拖了两周的小事做 10 分钟就停',
      '去一次社团活动，待 15 分钟就可以走',
    ],
  },
  'ask-help': {
    id: 'ask-help',
    title: '现在就能用的求助',
    desc: '不用等到准备好，也不用先解释清楚。',
    kind: 'help',
  },
  map: {
    id: 'map',
    title: '方向对照',
    desc: '把两条路并排写，先不选。',
    kind: 'map',
    items: ['这条路每天在做什么？', '我最怕它哪一点？', '我现在能验证它的最小动作是什么？'],
  },
  story: {
    id: 'story',
    title: '别人的真实路径',
    desc: '不是成功故事，是具体的人怎么走。',
    kind: 'story',
    items: ['他一开始也不知道自己要做什么', '他先用一件小事试了两周', '他改过一次方向，并不意味着前面白走'],
  },
  'tiny-test': {
    id: 'tiny-test',
    title: '这周验证一件小事',
    desc: '用行动代替想象，两周后再看。',
    kind: 'list',
    items: [
      '找一条这个方向的真实招聘要求，标出自己缺的两项',
      '约一个在做这行的人聊 15 分钟',
      '做一个小作品，只给自己看',
    ],
  },
}

const DEFAULT_PATHS: Record<ActivityDomain, string[]> = {
  mental: ['breathe', 'note', 'quiet'],
  career: ['map', 'story', 'tiny-test'],
}

const activePaths = computed<PathCard[]>(() => {
  const ids = trace.value?.sessionContext.availablePaths?.length
    ? trace.value.sessionContext.availablePaths
    : DEFAULT_PATHS[props.domain]
  return ids
    .map((id) => PATH_CATALOG[id])
    .filter((item): item is PathCard => Boolean(item))
    .slice(0, 4)
})

const statusText = computed(() => {
  switch (status.value) {
    case 'synced':
      return '已同步到你的账号'
    case 'syncing':
      return '正在同步…'
    case 'offline':
      return '只保存在这台设备'
    default:
      return '只保存在这台设备 · 登录后可跨设备'
  }
})

const breathRemaining = ref(120)
const breathing = ref(false)
let breathTimer: number | undefined

const breathPhase = computed(() => {
  if (!breathing.value) return '准备好就开始'
  const elapsed = 120 - breathRemaining.value
  const cycle = elapsed % 12
  if (cycle < 4) return '吸气'
  if (cycle < 6) return '停一下'
  return '呼气'
})

function stopBreath(): void {
  if (breathTimer) window.clearInterval(breathTimer)
  breathTimer = undefined
  breathing.value = false
}

function startBreath(): void {
  stopBreath()
  breathRemaining.value = 120
  breathing.value = true
  breathTimer = window.setInterval(() => {
    breathRemaining.value -= 1
    if (breathRemaining.value <= 0) {
      stopBreath()
      toast.pushToast('两分钟到了，感觉一下肩膀有没有松一点。', 'success')
    }
  }, 1000)
}

function formatRemaining(): string {
  const minutes = Math.floor(breathRemaining.value / 60)
  const seconds = String(breathRemaining.value % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

function toggleDone(key: string): void {
  done.value = { ...done.value, [key]: !done.value[key] }
}

function doneCount(path: PathCard): number {
  return (path.items ?? []).filter((_, index) => done.value[`${path.id}-${index}`]).length
}

function scrollToNote(): void {
  document.getElementById('echo-note')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function resetCare(): void {
  careVisible.value = false
}

async function submitText(raw?: string): Promise<void> {
  const value = (raw ?? input.value).trim()
  if (!value) {
    toast.pushToast('写一句再继续吧，几个字也可以', 'warn')
    return
  }
  busy.value = true
  try {
    if (hasCareWords(value)) careVisible.value = true
    const next = await traceStore.applyText(activityId.value, props.domain, value)
    if (raw === undefined) input.value = ''
    await nextTick()
    if (!openPath.value) openPath.value = next.sessionContext.availablePaths[0] ?? ''
    if (!previous.value) previous.value = traceStore.previousTrace(activityId.value)
  } catch {
    toast.pushToast('这句没能记下，请稍后再试', 'warn')
  } finally {
    busy.value = false
  }
}

function onInputKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
    event.preventDefault()
    void submitText()
  }
}

function saveSummary(): void {
  traceStore.updateSummary(activityId.value, summaryDraft.value)
  editingSummary.value = false
  toast.pushToast('这句话已经改成你的说法了', 'success')
}

function saveFamilyDraft(): void {
  if (!familyDraft.value.trim()) return
  void traceStore
    .absorbNote(activityId.value, props.domain, { text: familyDraft.value.trim(), mood: 'family' })
    .then(() => {
      familySaved.value = true
      toast.pushToast('已经存进你的轨迹，没有发送给任何人', 'success')
    })
}

function continuePrevious(kept: boolean): void {
  void submitText(
    kept ? `上次留下的那句话还在，我想接着往下看看。` : `其实我改主意了，上次说的那个方向我现在想重新看看。`
  )
}

watch(
  () => trace.value?.summary,
  (value) => {
    if (!editingSummary.value) summaryDraft.value = value ?? ''
  }
)

onMounted(async () => {
  await traceStore.load(activityId.value, props.domain)
  summaryDraft.value = trace.value?.summary ?? ''
  previous.value = traceStore.previousTrace(activityId.value)
})

onBeforeUnmount(() => {
  stopBreath()
})
</script>

<template>
  <section class="dyn">
    <header class="dyn-head">
      <div>
        <span class="dyn-kicker">动态参与</span>
        <h3>想到哪，说到哪</h3>
        <p>没有必须填完的题。你写一句，下面的面板就重算一次；改主意了也不用撤回，直接接着说。</p>
      </div>
      <span class="dyn-status" :class="`is-${status}`"> <Icon name="cloud" :size="13" />{{ statusText }} </span>
    </header>

    <p v-if="contentUnavailable" class="dyn-offline">
      活动内容暂时没加载出来。下面的参与面板不依赖活动页面，仍然可以用。
    </p>

    <div v-if="previous" class="dyn-previous">
      <span class="dyn-previous-label">上次你留下过</span>
      <p>{{ previous.summary || previous.tags.map((tag) => tag.content).join('、') }}</p>
      <div class="dyn-previous-actions">
        <button type="button" @click="continuePrevious(true)">还在，接着看</button>
        <button type="button" class="is-ghost" @click="continuePrevious(false)">变了，重新看看</button>
      </div>
    </div>

    <div v-if="careVisible" class="dyn-care">
      <button type="button" class="dyn-care-close" aria-label="收起" @click="resetCare">×</button>
      <strong>你写下的这些，值得有人认真听。</strong>
      <p>这里不判断你的情况，也不会通知任何人。你可以先不继续写，直接找一个真的人聊聊。</p>
      <div class="dyn-care-links">
        <a href="tel:12356">12356 心理援助热线</a>
        <a href="tel:120">120</a>
        <a href="tel:110">110</a>
        <RouterLink to="/resources">校内心理资源</RouterLink>
      </div>
    </div>

    <div class="dyn-quick" role="group" aria-label="快捷入口">
      <button
        v-for="(entry, index) in QUICK_ENTRIES"
        :key="entry.id"
        type="button"
        :disabled="busy"
        @click="submitText(entry.text)"
      >
        <span>{{ String(index + 1).padStart(2, '0') }}</span
        >{{ entry.label }}
      </button>
    </div>

    <div v-if="tags.length" class="dyn-tags">
      <span class="dyn-tags-label">你现在在</span>
      <span v-for="tag in tags" :key="tag.content" class="dyn-tag" :class="`is-${tag.source}`">
        {{ tag.content }}
        <button
          type="button"
          :aria-label="`移除 ${tag.content}`"
          @click="traceStore.removeTag(activityId, tag.content)"
        >
          ×
        </button>
      </span>
    </div>

    <div class="dyn-hint">
      <span class="dyn-hint-label">面板在听</span>
      <p>{{ hint }}</p>
      <small v-if="trace?.change">{{ trace.change }}</small>
    </div>

    <div class="dyn-input">
      <textarea
        v-model="input"
        maxlength="240"
        rows="3"
        placeholder="比如：期末快到了，天天失眠，但又不敢跟家里说。"
        @keydown="onInputKeydown"
      />
      <div class="dyn-input-foot">
        <span>{{ input.length }}/240 · 只写给你自己看</span>
        <button type="button" :disabled="busy" @click="submitText()">
          <Icon name="send" :size="14" />{{ busy ? '正在重算…' : '继续说' }}
        </button>
      </div>
    </div>

    <div class="dyn-paths">
      <div class="dyn-paths-head">
        <h4>下一步可以做</h4>
        <span>随你刚才的话变化 · {{ activePaths.length }} 个</span>
      </div>

      <div class="dyn-path-grid">
        <article
          v-for="(path, index) in activePaths"
          :key="path.id"
          class="dyn-path"
          :class="{ 'is-open': openPath === path.id }"
        >
          <button
            type="button"
            class="dyn-path-head"
            :aria-expanded="openPath === path.id"
            @click="openPath = openPath === path.id ? '' : path.id"
          >
            <span class="dyn-path-num">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="dyn-path-title">
              {{ path.title }}
              <small>{{ path.desc }}</small>
            </span>
            <Icon :name="openPath === path.id ? 'chevron' : 'arrow-right'" :size="15" />
          </button>

          <div v-if="openPath === path.id" class="dyn-path-body">
            <template v-if="path.kind === 'breathe'">
              <div class="dyn-breath" :class="{ 'is-on': breathing }">
                <span class="dyn-breath-ring"><i /></span>
                <strong>{{ breathPhase }}</strong>
                <small>{{ formatRemaining() }}</small>
              </div>
              <div class="dyn-actions">
                <button v-if="!breathing" type="button" @click="startBreath">
                  <Icon name="play" :size="13" />开始两分钟
                </button>
                <button v-else type="button" class="is-ghost" @click="stopBreath">先停一下</button>
              </div>
            </template>

            <template v-else-if="path.kind === 'list'">
              <ul class="dyn-checklist">
                <li v-for="(item, itemIndex) in path.items" :key="item">
                  <button
                    type="button"
                    :class="{ 'is-done': done[`${path.id}-${itemIndex}`] }"
                    @click="toggleDone(`${path.id}-${itemIndex}`)"
                  >
                    <span class="dyn-box"><Icon v-if="done[`${path.id}-${itemIndex}`]" name="check" :size="11" /></span>
                    <span>{{ item }}</span>
                  </button>
                </li>
              </ul>
              <small class="dyn-body-foot">做到 {{ doneCount(path) }}/{{ path.items?.length ?? 0 }} 件就算完成。</small>
            </template>

            <template v-else-if="path.kind === 'note'">
              <p class="dyn-body-lead">不用写完整，几个字也行。写下来的这句话只留在这台设备。</p>
              <button type="button" class="dyn-body-cta" @click="scrollToNote">
                去下面写一句 <Icon name="arrow-right" :size="13" />
              </button>
            </template>

            <template v-else-if="path.kind === 'company'">
              <p class="dyn-body-lead">挑一句最不费力的，发给一个你觉得安全的人：</p>
              <ul class="dyn-lines">
                <li>「你最近怎么样？我这两天有点闷。」</li>
                <li>「不用回很多，看到就行。」</li>
                <li>「下周有空一起吃饭吗？我想出去走走。」</li>
              </ul>
            </template>

            <template v-else-if="path.kind === 'family'">
              <p class="dyn-body-lead">写在这里不会发送给任何人，只是让你先说完。</p>
              <textarea
                v-model="familyDraft"
                maxlength="200"
                rows="3"
                placeholder="比如：我知道你们是为我好，但你们越说我越不敢选。"
              />
              <div class="dyn-actions">
                <button type="button" :disabled="!familyDraft.trim() || familySaved" @click="saveFamilyDraft">
                  {{ familySaved ? '已存进你的轨迹' : '存进我的轨迹' }}
                </button>
              </div>
            </template>

            <template v-else-if="path.kind === 'evidence'">
              <p class="dyn-body-lead">不用反驳自己，只回答三个具体的问题：</p>
              <ul class="dyn-lines">
                <li>有没有一次，事情没有你想的那么糟？</li>
                <li>那一次你做了什么，和平时不一样？</li>
                <li>如果朋友这样说自己，你会怎么回他？</li>
              </ul>
            </template>

            <template v-else-if="path.kind === 'help'">
              <ul class="dyn-lines">
                <li><a href="tel:12356">12356</a> · 24 小时心理援助热线</li>
                <li><a href="tel:120">120</a> / <a href="tel:110">110</a> · 有立即危险时</li>
                <li><RouterLink to="/resources">校内心理中心与预约方式</RouterLink></li>
              </ul>
              <button type="button" class="dyn-body-cta" @click="scrollToNote">
                也可以先留一句话 <Icon name="arrow-right" :size="13" />
              </button>
            </template>

            <template v-else>
              <p class="dyn-body-lead">{{ path.desc }}</p>
              <ul class="dyn-lines">
                <li v-for="item in path.items" :key="item">{{ item }}</li>
              </ul>
            </template>
          </div>
        </article>
      </div>
    </div>

    <div v-if="tags.length || summary" class="dyn-summary">
      <div class="dyn-summary-head">
        <h4>这次留下的</h4>
        <button v-if="!editingSummary" type="button" @click="editingSummary = true">改一句</button>
        <button v-else type="button" @click="editingSummary = false">取消</button>
      </div>

      <template v-if="!editingSummary">
        <p>{{ summary || '还没形成一句话，可以再写一句。' }}</p>
        <small v-if="trace?.openQuestion" class="dyn-open-question">下次想问你：{{ trace.openQuestion }}</small>
      </template>
      <template v-else>
        <textarea v-model="summaryDraft" maxlength="60" rows="2" />
        <div class="dyn-actions">
          <button type="button" @click="saveSummary">保存这句</button>
          <span class="dyn-actions-hint">{{ summaryDraft.length }}/60</span>
        </div>
      </template>

      <div class="dyn-foot">
        <Icon name="lock" :size="12" />
        <span>只存在你自己的轨迹里 · {{ statusText }} · 不做心理诊断</span>
        <RouterLink class="dyn-foot-link" to="/trace">看我的轨迹</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.dyn {
  width: min(760px, calc(100% - 32px));
  margin: 14px auto 0;
  padding: 22px;
  border: 1px solid #dce8df;
  border-radius: 10px;
  background: rgb(255 255 255 / 90%);
}

.dyn-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.dyn-kicker {
  display: block;
  font-size: 11.5px;
  font-weight: 800;
  letter-spacing: 0.16em;
  color: var(--brand-600);
}

.dyn-head h3 {
  margin: 7px 0 7px;
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 700;
  color: var(--ink-900);
}

.dyn-head p {
  max-width: 480px;
  margin: 0;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--ink-600);
}

.dyn-status {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 5px;
  padding: 5px 9px;
  border-radius: 999px;
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
}

.dyn-status.is-offline {
  background: var(--ink-50);
  color: var(--ink-600);
}

.dyn-offline {
  margin: 14px 0 0;
  padding: 9px 12px;
  border-radius: 6px;
  background: var(--surface-warm);
  color: var(--ink-700);
  font-size: 12px;
  line-height: 1.65;
}

.dyn-previous {
  margin-top: 16px;
  padding: 13px 15px;
  border-left: 3px solid var(--brand-400);
  border-radius: 6px;
  background: var(--brand-50);
}

.dyn-previous-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: var(--brand-700);
}

.dyn-previous p {
  margin: 6px 0 10px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--ink-800);
}

.dyn-previous-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dyn-previous-actions button {
  padding: 6px 12px;
  border: 0;
  border-radius: var(--radius-button);
  background: var(--brand-600);
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.dyn-previous-actions button.is-ghost {
  border: 1px solid var(--brand-300);
  background: transparent;
  color: var(--brand-700);
}

.dyn-care {
  position: relative;
  margin-top: 16px;
  padding: 15px 17px 16px;
  border: 1px solid #e3d2bb;
  border-radius: 8px;
  background: #fbf6ee;
}

.dyn-care strong {
  display: block;
  padding-right: 24px;
  font-size: 14.5px;
  color: var(--ink-900);
}

.dyn-care p {
  margin: 7px 0 10px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-700);
}

.dyn-care-links {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12.5px;
  font-weight: 700;
}

.dyn-care-links a {
  color: var(--brand-700);
}

.dyn-care-close {
  position: absolute;
  top: 8px;
  right: 9px;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink-500);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.dyn-quick {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 18px 0 14px;
}

.dyn-quick button {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid #dbe5dd;
  border-radius: 999px;
  background: #fff;
  color: var(--ink-700);
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    color 0.15s ease;
}

.dyn-quick button span {
  color: var(--brand-400);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.dyn-quick button:hover {
  border-color: var(--brand-400);
  color: var(--brand-700);
}

.dyn-quick button:disabled {
  opacity: 0.55;
  cursor: wait;
}

.dyn-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  margin-bottom: 12px;
}

.dyn-tags-label {
  margin-right: 2px;
  color: var(--ink-500);
  font-size: 12px;
}

.dyn-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 4px 7px 4px 10px;
  border-radius: 999px;
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 12px;
  font-weight: 600;
}

.dyn-tag.is-user {
  background: var(--ink-50);
  color: var(--ink-700);
}

.dyn-tag button {
  width: 17px;
  height: 17px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: currentcolor;
  font-size: 13px;
  line-height: 1;
  opacity: 0.55;
  cursor: pointer;
}

.dyn-tag button:hover {
  opacity: 1;
  background: rgb(0 0 0 / 6%);
}

.dyn-hint {
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--surface-mist);
}

.dyn-hint-label {
  display: block;
  margin-bottom: 5px;
  color: var(--brand-700);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.dyn-hint p {
  margin: 0;
  font-size: 15px;
  line-height: 1.65;
  color: var(--ink-900);
}

.dyn-hint small {
  display: block;
  margin-top: 7px;
  color: var(--ink-600);
  font-size: 12px;
}

.dyn-input {
  margin-top: 14px;
  padding: 10px 12px 10px;
  border: 1px solid #dbe5dd;
  border-radius: 8px;
  background: #fdfcfa;
}

.dyn-input:focus-within {
  border-color: var(--brand-400);
  box-shadow: 0 0 0 3px rgb(45 80 70 / 8%);
}

.dyn-input textarea {
  width: 100%;
  min-height: 76px;
  padding: 4px 2px;
  border: 0;
  outline: 0;
  background: transparent;
  font: inherit;
  font-size: 14px;
  line-height: 1.75;
  color: var(--ink-800);
  resize: vertical;
}

.dyn-input-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 6px;
}

.dyn-input-foot span {
  color: var(--ink-500);
  font-size: 11.5px;
}

.dyn-input-foot button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 0;
  border-radius: var(--radius-button);
  background: var(--brand-600);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.dyn-input-foot button:hover {
  background: var(--brand-700);
}

.dyn-input-foot button:disabled {
  opacity: 0.55;
  cursor: wait;
}

.dyn-paths {
  margin-top: 20px;
}

.dyn-paths-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.dyn-paths-head h4,
.dyn-summary-head h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: var(--ink-900);
}

.dyn-paths-head span {
  color: var(--ink-500);
  font-size: 11.5px;
}

.dyn-path-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 9px;
  margin-top: 10px;
}

.dyn-path {
  border: 1px solid #dbe5dd;
  border-radius: 8px;
  background: #fff;
}

.dyn-path.is-open {
  border-color: var(--brand-300);
  background: #fbfdfb;
}

.dyn-path-head {
  display: flex;
  width: 100%;
  align-items: flex-start;
  gap: 9px;
  padding: 12px 12px 11px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.dyn-path-num {
  flex: none;
  padding-top: 2px;
  color: var(--brand-400);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
}

.dyn-path-title {
  flex: 1;
  font-size: 13.5px;
  font-weight: 700;
  line-height: 1.45;
  color: var(--ink-900);
}

.dyn-path-title small {
  display: block;
  margin-top: 3px;
  color: var(--ink-600);
  font-size: 11.5px;
  font-weight: 400;
  line-height: 1.55;
}

.dyn-path-head > :last-child {
  flex: none;
  margin-top: 2px;
  color: var(--ink-400);
}

.dyn-path-body {
  padding: 0 12px 13px;
  border-top: 1px dashed #e3ece6;
}

.dyn-body-lead {
  margin: 11px 0 8px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-600);
}

.dyn-body-foot {
  display: block;
  margin-top: 8px;
  color: var(--ink-500);
  font-size: 11.5px;
}

.dyn-lines {
  margin: 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dyn-lines li {
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--ink-700);
}

.dyn-lines a {
  color: var(--brand-700);
  font-weight: 700;
}

.dyn-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 9px;
  margin-top: 12px;
}

.dyn-actions button,
.dyn-body-cta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 13px;
  border: 0;
  border-radius: var(--radius-button);
  background: var(--brand-600);
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.dyn-actions button.is-ghost {
  border: 1px solid #dbe5dd;
  background: #fff;
  color: var(--ink-700);
}

.dyn-actions button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.dyn-actions-hint {
  color: var(--ink-500);
  font-size: 11.5px;
}

.dyn-breath {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.dyn-breath-ring {
  position: relative;
  display: grid;
  width: 42px;
  height: 42px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  background: var(--brand-100);
}

.dyn-breath-ring i {
  display: block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--brand-500);
}

.dyn-breath.is-on .dyn-breath-ring {
  animation: dyn-breathe 6s ease-in-out infinite;
}

.dyn-breath strong {
  font-size: 14px;
  color: var(--ink-900);
}

.dyn-breath small {
  margin-left: auto;
  color: var(--ink-600);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

@keyframes dyn-breathe {
  0%,
  100% {
    transform: scale(0.86);
    background: var(--brand-100);
  }
  33% {
    transform: scale(1.16);
    background: var(--brand-200);
  }
  50% {
    transform: scale(1.16);
  }
}

.dyn-checklist {
  margin: 11px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.dyn-checklist button {
  display: flex;
  width: 100%;
  align-items: flex-start;
  gap: 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-700);
  font: inherit;
  font-size: 12.5px;
  line-height: 1.6;
  text-align: left;
  cursor: pointer;
}

.dyn-checklist button.is-done > span:last-child {
  color: var(--ink-500);
  text-decoration: line-through;
}

.dyn-box {
  display: grid;
  width: 15px;
  height: 15px;
  flex: none;
  margin-top: 2px;
  place-items: center;
  border: 1px solid var(--ink-300);
  border-radius: 3px;
  color: #fff;
}

.dyn-checklist button.is-done .dyn-box {
  border-color: var(--brand-600);
  background: var(--brand-600);
}

.dyn-path-body textarea {
  width: 100%;
  margin-top: 4px;
  padding: 9px 11px;
  border: 1px solid #dbe5dd;
  border-radius: 6px;
  background: #fff;
  font: inherit;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--ink-800);
  resize: vertical;
}

.dyn-summary {
  margin-top: 20px;
  padding: 15px 16px 13px;
  border-radius: 8px;
  background: var(--surface-soft);
}

.dyn-summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dyn-summary-head button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--brand-700);
  font-size: 12px;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

.dyn-summary > p {
  margin: 9px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--ink-900);
}

.dyn-open-question {
  display: block;
  margin-top: 7px;
  color: var(--ink-600);
  font-size: 12.5px;
  line-height: 1.6;
}

.dyn-summary textarea {
  width: 100%;
  margin-top: 8px;
  padding: 9px 11px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: #fff;
  font: inherit;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-800);
  resize: vertical;
}

.dyn-foot {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 11px;
  padding-top: 9px;
  border-top: 1px solid var(--line);
  color: var(--ink-500);
  font-size: 11.5px;
}

.dyn-foot span {
  flex: 1;
  line-height: 1.55;
}

.dyn-foot-link {
  flex: none;
  color: var(--brand-700);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.dyn-foot-link:hover {
  text-decoration: underline;
}

@media (max-width: 640px) {
  .dyn {
    padding: 17px 15px;
  }

  .dyn-head {
    flex-direction: column;
    gap: 9px;
  }

  .dyn-quick {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dyn-path-grid {
    grid-template-columns: 1fr;
  }

  .dyn-input-foot {
    align-items: flex-end;
  }
}
</style>
