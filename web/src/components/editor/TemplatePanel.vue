<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import {
  loadTemplateCatalog,
  saveCustomTemplate,
  type SaveCustomTemplateInput,
  type TemplateCatalogItem,
  type TemplateKind,
} from '@/services/templates'
import { useEditorStore } from '@/stores/editor'

const emit = defineEmits<{ use: [id: string]; openCreate: [] }>()
const store = useEditorStore()
const toast = useToast()
const templates = ref<TemplateCatalogItem[]>([])
const activeKind = ref<TemplateKind | 'all'>('all')
const visibleTemplates = computed(() =>
  activeKind.value === 'all' ? templates.value : templates.value.filter((item) => item.kind === activeKind.value)
)
const saveOpen = ref(false)
const saving = ref(false)
const templateName = ref('')
const templateCategory = ref<SaveCustomTemplateInput['category']>('recruitment')
const templateDescription = ref('')

async function load(): Promise<void> {
  templates.value = await loadTemplateCatalog()
}

function openSave(): void {
  templateName.value = `${store.doc.title}模板`.slice(0, 30)
  templateDescription.value = '从当前心晴活动页面保存，可继续用于新的心理活动。'
  templateCategory.value = guessCategory(store.doc.title)
  saveOpen.value = true
}

function guessCategory(title: string): string {
  if (/投票|评选|人气/.test(title)) return 'vote'
  if (/打卡|阅读|挑战/.test(title)) return 'checkin'
  if (/报名|讲座|比赛|夜跑/.test(title)) return 'registration'
  return 'recruitment'
}

async function save(): Promise<void> {
  if (saving.value) return
  saving.value = true
  try {
    saveCustomTemplate({
      name: templateName.value,
      category: templateCategory.value,
      description: templateDescription.value,
      document: store.doc,
    })
    await load()
    saveOpen.value = false
    toast.pushToast('当前页面已保存到「我的心晴模板」', 'success')
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '模板保存失败', 'error')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="tpl scroll-y">
    <button type="button" class="tpl__ai" @click="emit('openCreate')">
      <span class="tpl__ai-icon"><Icon name="spark" :size="18" /></span>
      <span class="tpl__ai-text">
        <strong>让 AI 根据需求生成</strong>
        <em>说清活动对象、参与方式和隐私范围</em>
      </span>
      <Icon name="chevron" :size="14" class="tpl__ai-arrow" />
    </button>

    <button type="button" class="tpl__save" @click="openSave">
      <span><Icon name="copy" :size="15" /></span>
      <div><strong>保存为心晴模板</strong><small>保留页面、组件和样式，之后可以复用</small></div>
      <Icon name="arrow-right" :size="14" />
    </button>

    <p class="tpl__title">
      心晴模板<span>{{ templates.length }}</span>
    </p>

    <div class="tpl__kinds">
      <button type="button" :class="{ 'is-active': activeKind === 'all' }" @click="activeKind = 'all'">全部</button>
      <button type="button" :class="{ 'is-active': activeKind === 'wellbeing' }" @click="activeKind = 'wellbeing'">
        心理活动
      </button>
      <button type="button" :class="{ 'is-active': activeKind === 'career' }" @click="activeKind = 'career'">
        生涯发展
      </button>
    </div>

    <article v-for="template in visibleTemplates" :key="template.id" class="tcard">
      <div
        class="tcard__cover"
        :style="{
          background: `linear-gradient(135deg, ${template.gradient[0]}, ${template.gradient[1]})`,
        }"
      >
        <Icon :name="template.icon" :size="22" />
        <span class="tcard__scene">{{ template.scene }}</span>
        <span v-if="template.source === 'custom'" class="tcard__custom">我的心晴模板</span>
      </div>
      <div class="tcard__body">
        <p class="tcard__name">{{ template.name }}</p>
        <p class="tcard__tagline">{{ template.tagline }}</p>
        <button type="button" class="btn btn--sm tcard__btn" @click="emit('use', template.id)">用这个模板</button>
      </div>
    </article>

    <p class="tpl__hint">套用模板会替换当前画布内容，可以用 Ctrl + Z 撤回。</p>

    <BaseModal
      :open="saveOpen"
      title="保存为我的心晴模板"
      subtitle="保存当前页面结构、组件内容和样式，下次可直接复用于心理活动。"
      :width="520"
      @close="saveOpen = false"
    >
      <div class="save-template-form">
        <label>
          <span>模板名称</span>
          <input v-model="templateName" maxlength="30" placeholder="例如：心理协会招新标准模板" />
        </label>
        <label>
          <span>模板分类</span>
          <select v-model="templateCategory">
            <option value="recruitment">心理社团招新</option>
            <option value="registration">心晴活动</option>
            <option value="vote">心理知识问答</option>
            <option value="checkin">自我关怀打卡</option>
            <option value="other">其他心理活动</option>
          </select>
        </label>
        <label>
          <span>模板说明</span>
          <textarea
            v-model="templateDescription"
            rows="3"
            maxlength="120"
            placeholder="简单说明这个模板适合哪类心理活动"
          />
        </label>
        <div class="save-template-summary">
          <Icon name="layers" :size="16" />
          <span>{{ store.doc.pages.length }} 个页面</span>
          <span>{{ store.doc.pages.reduce((total, page) => total + page.blocks.length, 0) }} 个组件</span>
          <span>同名模板会自动更新</span>
        </div>
      </div>
      <template #footer>
        <button class="btn btn--sm" type="button" @click="saveOpen = false">取消</button>
        <button class="btn btn--sm btn--primary" type="button" :disabled="saving" @click="save">
          <Icon name="check" :size="13" />{{ saving ? '保存中…' : '保存模板' }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.tpl {
  height: 100%;
  padding: 14px;
}

.tpl__ai {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid var(--brand-100);
  border-radius: 13px;
  background: linear-gradient(135deg, var(--brand-50), #f5f3ff);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    transform 0.15s ease;
}

.tpl__ai:hover {
  border-color: var(--brand-300);
  transform: translateY(-1px);
}

.tpl__ai-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: 10px;
  background: #fff;
  color: var(--brand-600);
  box-shadow: var(--shadow-sm);
}

.tpl__ai-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tpl__ai-text strong {
  font-size: 12.5px;
  color: var(--ink-800);
}

.tpl__ai-text em {
  font-style: normal;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--ink-500);
}

.tpl__ai-arrow {
  color: var(--brand-400);
  transform: rotate(-90deg);
}

.tpl__save {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 9px;
  margin-top: 8px;
  padding: 10px 11px;
  border: 1px dashed var(--line-strong);
  border-radius: 11px;
  color: var(--ink-600);
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.tpl__save:hover {
  border-color: var(--brand-300);
  background: var(--brand-50);
}

.tpl__save > span {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  border-radius: 8px;
  color: var(--brand-600);
  background: var(--brand-50);
  place-items: center;
}

.tpl__save > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.tpl__save strong {
  font-size: 10.5px;
}

.tpl__save small {
  color: var(--ink-400);
  font-size: 8px;
}

.tpl__save > svg {
  color: var(--ink-300);
}

.tpl__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 16px 0 9px;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--ink-400);
}

.tpl__kinds {
  display: grid;
  gap: 4px;
  margin-bottom: 10px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--ink-50);
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.tpl__kinds button {
  min-height: 28px;
  border: 0;
  border-radius: 7px;
  color: var(--ink-500);
  background: transparent;
  font-size: 9px;
  cursor: pointer;
}

.tpl__kinds button.is-active {
  color: var(--brand-700);
  background: #fff;
  box-shadow: var(--shadow-sm);
}

.tpl__title span {
  padding: 0 5px;
  border-radius: 99px;
  background: var(--ink-100);
}

.tcard {
  overflow: hidden;
  margin-bottom: 10px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.tcard:hover {
  border-color: var(--brand-200);
  box-shadow: 0 10px 26px -20px rgba(79, 70, 229, 0.8);
}

.tcard__cover {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 62px;
  padding: 0 13px;
  color: #fff;
}

.tcard__scene {
  padding: 3px 8px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.2);
  font-size: 10px;
  font-weight: 600;
  backdrop-filter: blur(6px);
}

.tcard__custom {
  position: absolute;
  right: 10px;
  bottom: 9px;
  padding: 2px 6px;
  border-radius: 99px;
  color: #fff;
  background: rgb(0 0 0 / 18%);
  font-size: 7px;
  font-weight: 700;
  backdrop-filter: blur(5px);
}

.tcard__cover {
  position: relative;
}

.tcard__body {
  padding: 11px 13px 12px;
}

.tcard__name {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink-800);
}

.tcard__tagline {
  margin-top: 3px;
  font-size: 11px;
  color: var(--ink-500);
}

.tcard__btn {
  width: 100%;
  margin-top: 10px;
}

.tpl__hint {
  margin-top: 4px;
  font-size: 10.5px;
  line-height: 1.6;
  color: var(--ink-400);
}

.save-template-form {
  display: grid;
  gap: 13px;
}

.save-template-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.save-template-form label > span {
  color: var(--ink-600);
  font-size: 10.5px;
  font-weight: 650;
}

.save-template-form input,
.save-template-form select,
.save-template-form textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 9px;
  outline: 0;
  color: var(--ink-800);
  background: #fff;
  font: inherit;
  font-size: 11px;
}

.save-template-form input:focus,
.save-template-form select:focus,
.save-template-form textarea:focus {
  border-color: var(--brand-300);
  box-shadow: 0 0 0 3px var(--brand-50);
}

.save-template-form textarea {
  line-height: 1.6;
  resize: vertical;
}

.save-template-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 9px;
  color: var(--ink-500);
  background: var(--ink-50);
  font-size: 9px;
}

.save-template-summary span {
  padding-right: 8px;
  border-right: 1px solid var(--line);
}

.save-template-summary span:last-child {
  border-right: 0;
}
</style>
