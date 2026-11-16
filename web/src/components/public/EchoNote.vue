<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { requestReferral } from '@/services/public'
import { hasCareWords } from '@/services/traceParser'
import { useTraceStore } from '@/stores/trace'

/**
 * 活动页里的「留一句话」。
 *
 * 两件事：
 * 1. 参与者在活动里/活动后可以匿名写一句，只存在这台设备上，不上传。
 * 2. 写完之后给一张温和的回执卡：一句安慰 + 求助方向 + 电话；要不要让人联系，由他自己点。
 *
 * 边界：不做心理诊断，不扫描上传，不自动通知任何人。只有他自己点「想找人聊聊」才会提交联系意愿。
 */

const props = defineProps<{ slug: string }>()

const toast = useToast()
const traceStore = useTraceStore()

type Note = { id: string; text: string; mood: string; at: number }

const MOODS = [
  { id: 'tired', label: '有点累' },
  { id: 'ok', label: '还行' },
  { id: 'talk', label: '想说话' },
  { id: 'quiet', label: '想安静' },
]

const moods = MOODS
const text = ref('')
const mood = ref('ok')
const notes = ref<Note[]>([])
const writing = ref(false)
const showAll = ref(false)
const card = ref<'none' | 'soft' | 'care'>('none')
const saveToTrace = ref(false)
const savedToTrace = ref(false)
const referralOpen = ref(false)
const contact = ref('')
const when = ref('')
const busy = ref(false)

const storageKey = computed(() => `diy-campus:echo:${props.slug}`)

// 只认明确的自伤表达，不做关键词泛扫：宁可漏，不可误伤。
function loadNotes(): void {
  try {
    const raw = localStorage.getItem(storageKey.value)
    if (!raw) return
    const parsed = JSON.parse(raw) as Note[]
    if (Array.isArray(parsed)) notes.value = parsed.slice(-30)
  } catch {
    notes.value = []
  }
}

function persist(): void {
  try {
    localStorage.setItem(storageKey.value, JSON.stringify(notes.value.slice(-30)))
  } catch {
    /* 存不下也不影响使用 */
  }
}

async function send(): Promise<void> {
  const value = text.value.trim()
  if (!value) {
    toast.pushToast('写一句再发吧，几个字也可以', 'warn')
    return
  }
  const care = hasCareWords(value)
  const moodLabel = moods.find((item) => item.id === mood.value)?.label ?? '此刻'
  notes.value.push({ id: `n_${Date.now()}`, text: value.slice(0, 120), mood: mood.value, at: Date.now() })
  persist()
  text.value = ''
  card.value = care ? 'care' : 'soft'
  savedToTrace.value = false
  if (saveToTrace.value) {
    try {
      await traceStore.absorbNote(props.slug, 'mental', { text: value.slice(0, 120), mood: moodLabel })
      savedToTrace.value = true
      toast.pushToast('这句话已存进你的轨迹，其他人看不到', 'success')
    } catch {
      toast.pushToast('这句还留在这台设备上，轨迹暂时没存上', 'warn')
    }
  }
}

/** Ctrl / Cmd + Enter 直接发送，手机上还是点按钮 */
function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
    event.preventDefault()
    send()
  }
}

async function submitReferral(): Promise<void> {
  if (busy.value) return
  busy.value = true
  try {
    const result = await requestReferral(props.slug, {
      wantsContact: true,
      contact: contact.value.trim() || undefined,
      preferredTime: when.value.trim() || undefined,
    })
    toast.pushToast(result.message, 'success')
    referralOpen.value = false
    card.value = 'none'
  } catch {
    toast.pushToast('没能提交，可以直接拨打上面的电话', 'warn')
  } finally {
    busy.value = false
  }
}

function timeLabel(at: number): string {
  const d = new Date(at)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日 ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const visibleNotes = computed(() => (showAll.value ? notes.value : notes.value.slice(-2)))

loadNotes()
</script>

<template>
  <section id="echo-note" class="echo">
    <header class="echo-head">
      <div>
        <span class="echo-kicker">留一句话</span>
        <h3>此刻你想说点什么？</h3>
        <p>
          不用写完整，几个字也行。默认只留在这台设备上；勾选「存进我的轨迹」时，会存进你自己的档案，不会公开，也不会给组织者看。
        </p>
      </div>
      <span v-if="notes.length" class="echo-count">写过 {{ notes.length }} 条</span>
    </header>

    <div class="echo-moods" role="group" aria-label="现在的状态">
      <button
        v-for="item in moods"
        :key="item.id"
        type="button"
        :class="{ 'is-on': mood === item.id }"
        @click="mood = item.id"
      >
        {{ item.label }}
      </button>
    </div>

    <textarea
      v-model="text"
      class="echo-input"
      maxlength="120"
      rows="3"
      placeholder="比如：今天说出来了，好像轻一点点。"
      @focus="writing = true"
      @keydown="onKeydown"
    />
    <div class="echo-save-row">
      <label class="echo-save">
        <input v-model="saveToTrace" type="checkbox" />
        <span>存进我的轨迹（只有你自己能看）</span>
      </label>
      <small>不勾选时，这句话只留在这台设备上。</small>
    </div>
    <div class="echo-actions">
      <span class="echo-hint">{{ text.length }}/120</span>
      <button type="button" class="echo-send" @click="send"><Icon name="send" :size="14" />留在这</button>
    </div>

    <div v-if="notes.length" class="echo-list">
      <button v-if="notes.length > 2" type="button" class="echo-more" @click="showAll = !showAll">
        {{ showAll ? '收起' : `看之前写的 ${notes.length - 2} 条` }}
      </button>
      <article v-for="note in visibleNotes" :key="note.id" class="echo-note">
        <p>{{ note.text }}</p>
        <span>{{ moods.find((m) => m.id === note.mood)?.label }} · {{ timeLabel(note.at) }}</span>
      </article>
    </div>

    <div v-if="card !== 'none'" class="echo-card" :class="`is-${card}`">
      <button type="button" class="echo-close" aria-label="关闭" @click="card = 'none'">×</button>

      <template v-if="card === 'soft'">
        <strong>谢谢你说出来。</strong>
        <p>不管写得长还是短，能写下来就已经在照顾自己了。如果这会儿还想有人陪着说几句，下面这些都能用。</p>
      </template>
      <template v-else>
        <strong>你写下的这些，值得有人认真听。</strong>
        <p>我不是人，没法判断你的情况，也不会把这句话告诉任何人。但你可以现在就找一个真的人聊聊，不用等到准备好。</p>
      </template>

      <p v-if="savedToTrace" class="echo-saved"><Icon name="check-circle" :size="13" />已存进你的轨迹，只有你能看到</p>

      <ul class="echo-res">
        <li>
          <span>校园心理中心</span>
          <RouterLink to="/resources">看校内咨询与预约方式</RouterLink>
        </li>
        <li>
          <span>24 小时心理援助热线</span>
          <a href="tel:12356">12356</a>
        </li>
        <li>
          <span>如果有立即危险</span>
          <span><a href="tel:120">120</a> / <a href="tel:110">110</a></span>
        </li>
      </ul>

      <div class="echo-card-actions">
        <button type="button" class="echo-primary" @click="referralOpen = !referralOpen">
          <Icon name="users" :size="14" />我想找人聊聊
        </button>
        <button type="button" class="echo-ghost" @click="card = 'none'">先自己待一会儿</button>
      </div>

      <div v-if="referralOpen" class="echo-referral">
        <p>留一个联系方式，社团的负责同学会在 24 小时内找你，只说这一件事。</p>
        <input v-model="contact" maxlength="40" placeholder="怎么联系你（选填）" />
        <input v-model="when" maxlength="60" placeholder="方便的时间（选填）" />
        <button type="button" :disabled="busy" @click="submitReferral">
          {{ busy ? '提交中…' : '提交联系意愿' }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.echo {
  width: min(760px, calc(100% - 32px));
  margin: 14px auto 0;
  padding: 20px 22px 22px;
  border: 1px solid #dce8df;
  border-radius: 10px;
  background: rgb(255 255 255 / 88%);
}

.echo-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.echo-kicker {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
  color: var(--brand-600);
}

.echo-head h3 {
  margin: 6px 0 6px;
  font-size: 18px;
  font-weight: 700;
  color: var(--ink-900);
}

.echo-head p {
  max-width: 460px;
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-600);
}

.echo-count {
  flex: none;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 11.5px;
  font-weight: 600;
}

.echo-moods {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0 12px;
}

.echo-moods button {
  padding: 6px 12px;
  border: 1px solid #dbe5dd;
  border-radius: 999px;
  background: #fff;
  color: var(--ink-600);
  font-size: 12.5px;
  cursor: pointer;
  transition: 0.15s ease;
}

.echo-moods button:hover {
  border-color: var(--brand-300);
}

.echo-moods button.is-on {
  border-color: var(--brand-600);
  background: var(--brand-600);
  color: #fff;
  font-weight: 600;
}

.echo-input {
  width: 100%;
  min-height: 76px;
  padding: 12px 14px;
  border: 1px solid #dbe5dd;
  border-radius: 8px;
  background: #fdfcfa;
  font: inherit;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--ink-800);
  resize: vertical;
}

.echo-input:focus {
  outline: none;
  border-color: var(--brand-400);
  box-shadow: 0 0 0 3px rgb(45 80 70 / 8%);
}

.echo-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.echo-save-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 12px;
  margin-top: 9px;
}

.echo-save {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-700);
  font-size: 12px;
  cursor: pointer;
}

.echo-save input {
  accent-color: var(--brand-600);
}

.echo-save-row small {
  color: var(--ink-500);
  font-size: 11.5px;
}

.echo-saved {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 10px 0 0;
  padding: 7px 10px;
  border-radius: 6px;
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 12px;
  font-weight: 600;
}

.echo-hint {
  font-size: 11.5px;
  color: var(--ink-500);
}

.echo-send {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border-radius: 10px;
  background: var(--brand-600);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.echo-send:hover {
  background: var(--brand-700);
}

.echo-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.echo-more {
  align-self: flex-start;
  padding: 0;
  border: 0;
  background: none;
  color: var(--brand-600);
  font-size: 12px;
  text-decoration: underline;
  cursor: pointer;
}

.echo-note {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8f6f1;
}

.echo-note p {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--ink-800);
  white-space: pre-wrap;
}

.echo-note span {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--ink-500);
}

.echo-card {
  position: relative;
  margin-top: 16px;
  padding: 18px 20px 20px;
  border-radius: 8px;
  border: 1px solid #cfe0d4;
  background: #f6faf7;
}

.echo-card.is-care {
  border-color: #e4d3bd;
  background: #fbf6ee;
}

.echo-card > strong {
  display: block;
  font-size: 15px;
  color: var(--ink-900);
}

.echo-card > p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-600);
}

.echo-close {
  position: absolute;
  top: 10px;
  right: 12px;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink-400);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.echo-close:hover {
  background: rgb(0 0 0 / 4%);
  color: var(--ink-700);
}

.echo-res {
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.echo-res li {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border-radius: 10px;
  background: rgb(255 255 255 / 70%);
  border: 1px solid #e6efe8;
  font-size: 12.5px;
}

.echo-res span {
  color: var(--ink-500);
}

.echo-res a {
  color: var(--brand-700);
  font-weight: 700;
  text-decoration: none;
}

.echo-res a:hover {
  text-decoration: underline;
}

.echo-card-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.echo-primary,
.echo-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  border-radius: 10px;
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
}

.echo-primary {
  border: 0;
  background: var(--brand-600);
  color: #fff;
}

.echo-primary:hover {
  background: var(--brand-700);
}

.echo-ghost {
  border: 1px solid #dbe5dd;
  background: #fff;
  color: var(--ink-600);
}

.echo-ghost:hover {
  border-color: var(--brand-300);
}

.echo-referral {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed #dbe5dd;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.echo-referral p {
  margin: 0;
  font-size: 12px;
  color: var(--ink-500);
  line-height: 1.7;
}

.echo-referral input {
  padding: 9px 12px;
  border: 1px solid #dbe5dd;
  border-radius: 10px;
  background: #fff;
  font: inherit;
  font-size: 13px;
}

.echo-referral button {
  align-self: flex-start;
  padding: 9px 16px;
  border: 0;
  border-radius: 10px;
  background: var(--brand-600);
  color: #fff;
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
}

.echo-referral button:disabled {
  opacity: 0.6;
  cursor: wait;
}

@media (max-width: 560px) {
  .echo {
    padding: 16px;
  }
  .echo-card-actions {
    flex-direction: column;
  }
  .echo-primary,
  .echo-ghost {
    justify-content: center;
  }
}
</style>
