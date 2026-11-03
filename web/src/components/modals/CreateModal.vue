<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { Doc } from '@/types/editor'
import BaseModal from '@/components/ui/BaseModal.vue'
import Icon from '@/components/ui/Icon.vue'
import { metaOf } from '@/data/blockCatalog'
import { buildQuickDoc, newDocFromBuild } from '@/data/templates'
import { buildPlanPrompt, localDoc, normalizePlan, parsePlanFromText, planToDoc } from '@/services/planner'
import { AgentError, askAgent, extractJson, extractPoster, PLAN_SYSTEM_EXTRA, probeAgent } from '@/services/agent'
import { useEditorStore } from '@/stores/editor'
import { useToast } from '@/composables/useToast'
import { uploadAsset } from '@/services/assets'
import { loadTemplateCatalog, loadTemplateDocument, type TemplateCatalogItem } from '@/services/templates'

type Tab = 'ai' | 'template' | 'poster'

const props = withDefaults(defineProps<{ open: boolean; initialTab?: Tab; projectId?: string }>(), {
  initialTab: 'ai',
  projectId: '',
})
const emit = defineEmits<{ close: []; applied: [doc: Doc] }>()

const store = useEditorStore()
const toast = useToast()

const tab = ref<Tab>(props.initialTab)
const prompt = ref('')
const generating = ref(false)
const step = ref('')
const errorText = ref('')
const templates = ref<TemplateCatalogItem[]>([])
const result = ref<{
  doc: Doc
  source: 'agent' | 'local'
  reason: string
  note: string
} | null>(null)

const EXAMPLES = [
  '心理协会秋季招新，需要社团介绍、活动方向和报名表',
  '考试周心晴游园会，设置五个解压互动摊位，可匿名报名',
  '心理健康知识问答，完成后给出科普反馈，不做心理诊断',
  '21天自我关怀打卡，每天完成一个轻量练习',
]

watch(
  () => props.open,
  (open) => {
    if (open) {
      tab.value = props.initialTab
      result.value = null
      errorText.value = ''
      generating.value = false
    }
  }
)

onMounted(async () => {
  templates.value = await loadTemplateCatalog()
})

/* ------------------------------- 海报解析（多模态） ------------------------------- */

const posterFile = ref<string>('')
const posterName = ref('')
const posterParsing = ref(false)
/** 视觉能力没开（503）时不再反复重试，直接转手动填写 */
const posterVisionOff = ref(false)
const posterNotes = ref<string[]>([])
const posterMeta = ref('')
const posterEvidence = ref<{ label: string; original: string }[]>([])
const posterForm = ref({
  title: '',
  org: '',
  startAt: '',
  place: '',
  deadline: '',
  capacity: 0,
  contact: '',
})

const EVIDENCE_LABEL: Record<string, string> = {
  title: '活动名称',
  org: '主办方',
  startAt: '时间',
  place: '地点',
  deadline: '截止时间',
  capacity: '名额',
  fee: '费用',
  contact: '联系方式',
  intro: '宣传语',
}

async function compressPoster(file: File): Promise<string> {
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
  const image = await new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('图片读取失败'))
    img.src = dataUrl
  })
  const maxSide = 1400
  const scale = Math.min(1, maxSide / Math.max(image.width, image.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(image.width * scale))
  canvas.height = Math.max(1, Math.round(image.height * scale))
  canvas.getContext('2d')?.drawImage(image, 0, 0, canvas.width, canvas.height)
  return canvas.toDataURL('image/jpeg', 0.82)
}

/**
 * 让视觉模型先读一遍海报，把能认出来的字段填进表单。
 *
 * 三条原则写在这里，改代码的人别破坏：
 * 1. 只覆盖**有值**的字段——模型没认出来就留用户原来填的，不拿空值冲掉数据。
 * 2. 认不出的、把握不大的，一条条列在表单上方，不假装全都认得。
 * 3. 视觉能力没开（503）就静默转手动，别弹一个看不懂的错误吓用户。
 */
async function recognizePoster(): Promise<void> {
  if (!posterFile.value || posterParsing.value) return
  posterParsing.value = true
  posterNotes.value = []
  posterMeta.value = ''
  posterEvidence.value = []

  try {
    const result = await extractPoster(posterFile.value)
    const info = result.info

    let filled = 0
    const apply = (key: keyof typeof posterForm.value, value: string | number | null): void => {
      if (value === null || value === '' || value === undefined) return
      ;(posterForm.value[key] as string | number) = value
      filled += 1
    }
    apply('title', info.title)
    apply('org', info.org)
    apply('startAt', info.startAt)
    apply('place', info.place)
    apply('deadline', info.deadline)
    apply('capacity', info.capacity)
    apply('contact', info.contact)

    const notes = [...result.warnings]
    if (info.unclear.length) notes.push(`没读清：${info.unclear.join('、')}`)
    if (info.signupFields.length) notes.push(`海报里提到要填：${info.signupFields.join('、')}`)
    posterNotes.value = notes
    posterMeta.value = `${result.model} · ${result.latencyMs}ms · 把握 ${Math.round(info.confidence * 100)}%`
    posterEvidence.value = Object.entries(info.evidence)
      .filter(([key]) => EVIDENCE_LABEL[key])
      .slice(0, 6)
      .map(([key, original]) => ({ label: EVIDENCE_LABEL[key] ?? key, original: String(original) }))

    if (filled === 0) {
      toast.pushToast('这张海报没读出可用信息，请手动填', 'warn')
    } else {
      toast.pushToast(`已从海报读出 ${filled} 项，请核对后生成`, 'success')
    }
  } catch (error) {
    if (error instanceof AgentError && error.code === 'NOT_CONFIGURED') {
      posterVisionOff.value = true
    } else if (error instanceof AgentError && error.code === 'IMAGE') {
      toast.pushToast(error.message, 'warn')
    } else {
      toast.pushToast(error instanceof Error ? error.message : '读图失败，先手动填吧', 'warn')
    }
  } finally {
    posterParsing.value = false
  }
}

async function onPosterPick(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast.pushToast('只能上传图片', 'warn')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    toast.pushToast('图片不要超过 5MB', 'warn')
    return
  }
  try {
    const compressed = await compressPoster(file)
    if (props.projectId) {
      try {
        const asset = await uploadAsset(file, props.projectId)
        posterFile.value = asset.url
      } catch {
        posterFile.value = compressed
        toast.pushToast('后端图片上传失败，暂时使用本地压缩图片', 'warn')
      }
    } else {
      posterFile.value = compressed
    }
  } catch {
    toast.pushToast('图片处理失败，请换一张图片', 'error')
    return
  }
  posterName.value = file.name
  posterForm.value.title = store.doc.title
  posterForm.value.org = store.doc.meta.org
  void recognizePoster()
}

/* ------------------------------- AI 生成 ------------------------------- */

const generatedPages = computed(() => result.value?.doc.pages ?? [])

async function generate(): Promise<void> {
  const text = prompt.value.trim()
  if (!text) {
    errorText.value = '请先描述活动需求，比如「摄影社招新，10月26日晚7点面试」'
    return
  }
  generating.value = true
  errorText.value = ''
  result.value = null

  try {
    step.value = '正在看智能体在不在线…'
    const health = await probeAgent(1400)
    if (health.ok) {
      step.value = '正在让智能体排页面结构…'
      try {
        const reply = await askAgent(buildPlanPrompt(text), {
          systemExtra: PLAN_SYSTEM_EXTRA,
        })
        const json = extractJson(reply.reply)
        const plan = normalizePlan(json) ?? parsePlanFromText(reply.reply)
        if (plan) {
          const doc = planToDoc(plan, store.doc)
          result.value = {
            doc,
            source: 'agent',
            reason: plan.reason || '智能体按活动需求排好了页面顺序和组件。',
            note: json ? '智能体 · 结构化 JSON' : '智能体 · 文本结构解析',
          }
          return
        }
        toast.pushToast('智能体这次没给出页面结构，已用本地模板兜底', 'warn')
      } catch (error) {
        const message = error instanceof Error ? error.message : '智能体调用失败'
        toast.pushToast(`${message}，已用本地模板兜底`, 'warn')
      }
    }

    step.value = '正在按场景匹配模板…'
    const local = localDoc(text, store.doc)
    result.value = {
      doc: local.doc,
      source: 'local',
      reason: local.reason,
      note: `本地规则 · ${local.scene}模板`,
    }
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '生成失败了，重试一次'
  } finally {
    generating.value = false
    step.value = ''
  }
}

function applyResult(): void {
  if (!result.value) return
  emit('applied', result.value.doc)
}

/* ------------------------------- 模板 / 海报 ------------------------------- */

async function applyTemplate(id: string): Promise<void> {
  emit('applied', await loadTemplateDocument(id))
}

function applyPoster(): void {
  const build = buildQuickDoc({
    title: posterForm.value.title || '心理活动',
    org: posterForm.value.org,
    startAt: posterForm.value.startAt,
    place: posterForm.value.place,
    deadline: posterForm.value.deadline,
    capacity: Number(posterForm.value.capacity) || 0,
    contact: posterForm.value.contact,
  })
  const next = newDocFromBuild(build, store.doc)
  const homePage = next.pages.find((page) => page.role === 'home') ?? next.pages[0]
  const banner = homePage?.blocks.find((block) => block.type === 'banner')
  if (banner && posterFile.value) {
    banner.props.image = posterFile.value
  }
  emit('applied', next)
}

function close(): void {
  emit('close')
}

function startBlankAndClose(): void {
  store.startBlank()
  close()
}

type PosterTextKey = 'title' | 'org' | 'startAt' | 'place' | 'deadline' | 'contact'

const FORM_FIELDS: { key: PosterTextKey; label: string; placeholder: string }[] = [
  { key: 'title', label: '活动名称', placeholder: '如 光影摄影社招新' },
  { key: 'org', label: '主办方', placeholder: '如 光影摄影社' },
  { key: 'startAt', label: '时间', placeholder: '如 12月26日 19:00' },
  { key: 'place', label: '地点', placeholder: '如 三教 402' },
  { key: 'deadline', label: '报名截止', placeholder: '如 12-25 22:00' },
  { key: 'contact', label: '联系方式', placeholder: '微信 / 群名' },
]
</script>

<template>
  <BaseModal
    :open="open"
    title="制作心晴活动页面"
    subtitle="从活动需求、一个心晴模板，或者一张海报开始"
    :width="840"
    @close="close"
  >
    <div class="create">
      <div class="create__tabs">
        <button type="button" class="ctab" :class="{ 'is-active': tab === 'ai' }" @click="tab = 'ai'">
          <Icon name="spark" :size="14" /> AI 辅助生成
        </button>
        <button type="button" class="ctab" :class="{ 'is-active': tab === 'template' }" @click="tab = 'template'">
          <Icon name="grid" :size="14" /> 心晴模板
        </button>
        <button type="button" class="ctab" :class="{ 'is-active': tab === 'poster' }" @click="tab = 'poster'">
          <Icon name="image" :size="14" /> 上传活动海报
        </button>
        <button type="button" class="create__blank" @click="startBlankAndClose">
          <Icon name="file" :size="13" /> 从空白开始
        </button>
      </div>

      <!-- AI -->
      <section v-if="tab === 'ai'" class="ai-tab">
        <label class="field">
            <span class="field__label">描述心晴活动需求</span>
          <textarea
            v-model="prompt"
            class="textarea create__prompt"
            rows="3"
            placeholder="例如：做一个考试周解压游园会，包含活动介绍、匿名报名和保密说明"
          />
        </label>
        <div class="create__examples">
          <button v-for="example in EXAMPLES" :key="example" type="button" class="chip" @click="prompt = example">
            {{ example }}
          </button>
        </div>

        <div class="create__actions">
          <button type="button" class="btn btn--primary" :disabled="generating" @click="generate">
            <Icon :name="generating ? 'refresh' : 'wand'" :size="14" />
            {{ generating ? step || '生成中…' : '生成页面方案' }}
          </button>
          <span class="hint">智能体在线就用智能体，离线自动用本地规则——两条路都能出稿。</span>
        </div>

        <p v-if="errorText" class="create__error">{{ errorText }}</p>

        <div v-if="result" class="result">
          <header class="result__head">
            <div>
              <p class="result__title">{{ result.doc.title }}</p>
              <p class="result__note">
                {{ result.note }} · {{ result.doc.pages.length }} 页 ·
                {{ result.doc.pages.reduce((sum, page) => sum + page.blocks.length, 0) }}
                个组件
              </p>
            </div>
            <span class="result__badge" :class="result.source === 'agent' ? 'is-agent' : ''">
              {{ result.source === 'agent' ? '智能体' : '本地规则' }}
            </span>
          </header>

          <p class="result__reason">{{ result.reason }}</p>

          <ul class="result__pages">
            <li v-for="page in generatedPages" :key="page.id" class="result__page">
              <span class="result__page-name">{{ page.name }}</span>
              <span class="result__chips">
                <span v-for="block in page.blocks" :key="block.id" class="result__chip">
                  {{ metaOf(block.type).label }}
                </span>
              </span>
            </li>
          </ul>

          <div class="result__foot">
            <div class="result__theme">
              <span class="result__swatch" :style="{ background: result.doc.theme.primary }" />
              <span class="result__swatch" :style="{ background: result.doc.theme.accent }" />
              <span class="hint">主题色</span>
            </div>
            <div class="result__buttons">
              <button type="button" class="btn btn--sm" @click="result = null">重新生成</button>
              <button type="button" class="btn btn--sm btn--primary" @click="applyResult">
                <Icon name="check" :size="13" /> 应用到画布
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- 模板 -->
      <section v-else-if="tab === 'template'" class="tpl-tab">
        <article v-for="template in templates" :key="template.id" class="tpl-card">
          <div
            class="tpl-card__cover"
            :style="{
              background: `linear-gradient(135deg, ${template.gradient[0]}, ${template.gradient[1]})`,
            }"
          >
            <Icon :name="template.icon" :size="20" />
            <span>{{ template.scene }}</span>
          </div>
          <div class="tpl-card__body">
            <p class="tpl-card__name">{{ template.name }}</p>
            <p class="tpl-card__tag">{{ template.tagline }}</p>
            <button type="button" class="btn btn--sm btn--block" @click="applyTemplate(template.id)">使用模板</button>
          </div>
        </article>
      </section>

      <!-- 海报 -->
      <section v-else class="poster-tab">
        <div class="poster-tab__upload" :class="{ 'has-image': posterFile }">
          <img v-if="posterFile" :src="posterFile" alt="活动海报预览" class="poster-tab__img" />
          <div v-else class="poster-tab__empty">
            <Icon name="image" :size="24" />
            <p>把活动海报拖进来 / 点这里选择</p>
            <span>支持 PNG · JPG，5MB 以内</span>
          </div>
          <input type="file" accept="image/*" class="poster-tab__input" @change="onPosterPick" />
        </div>

        <div class="poster-tab__form">
          <p v-if="posterParsing" class="poster-tab__note poster-tab__note--busy">
            <Icon name="wand" :size="12" />
            正在读海报里的活动信息…
          </p>
          <p v-else-if="posterVisionOff" class="poster-tab__note">
            <Icon name="info" :size="12" />
            读图功能没开（智能体没配视觉模型），下面的字段请手动填写。
          </p>
          <p v-else class="poster-tab__note poster-tab__note--ok">
            <Icon name="wand" :size="12" />
            AI 会先读海报预填字段；认不出来的留空，不会瞎编。填完请核对。
          </p>

          <ul v-if="posterNotes.length" class="poster-tab__notes">
            <li v-for="note in posterNotes" :key="note">{{ note }}</li>
          </ul>

          <details v-if="posterEvidence.length" class="poster-tab__evidence">
            <summary>看 AI 是从海报哪句话读出来的</summary>
            <ul>
              <li v-for="item in posterEvidence" :key="item.label">
                <span>{{ item.label }}</span>
                <em>「{{ item.original }}」</em>
              </li>
            </ul>
          </details>

          <label v-for="field in FORM_FIELDS" :key="field.key" class="field">
            <span class="field__label">{{ field.label }}</span>
            <input v-model="posterForm[field.key]" class="input" :placeholder="field.placeholder" />
          </label>
          <label class="field">
            <span class="field__label">人数上限</span>
            <input v-model.number="posterForm.capacity" class="input" type="number" min="0" />
          </label>
          <button type="button" class="btn btn--primary btn--block" @click="applyPoster">用这些信息生成页面</button>
          <p v-if="posterMeta" class="hint">{{ posterMeta }}</p>
          <p v-if="posterName" class="hint">已选择：{{ posterName }}</p>
        </div>
      </section>
    </div>
  </BaseModal>
</template>

<style scoped>
.create {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.create__tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.ctab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 12px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  color: var(--ink-500);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.14s ease,
    color 0.14s ease;
}

.ctab:hover {
  background: var(--ink-100);
  color: var(--ink-800);
}

.ctab.is-active {
  background: var(--brand-50);
  border-color: var(--brand-100);
  color: var(--brand-700);
}

.create__blank {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  padding: 5px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink-400);
  font-size: 11.5px;
  cursor: pointer;
}

.create__blank:hover {
  background: var(--ink-100);
  color: var(--ink-700);
}

.create__prompt {
  min-height: 82px;
  font-size: 13px;
}

.create__examples {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 5px 10px;
  border: 1px solid var(--line);
  border-radius: 99px;
  background: #fff;
  color: var(--ink-600);
  font-size: 11.5px;
  cursor: pointer;
  transition:
    border-color 0.14s ease,
    color 0.14s ease;
}

.chip:hover {
  border-color: var(--brand-300);
  color: var(--brand-700);
}

.create__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.create__error {
  padding: 9px 11px;
  border-radius: 9px;
  background: var(--rose-50);
  color: #b42318;
  font-size: 12px;
}

/* 结果 */
.result {
  padding: 14px;
  border: 1px solid var(--brand-100);
  border-radius: 14px;
  background: linear-gradient(180deg, #fbfaff, #fff);
}

.result__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.result__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-900);
}

.result__note {
  margin-top: 2px;
  font-size: 11px;
  color: var(--ink-400);
}

.result__badge {
  padding: 3px 8px;
  border-radius: 99px;
  background: var(--amber-50);
  color: #b45309;
  font-size: 10.5px;
  font-weight: 600;
  white-space: nowrap;
}

.result__badge.is-agent {
  background: var(--mint-50);
  color: #047857;
}

.result__reason {
  margin-top: 9px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-600);
}

.result__pages {
  list-style: none;
  margin: 11px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.result__page {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.result__page-name {
  width: 68px;
  flex: 0 0 auto;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--ink-700);
}

.result__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.result__chip {
  padding: 2px 7px;
  border-radius: 5px;
  background: var(--ink-100);
  color: var(--ink-500);
  font-size: 10px;
}

.result__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 13px;
  padding-top: 11px;
  border-top: 1px dashed var(--line-strong);
}

.result__theme {
  display: flex;
  align-items: center;
  gap: 5px;
}

.result__swatch {
  width: 16px;
  height: 16px;
  border-radius: 5px;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.result__buttons {
  display: flex;
  gap: 6px;
}

/* 模板 */
.tpl-tab {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.tpl-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 13px;
  background: #fff;
}

.tpl-card__cover {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 82px;
  padding: 11px;
  color: #fff;
}

.tpl-card__cover span {
  font-size: 9.5px;
  opacity: 0.9;
}

.tpl-card__body {
  padding: 9px;
}

.tpl-card__name {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ink-800);
}

.tpl-card__tag {
  margin: 2px 0 8px;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--ink-400);
  min-height: 30px;
}

/* 海报 */
.poster-tab {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
}

.poster-tab__upload {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 220px;
  border: 1.5px dashed var(--line-strong);
  border-radius: 13px;
  background: var(--ink-50);
  overflow: hidden;
  cursor: pointer;
}

.poster-tab__upload:hover {
  border-color: var(--brand-300);
  background: var(--brand-50);
}

.poster-tab__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-tab__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: var(--ink-400);
  font-size: 11.5px;
  text-align: center;
  padding: 18px;
}

.poster-tab__empty span {
  font-size: 10px;
  color: var(--ink-300);
}

.poster-tab__input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.poster-tab__form {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.poster-tab__note {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  padding: 8px 10px;
  border-radius: 9px;
  background: var(--amber-50);
  color: #b45309;
  font-size: 11px;
  line-height: 1.6;
}

.poster-tab__note--ok {
  background: var(--brand-50);
  color: var(--brand-700, #3730a3);
}

.poster-tab__note--busy {
  background: var(--brand-50);
  color: var(--brand-700, #3730a3);
}

.poster-tab__notes {
  margin: 0;
  padding: 0 0 0 16px;
  font-size: 11px;
  line-height: 1.7;
  color: #b45309;
}

.poster-tab__evidence {
  font-size: 11px;
  color: var(--ink-500);
  background: var(--ink-50);
  border-radius: 9px;
  padding: 7px 10px;
}

.poster-tab__evidence summary {
  cursor: pointer;
  color: var(--ink-600);
}

.poster-tab__evidence ul {
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
}

.poster-tab__evidence li {
  display: flex;
  gap: 6px;
  padding: 2px 0;
  line-height: 1.6;
}

.poster-tab__evidence li span {
  flex: 0 0 56px;
  color: var(--ink-400);
}

.poster-tab__evidence li em {
  font-style: normal;
  color: var(--ink-700);
}

@media (max-width: 860px) {
  .tpl-tab {
    grid-template-columns: repeat(2, 1fr);
  }

  .poster-tab {
    grid-template-columns: 1fr;
  }
}
</style>
