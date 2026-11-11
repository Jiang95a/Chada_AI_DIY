<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { useProjectStore } from '@/stores/projects'
import coverGarden from '@/assets/activity-cover-garden.svg'
import coverSelfCare from '@/assets/activity-cover-self-care.svg'
import coverClub from '@/assets/activity-cover-club.svg'
import coverQuiet from '@/assets/activity-cover-quiet.svg'
import {
  loadTemplateCatalog,
  removeCustomTemplate,
  type TemplateCatalogItem,
  type TemplateKind,
} from '@/services/templates'

/**
 * 模板灵感页。
 *
 * 之前左侧栏的「模板灵感」指向 /projects/new#templates —— 那只是创建页里的一个
 * 锚点分区，同一个页面内点它，vue-router 不会滚动，表现就是"点了没反应"。
 * 现在给它一条真路由，模板能预览、能一键建成项目。
 *
 * 模板优先读取后端 GET /templates；后端不可用时由 services/templates.ts
 * 自动回退到 data/templates.ts，保证离线演示仍可用。
 */

const router = useRouter()
const toast = useToast()
const projectStore = useProjectStore()

const creatingId = ref('')
const deletingTemplate = ref<TemplateCard | null>(null)
const catalog = ref<TemplateCatalogItem[]>([])
const activeKind = ref<TemplateKind | 'all'>('all')
const aiPrompt = ref('')
const aiExamples = [
  '心理协会秋季招新，包含社团介绍、活动方向和报名表',
  '考试周心晴游园会，包含活动介绍、时间地点和报名',
  '21 天自我关怀打卡，每天完成一个轻量练习',
]

interface TemplateCard {
  id: string
  name: string
  scene: string
  tagline: string
  icon: string
  source: TemplateCatalogItem['source']
  kind: TemplateKind
  title: string
  pageCount: number
  blockCount: number
  previewPages: string[]
  coverImage: string
}

const templateCovers: Record<string, string> = {
  recruit: coverClub,
  signup: coverGarden,
  vote: coverQuiet,
  checkin: coverSelfCare,
}

const cards = computed<TemplateCard[]>(() =>
  catalog.value.map((template) => {
    const build = template.build()
    return {
      id: template.id,
      name: template.name,
      scene: template.scene,
      tagline: template.tagline,
      icon: template.icon,
      source: template.source,
      kind: template.kind,
      title: build.title,
      pageCount: build.pages.length,
      blockCount: build.pages.reduce((total, page) => total + page.blocks.length, 0),
      previewPages: build.pages.map((page) => page.name),
      coverImage: templateCovers[template.id] ?? (template.kind === 'career' ? coverQuiet : coverGarden),
    }
  })
)

const customCount = computed(() => cards.value.filter((card) => card.source === 'custom').length)
const visibleCards = computed(() =>
  activeKind.value === 'all' ? cards.value : cards.value.filter((card) => card.kind === activeKind.value)
)

async function loadTemplates(): Promise<void> {
  catalog.value = await loadTemplateCatalog()
}

onMounted(loadTemplates)

async function useTemplate(card: TemplateCard): Promise<void> {
  if (creatingId.value) return
  creatingId.value = card.id
  try {
    const project = await projectStore.createProjectRemote({
      title: card.title || card.name,
      type: card.name,
      templateId: card.id,
    })
    toast.pushToast(`已按「${card.name}」建好项目，正在打开编辑器`, 'success')
    await router.push(`/editor/${project.id}`)
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '创建失败，稍后再试', 'error')
  } finally {
    creatingId.value = ''
  }
}

function askAi(prompt = aiPrompt.value): void {
  const value = prompt.trim()
  void router.push({
    path: '/ai',
    query: value ? { prompt: value } : undefined,
  })
}

async function confirmDeleteTemplate(): Promise<void> {
  if (!deletingTemplate.value) return
  removeCustomTemplate(deletingTemplate.value.id)
  deletingTemplate.value = null
  await loadTemplates()
  toast.pushToast('心晴模板已删除', 'success')
}
</script>

<template>
  <WorkspaceShell>
    <main class="templates-page">
      <header class="templates-head">
        <div>
          <h1>模板库</h1>
          <p class="templates-sub">从四种高频心晴场景开始，进入页面工坊替换文案、图片和组件。</p>
        </div>
        <button class="app-button app-button--primary" type="button" @click="askAi()">
          <Icon name="wand" :size="16" />让 AI 帮我做页面
        </button>
      </header>

      <section class="templates-ai-builder">
        <div class="templates-ai-builder__copy">
          <span class="templates-ai-badge"><Icon name="spark" :size="15" />AI WELLBEING BUILDER</span>
          <h2>描述一场心晴活动，生成完整页面</h2>
          <p>说清活动对象、参与方式和需要收集的信息，AI 会排好页面结构并创建活动草稿。</p>
          <textarea
            v-model="aiPrompt"
            rows="4"
            maxlength="300"
            placeholder="例如：做一个考试周解压游园会页面，需要活动介绍、摊位信息、匿名报名和保密说明"
            @keydown.ctrl.enter.prevent="askAi()"
            @keydown.meta.enter.prevent="askAi()"
          />
          <div class="templates-ai-examples">
            <button v-for="example in aiExamples" :key="example" type="button" @click="aiPrompt = example">
              {{ example }}
            </button>
          </div>
          <button class="templates-ai-submit" type="button" @click="askAi()">
            <Icon name="wand" :size="18" />
            <span><strong>生成我的心晴活动页</strong><small>Ctrl / ⌘ + Enter</small></span>
            <Icon name="arrow-right" :size="19" />
          </button>
        </div>
        <div class="templates-ai-builder__preview">
          <span>AI 会为心理活动准备</span>
          <div class="ai-preview-page">
            <i />
            <strong>温柔的活动主张</strong>
            <p>活动介绍 · 参与方式 · 安心说明</p>
            <div><span /><span /><span /></div>
          </div>
          <div class="ai-preview-page ai-preview-page--small">
            <i />
            <strong>参与表单与确认页</strong>
            <p>最小信息 · 匿名选项 · 保密提示</p>
          </div>
          <ul>
            <li><Icon name="check" :size="12" />匹配心理活动组件</li>
            <li><Icon name="check" :size="12" />生成后仍可自由调整</li>
            <li><Icon name="check" :size="12" />隐私提示与表单一起生成</li>
          </ul>
        </div>
      </section>

      <div class="templates-list-heading">
        <div>
          <h2>从心晴场景开始</h2>
          <p>心理社团、团辅活动、知识问答和自我关怀都有成熟结构。</p>
        </div>
        <span
          >{{ cards.length }} 个模板<template v-if="customCount"> · 我的心晴 {{ customCount }}</template></span
        >
      </div>

      <div class="templates-kinds">
        <button type="button" :class="{ 'is-active': activeKind === 'all' }" @click="activeKind = 'all'">全部</button>
        <button type="button" :class="{ 'is-active': activeKind === 'wellbeing' }" @click="activeKind = 'wellbeing'">
          心理活动
        </button>
        <button type="button" :class="{ 'is-active': activeKind === 'career' }" @click="activeKind = 'career'">
          生涯发展
        </button>
      </div>

      <section class="templates-grid">
        <article v-for="card in visibleCards" :key="card.id" class="template-card">
          <div
            class="template-card__art"
            :class="`template-card__art--${card.id}`"
          >
            <img class="template-card__image" :src="card.coverImage" alt="" loading="lazy" decoding="async" />
            <span class="template-card__scene">{{ card.scene }}</span>
            <span v-if="card.source === 'custom'" class="template-card__custom">我的心晴模板</span>
            <span v-else-if="card.kind === 'career'" class="template-card__kind">生涯</span>
            <Icon :name="card.icon || 'grid'" :size="22" />
            <strong>{{ card.name }}</strong>
          </div>
          <div class="template-card__body">
            <h2>{{ card.name }}</h2>
            <p>{{ card.tagline }}</p>
            <ul class="template-card__meta">
              <li>{{ card.pageCount }} 页</li>
              <li>{{ card.blockCount }} 个组件</li>
              <li>{{ card.previewPages.join(' / ') }}</li>
            </ul>
          </div>
          <div class="template-card__foot">
            <span class="template-card__title">示例标题：{{ card.title }}</span>
            <div class="template-card__actions">
              <button
                v-if="card.source === 'custom'"
                class="template-card__delete"
                type="button"
                aria-label="删除自定义模板"
                @click="deletingTemplate = card"
              >
                <Icon name="trash" :size="14" />
              </button>
              <button
                class="app-button app-button--primary"
                type="button"
                :disabled="creatingId === card.id"
                @click="useTemplate(card)"
              >
                <Icon name="check" :size="15" />
                {{ creatingId === card.id ? '创建中…' : '用这个模板创建' }}
              </button>
            </div>
          </div>
        </article>
      </section>
    </main>

    <BaseModal
      :open="Boolean(deletingTemplate)"
      title="删除心晴模板"
      subtitle="只删除模板副本，不会影响已经创建的活动项目。"
      :width="430"
      @close="deletingTemplate = null"
    >
      <div class="delete-template-confirm">
        <span><Icon name="trash" :size="19" /></span>
        <div>
          <strong>确定删除「{{ deletingTemplate?.name }}」？</strong>
          <p>删除后无法继续在新建活动或编辑器模板面板中使用这个模板。</p>
        </div>
      </div>
      <template #footer>
        <button class="app-button app-button--secondary" type="button" @click="deletingTemplate = null">取消</button>
        <button class="template-delete-confirm" type="button" @click="confirmDeleteTemplate">确认删除</button>
      </template>
    </BaseModal>
  </WorkspaceShell>
</template>

<style scoped>
.templates-page {
  display: flex;
  width: min(1440px, 100%);
  margin: 0 auto;
  flex-direction: column;
  gap: 24px;
  padding: 36px 36px 64px;
}

.templates-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.templates-head h1 {
  margin: 0;
  color: var(--brand-900);
  font-family: var(--font-serif);
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.035em;
  line-height: 1.12;
}

.templates-sub {
  max-width: 640px;
  margin: 9px 0 0;
  color: var(--ink-500);
  font-size: 13px;
  line-height: 1.75;
}

.templates-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.templates-kinds {
  display: flex;
  gap: 5px;
  width: max-content;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: var(--radius-button);
  background: rgb(255 253 249 / 82%);
}

.templates-kinds button {
  min-height: 31px;
  padding: 0 13px;
  border: 0;
  border-radius: 9px;
  color: var(--ink-500);
  background: transparent;
  font-size: 9px;
  cursor: pointer;
}

.templates-kinds button.is-active {
  color: var(--brand-700);
  background: var(--brand-100);
}

.template-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  background: var(--surface);
  box-shadow: var(--shadow-card);
  transition:
    transform 200ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 200ms ease,
    border-color 200ms ease;
}

.template-card:hover {
  transform: translateY(-4px);
  border-color: var(--brand-200);
  box-shadow: 0 18px 34px -26px rgb(37 55 45 / 42%);
}

.template-card__art {
  position: relative;
  display: flex;
  min-height: 168px;
  flex-direction: column;
  justify-content: flex-end;
  gap: 7px;
  overflow: hidden;
  padding: 18px;
  color: var(--brand-900);
}

.template-card__image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 380ms cubic-bezier(0.22, 1, 0.36, 1);
}

.template-card:hover .template-card__image {
  transform: scale(1.025);
}

.template-card__art::before {
  position: absolute;
  top: 24px;
  right: 22px;
  width: 74px;
  height: 74px;
  border: 1px solid currentcolor;
  border-radius: 48% 52% 42% 58%;
  opacity: 0.18;
  content: '';
  transform: rotate(18deg);
}

.template-card__art::after {
  position: absolute;
  right: -38px;
  bottom: -70px;
  width: 170px;
  height: 170px;
  border: 1px solid currentcolor;
  border-radius: 50%;
  opacity: 0.12;
  content: '';
}

.template-card__art--recruit {
  background: #dce9dd;
}

.template-card__art--signup {
  background: #eadfd3;
}

.template-card__art--vote {
  background: #bfd6cf;
}

.template-card__art--checkin {
  background: #24483a;
}

.template-card__art:not([class*='--recruit']):not([class*='--signup']):not([class*='--vote']):not(
    [class*='--checkin']
  ) {
  background: var(--brand-100);
}

.template-card__scene {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 5px 9px;
  border: 1px solid rgb(45 80 70 / 13%);
  border-radius: 999px;
  color: rgb(31 49 41 / 64%);
  background: rgb(255 255 255 / 40%);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
  backdrop-filter: blur(8px);
  z-index: 2;
}

.template-card__custom {
  position: absolute;
  right: 12px;
  bottom: 12px;
  top: auto;
  left: auto;
  padding: 3px 7px;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 999px;
  color: var(--brand-900);
  background: rgb(255 255 255 / 42%);
  font-size: 8px;
  font-weight: 700;
  backdrop-filter: blur(7px);
  z-index: 2;
}

.template-card__kind {
  position: absolute;
  right: 12px;
  bottom: 12px;
  top: auto;
  left: auto;
  padding: 3px 7px;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 999px;
  color: var(--brand-900);
  background: rgb(255 255 255 / 42%);
  font-size: 8px;
  font-weight: 700;
  backdrop-filter: blur(7px);
  z-index: 2;
}

.template-card__art strong {
  position: relative;
  z-index: 1;
  display: block;
  width: max-content;
  max-width: 180px;
  padding: 7px 10px;
  border-radius: 10px;
  background: rgb(255 253 249 / 88%);
  font-family: var(--font-serif);
  font-size: 21px;
  line-height: 1.25;
}

.template-card__art > svg {
  position: absolute;
  top: 35px;
  right: 35px;
  z-index: 1;
  box-sizing: content-box;
  padding: 7px;
  border-radius: 50%;
  color: rgb(31 49 41 / 44%);
  background: rgb(255 253 249 / 78%);
}

.template-card__body {
  flex: 1;
  padding: 17px 18px 10px;
}

.template-card__body h2 {
  margin: 0 0 4px;
  color: var(--brand-900);
  font-size: 15px;
  letter-spacing: -0.015em;
}

.template-card__body p {
  margin: 0;
  color: var(--ink-500);
  font-size: 11.5px;
  line-height: 1.7;
}

.template-card__meta {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.template-card__meta li {
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--surface-soft);
  font-size: 10px;
  color: var(--ink-500);
}

.template-card__foot {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 18px 18px;
}

.template-card__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.template-card__actions .app-button {
  flex: 1;
  min-height: 38px;
  font-size: 12px;
}

.template-card__delete {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border: 1px solid var(--line);
  border-radius: 9px;
  color: var(--ink-400);
  background: #fff;
  cursor: pointer;
  place-items: center;
}

.template-card__delete:hover {
  border-color: #fecaca;
  color: #d92d20;
  background: #fef2f2;
}

.template-card__title {
  overflow: hidden;
  color: var(--ink-400);
  font-size: 10.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.templates-ai-builder {
  position: relative;
  display: grid;
  gap: 30px;
  overflow: hidden;
  padding: 30px;
  border-radius: 22px;
  color: #fff;
  background: #24483a;
  grid-template-columns: minmax(0, 1.25fr) minmax(340px, 0.75fr);
}

.templates-ai-builder::after {
  position: absolute;
  right: -110px;
  bottom: -180px;
  width: 420px;
  height: 420px;
  border: 1px solid rgb(255 255 255 / 14%);
  border-radius: 50%;
  content: '';
}

.templates-ai-builder__copy,
.templates-ai-builder__preview {
  position: relative;
  z-index: 1;
}

.templates-ai-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 9px;
  border: 1px solid rgb(255 255 255 / 16%);
  border-radius: 999px;
  color: #d1e8d7;
  background: rgb(255 255 255 / 8%);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.templates-ai-builder h2 {
  margin: 18px 0 8px;
  font-size: clamp(26px, 3.4vw, 40px);
  letter-spacing: -0.055em;
  line-height: 1.08;
}

.templates-ai-builder__copy > p {
  max-width: 620px;
  margin: 0 0 18px;
  color: rgb(255 255 255 / 65%);
  font-size: 12px;
  line-height: 1.7;
}

.templates-ai-builder textarea {
  width: 100%;
  padding: 15px 16px;
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 14px;
  outline: none;
  color: #fff;
  background: rgb(7 14 33 / 38%);
  font: inherit;
  font-size: 12px;
  line-height: 1.7;
  resize: vertical;
  backdrop-filter: blur(10px);
}

.templates-ai-builder textarea::placeholder {
  color: rgb(255 255 255 / 38%);
}

.templates-ai-builder textarea:focus {
  border-color: rgb(151 239 217 / 65%);
  box-shadow: 0 0 0 3px rgb(88 215 188 / 10%);
}

.templates-ai-examples {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.templates-ai-examples button {
  padding: 6px 9px;
  border: 1px solid rgb(255 255 255 / 13%);
  border-radius: 999px;
  color: rgb(255 255 255 / 65%);
  background: rgb(255 255 255 / 6%);
  font-size: 8px;
  cursor: pointer;
}

.templates-ai-examples button:hover {
  color: #fff;
  background: rgb(255 255 255 / 12%);
}

.templates-ai-submit {
  display: flex;
  width: 100%;
  min-height: 58px;
  align-items: center;
  gap: 11px;
  margin-top: 14px;
  padding: 0 15px;
  border: 0;
  border-radius: 14px;
  color: #102239;
  background: #e7eadb;
  text-align: left;
  cursor: pointer;
  transition:
    transform 160ms ease,
    box-shadow 160ms ease;
}

.templates-ai-submit:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 32px -18px rgb(158 241 220 / 70%);
}

.templates-ai-submit > span {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.templates-ai-submit strong {
  font-size: 12px;
}

.templates-ai-submit small {
  color: rgb(16 34 57 / 56%);
  font-size: 8px;
}

.templates-ai-builder__preview {
  display: flex;
  justify-content: center;
  flex-direction: column;
}

.templates-ai-builder__preview > span {
  margin-bottom: 11px;
  color: rgb(255 255 255 / 52%);
  font-size: 9px;
  font-weight: 700;
}

.ai-preview-page {
  padding: 15px;
  border: 1px solid rgb(255 255 255 / 16%);
  border-radius: 15px;
  background: rgb(255 255 255 / 9%);
  box-shadow: 0 18px 35px -26px #000;
  backdrop-filter: blur(14px);
}

.ai-preview-page i {
  display: block;
  height: 58px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #668b78;
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 18%);
}

.ai-preview-page strong {
  font-size: 11px;
}

.ai-preview-page p {
  margin: 4px 0 10px;
  color: rgb(255 255 255 / 52%);
  font-size: 8px;
}

.ai-preview-page > div {
  display: flex;
  gap: 5px;
}

.ai-preview-page > div span {
  height: 16px;
  flex: 1;
  border-radius: 5px;
  background: rgb(255 255 255 / 10%);
}

.ai-preview-page--small {
  width: 82%;
  margin: -6px 0 0 auto;
  transform: rotate(2deg);
}

.ai-preview-page--small i {
  height: 34px;
}

.templates-ai-builder__preview ul {
  display: grid;
  gap: 6px;
  margin: 14px 0 0;
  padding: 0;
  color: rgb(255 255 255 / 66%);
  font-size: 8px;
  list-style: none;
}

.templates-ai-builder__preview li {
  display: flex;
  align-items: center;
  gap: 6px;
}

.templates-list-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.templates-list-heading h2 {
  margin: 0;
  font-size: 17px;
}

.templates-list-heading p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 10px;
}

.templates-list-heading > span {
  padding: 5px 8px;
  border-radius: 999px;
  color: var(--primary);
  background: var(--primary-soft);
  font-size: 8px;
  font-weight: 750;
}

.eyebrow {
  margin: 0;
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--ink-400, #98a2b3);
}

.delete-template-confirm {
  display: flex;
  align-items: flex-start;
  gap: 11px;
}

.delete-template-confirm > span {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border-radius: 11px;
  color: #d92d20;
  background: #fef2f2;
  place-items: center;
}

.delete-template-confirm strong {
  color: var(--ink-900);
  font-size: 12px;
}

.delete-template-confirm p {
  margin-top: 5px;
  color: var(--ink-500);
  font-size: 10px;
  line-height: 1.65;
}

.template-delete-confirm {
  min-height: 34px;
  padding: 0 13px;
  border: 0;
  border-radius: 9px;
  color: #fff;
  background: #d92d20;
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 980px) {
  .templates-page {
    padding: 28px 22px 48px;
  }

  .templates-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .templates-ai-builder {
    grid-template-columns: 1fr;
  }

  .templates-ai-builder__preview {
    display: none;
  }
}

@media (max-width: 640px) {
  .templates-page {
    padding: 24px 18px 42px;
  }

  .templates-head {
    align-items: stretch;
  }

  .templates-head .app-button {
    width: 100%;
  }

  .templates-grid {
    grid-template-columns: 1fr;
  }

  .templates-ai-builder {
    padding: 22px 18px;
  }

  .templates-ai-examples {
    display: grid;
  }

  .templates-ai-examples button {
    text-align: left;
  }
}
</style>
