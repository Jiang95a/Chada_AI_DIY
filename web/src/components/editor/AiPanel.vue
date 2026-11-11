<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useEditorStore } from '@/stores/editor'
import { AgentError, askAgent, probeAgent } from '@/services/agent'
import { describeDocForAgent } from '@/services/planner'
import { localReply, QUICK_PROMPTS, suggestFor } from '@/services/copilot'
import { metaOf } from '@/data/blockCatalog'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'

const emit = defineEmits<{ generate: [] }>()

const store = useEditorStore()
const { doc } = storeToRefs(store)
const toast = useToast()

interface ChatItem {
  id: number
  role: 'assistant' | 'user'
  text: string
  meta?: string
}

let seq = 0
const messages = ref<ChatItem[]>([
  {
    id: ++seq,
    role: 'assistant',
    text: '我是这个工坊的 AI 助手。说一句你要办的活动，我给你排页面；选中某个组件再问我，我给具体改法。',
    meta: '本地规则模式 · 不联网也能用',
  },
])

const status = ref<'unknown' | 'online' | 'offline'>('unknown')
const health = ref<string>('')
const input = ref('')
const sending = ref(false)
const threadId = ref('')
const applied = ref<string[]>([])
const dismissed = ref<string[]>([])

const statusLabel = computed(() => {
  if (status.value === 'online') return health.value || '智能体在线'
  if (status.value === 'offline') return '离线 · 本地规则'
  return '检测中…'
})

const suggestions = computed(() =>
  suggestFor(doc.value, store.currentPage, store.selectedBlock).filter(
    (item) => !applied.value.includes(item.id) && !dismissed.value.includes(item.id)
  )
)

const hasBlock = computed(() => Boolean(store.selectedBlock))

function pushMessage(role: ChatItem['role'], text: string, meta?: string): void {
  messages.value = [...messages.value, { id: ++seq, role, text, meta }]
}

async function refreshStatus(): Promise<void> {
  status.value = 'unknown'
  const result = await probeAgent()
  if (result.ok) {
    status.value = 'online'
    const llm = result.health?.llm as { model?: string; key_configured?: boolean } | undefined
    health.value = llm?.model ? `在线 · ${llm.model}` : '在线'
    if (llm && llm.key_configured === false) {
      health.value = '在线 · 未配置 Key'
    }
  } else {
    status.value = 'offline'
    health.value = ''
  }
}

onMounted(refreshStatus)

function selectedSummary(): string {
  const block = store.selectedBlock
  if (!block) return ''
  const payload = JSON.stringify(block.props).slice(0, 220)
  return `${metaOf(block.type).label} ${payload}`
}

async function send(text?: string): Promise<void> {
  const message = (text ?? input.value).trim()
  if (!message || sending.value) return

  pushMessage('user', message)
  input.value = ''
  sending.value = true

  const pageIndex = Math.max(
    0,
    doc.value.pages.findIndex((page) => page.id === store.currentPageId)
  )
  const context = describeDocForAgent(doc.value, pageIndex, selectedSummary())

  if (status.value === 'online') {
    try {
      const reply = await askAgent(`${context}\n\n我的问题：${message}`, {
        threadId: threadId.value || undefined,
      })
      if (reply.threadId) threadId.value = reply.threadId

      // 把「这轮到底发生了什么」摊开给用户看：带了几条资料、调了什么工具、有没有被护栏拦。
      // 这不是炫技——AI 建议要能解释，用户才敢点「应用」。
      const bits = ['智能体', `${reply.latencyMs}ms`]
      if (reply.ragHits > 0) bits.push(`资料 ${reply.ragHits} 条`)
      if (reply.toolCalls.length) bits.push(reply.toolCalls.map((call) => call.name).join(' / '))
      if (reply.flags.includes('system_extra_blocked')) bits.push('附加要求已拦截')

      pushMessage('assistant', reply.reply || '（这次没拿到回复，再问一次试试）', bits.join(' · '))
    } catch (error) {
      status.value = 'offline'
      const reason = error instanceof AgentError ? error.message : '智能体调用失败'
      pushMessage(
        'assistant',
        localReply(doc.value, store.currentPage, store.selectedBlock, store.issues),
        `已切回本地规则 · ${reason}`
      )
      toast.pushToast(reason, 'warn')
    } finally {
      sending.value = false
    }
    return
  }

  window.setTimeout(() => {
    pushMessage(
      'assistant',
      localReply(doc.value, store.currentPage, store.selectedBlock, store.issues),
      '本地规则 · 未连接智能体'
    )
    sending.value = false
  }, 420)
}

function applySuggestion(id: string): void {
  const target = suggestions.value.find((item) => item.id === id)
  if (!target) return
  store.applySuggestion(target)
  applied.value = [...applied.value, id]
  toast.pushToast('已应用这句建议，可撤销', 'success')
}

function dismissSuggestion(id: string): void {
  dismissed.value = [...dismissed.value, id]
}
</script>

<template>
  <div class="ai">
    <header class="ai__head">
      <span class="ai__spark"><Icon name="spark" :size="15" /></span>
      <div class="ai__titles">
        <p class="ai__name">AI 助手</p>
        <p class="ai__status" :class="`is-${status}`">
          <i class="dot" />
          {{ statusLabel }}
        </p>
      </div>
      <button type="button" class="icon-btn icon-btn--sm" title="重新检测智能体" @click="refreshStatus">
        <Icon name="refresh" :size="14" />
      </button>
    </header>

    <button type="button" class="ai__generate" @click="emit('generate')">
      <span class="ai__generate-icon"><Icon name="wand" :size="15" /></span>
      <span>
        <strong>用它生成整页方案</strong>
        <em>活动需求 → 首页 / 介绍 / 表单 / 成功页</em>
      </span>
      <Icon name="chevron" :size="13" class="ai__generate-arrow" />
    </button>

    <div class="ai__scroll scroll-y">
      <div v-for="item in messages" :key="item.id" class="bubble" :class="`bubble--${item.role}`">
        <p class="bubble__text">{{ item.text }}</p>
        <p v-if="item.meta" class="bubble__meta">{{ item.meta }}</p>
      </div>

      <div v-if="sending" class="bubble bubble--assistant">
        <p class="bubble__text bubble__typing"><i /><i /><i /></p>
      </div>

      <section class="sugs">
        <p class="sugs__title">
          可应用的修改
          <span>{{ hasBlock ? '针对选中组件' : '针对当前页面' }}</span>
        </p>
        <article v-for="item in suggestions" :key="item.id" class="sug">
          <p class="sug__title">{{ item.title }}</p>
          <p class="sug__reason">{{ item.reason }}</p>
          <p v-if="item.before || item.after" class="sug__diff">
            <span class="sug__before">{{ item.before }}</span>
            <Icon name="chevron" :size="11" class="sug__arrow" />
            <span class="sug__after">{{ item.after }}</span>
          </p>
          <div class="sug__acts">
            <button v-if="item.patch" type="button" class="btn btn--sm btn--primary" @click="applySuggestion(item.id)">
              应用
            </button>
            <button type="button" class="btn btn--sm btn--ghost" @click="dismissSuggestion(item.id)">忽略</button>
          </div>
        </article>
        <p v-if="!suggestions.length" class="sugs__empty">这一项暂时没有可自动应用的建议，直接问我就行。</p>
      </section>
    </div>

    <div class="ai__composer">
      <div class="ai__chips">
        <button v-for="prompt in QUICK_PROMPTS" :key="prompt" type="button" class="chip" @click="send(prompt)">
          {{ prompt }}
        </button>
      </div>
      <div class="ai__input-row">
        <textarea
          v-model="input"
          class="textarea ai__input"
          rows="2"
          placeholder="问点什么，或按 Enter 发送（Shift + Enter 换行）"
          @keydown.enter.exact.prevent="send()"
        />
        <button type="button" class="ai__send" :disabled="sending || !input.trim()" @click="send()">
          <Icon name="send" :size="15" />
        </button>
      </div>
      <p class="ai__note">AI 只输出组件结构，不会直接生成 HTML，改完都能撤销。</p>
    </div>
  </div>
</template>

<style scoped>
.ai {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.ai__head {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px 12px 11px 14px;
  border-bottom: 1px solid var(--line);
}

.ai__spark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--brand-50), #f3e8ff);
  color: var(--brand-600);
}

.ai__titles {
  flex: 1;
  min-width: 0;
}

.ai__name {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ink-800);
}

.ai__status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10.5px;
  color: var(--ink-400);
}

.ai__status .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink-300);
}

.ai__status.is-online {
  color: #047857;
}

.ai__status.is-online .dot {
  background: var(--mint-500);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.16);
}

.ai__status.is-offline {
  color: #b45309;
}

.ai__status.is-offline .dot {
  background: var(--amber-500);
}

.ai__settings {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--line);
  background: var(--ink-50);
}

.ai__settings-actions {
  display: flex;
  gap: 6px;
}

.ai__generate {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 11px 12px 0;
  padding: 9px 10px;
  border: 1px solid var(--brand-100);
  border-radius: 11px;
  background: linear-gradient(135deg, var(--brand-50), var(--accent-50));
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    transform 0.15s ease;
}

.ai__generate:hover {
  border-color: var(--brand-300);
  transform: translateY(-1px);
}

.ai__generate-icon {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  flex: 0 0 auto;
  border-radius: 8px;
  background: #fff;
  color: var(--brand-600);
  box-shadow: var(--shadow-sm);
}

.ai__generate strong {
  display: block;
  font-size: 12px;
  color: var(--ink-800);
}

.ai__generate em {
  display: block;
  font-style: normal;
  font-size: 10px;
  color: var(--ink-500);
}

.ai__generate-arrow {
  margin-left: auto;
  color: var(--brand-400);
  transform: rotate(-90deg);
}

.ai__scroll {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 12px 12px 6px;
}

.bubble {
  max-width: 94%;
  padding: 9px 11px;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.68;
}

.bubble--assistant {
  align-self: flex-start;
  background: #fff;
  border: 1px solid var(--line);
  color: var(--ink-700);
  border-bottom-left-radius: 4px;
}

.bubble--user {
  align-self: flex-end;
  background: linear-gradient(135deg, var(--brand-600), var(--violet-500));
  color: #fff;
  border-bottom-right-radius: 4px;
}

.bubble__text {
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble__meta {
  margin-top: 5px;
  font-size: 9.5px;
  color: var(--ink-400);
}

.bubble--user .bubble__meta {
  color: rgba(255, 255, 255, 0.7);
}

.bubble__typing {
  display: flex;
  gap: 3px;
  padding: 3px 0;
}

.bubble__typing i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink-300);
  animation: blink 1.1s infinite ease-in-out;
}

.bubble__typing i:nth-child(2) {
  animation-delay: 0.16s;
}

.bubble__typing i:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.28;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

.sugs {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 4px;
}

.sugs__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--ink-400);
}

.sugs__title span {
  font-weight: 500;
  letter-spacing: 0;
}

.sug {
  padding: 10px;
  border: 1px solid var(--brand-100);
  border-radius: 11px;
  background: #fff;
  box-shadow: 0 8px 22px -20px rgba(79, 70, 229, 0.8);
}

.sug__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-800);
}

.sug__reason {
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--ink-500);
}

.sug__diff {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 7px;
  font-size: 10.5px;
}

.sug__before {
  padding: 2px 6px;
  border-radius: 5px;
  background: var(--rose-50);
  color: #b42318;
  text-decoration: line-through;
  text-decoration-color: rgba(180, 35, 24, 0.4);
}

.sug__after {
  padding: 2px 6px;
  border-radius: 5px;
  background: var(--mint-50);
  color: #047857;
}

.sug__arrow {
  color: var(--ink-300);
  transform: rotate(-90deg);
}

.sug__acts {
  display: flex;
  gap: 5px;
  margin-top: 8px;
}

.sugs__empty {
  padding: 8px 2px;
  font-size: 10.5px;
  color: var(--ink-400);
}

.ai__composer {
  flex: 0 0 auto;
  padding: 10px 12px 12px;
  border-top: 1px solid var(--line);
  background: #fff;
}

.ai__chips {
  display: flex;
  gap: 5px;
  overflow-x: auto;
  padding-bottom: 7px;
  scrollbar-width: none;
}

.ai__chips::-webkit-scrollbar {
  display: none;
}

.chip {
  flex: 0 0 auto;
  padding: 4px 9px;
  border: 1px solid var(--line);
  border-radius: 99px;
  background: #fff;
  color: var(--ink-500);
  font-size: 10.5px;
  cursor: pointer;
  transition:
    border-color 0.14s ease,
    color 0.14s ease;
}

.chip:hover {
  border-color: var(--brand-300);
  color: var(--brand-700);
}

.ai__input-row {
  position: relative;
}

.ai__input {
  min-height: 58px;
  padding-right: 42px;
  font-size: 12px;
  resize: none;
}

.ai__send {
  position: absolute;
  right: 7px;
  bottom: 7px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--brand-600), var(--violet-500));
  color: #fff;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.ai__send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ai__note {
  margin-top: 6px;
  font-size: 9.5px;
  color: var(--ink-400);
  text-align: center;
}
</style>
