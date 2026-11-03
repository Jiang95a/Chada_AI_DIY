<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useEditorStore } from '@/stores/editor'
import type { ButtonAction, FieldDef, FormField, InfoItem, PageRole, Theme } from '@/types/editor'
import { fieldsFor, metaOf, THEME_PRESETS } from '@/data/blockCatalog'
import { randomTheme } from '@/data/templates'
import Icon from '@/components/ui/Icon.vue'
import FormFieldsEditor from './fields/FormFieldsEditor.vue'
import InfoItemsEditor from './fields/InfoItemsEditor.vue'
import ActionEditor from './fields/ActionEditor.vue'
import PrivacySettingsPanel from './PrivacySettingsPanel.vue'

const props = defineProps<{ tab: 'props' | 'style' }>()

const store = useEditorStore()
const { doc } = storeToRefs(store)

const block = computed(() => store.selectedBlock)
const fields = computed<FieldDef[]>(() => (block.value ? fieldsFor(block.value.type) : []))
const theme = computed(() => doc.value.theme)
const page = computed(() => store.currentPage)

const ALIGN_OPTIONS = [
  { value: 'left', label: '左' },
  { value: 'center', label: '中' },
  { value: 'right', label: '右' },
]

function str(value: unknown): string {
  if (value === undefined || value === null) return ''
  return typeof value === 'string' ? value : String(value)
}

function setProp(key: string, value: unknown, immediate = false): void {
  if (!block.value) return
  store.updateProps(block.value.id, { [key]: value }, immediate)
}

function onText(key: string, event: Event): void {
  setProp(key, (event.target as HTMLInputElement).value)
}

function onNumber(key: string, event: Event): void {
  const value = Number((event.target as HTMLInputElement).value)
  setProp(key, Number.isFinite(value) ? value : 0)
}

function onSwitch(key: string): void {
  setProp(key, !block.value?.props[key])
}

function onSelect(key: string, event: Event): void {
  setProp(key, (event.target as HTMLSelectElement).value)
}

function onLines(key: string, event: Event): void {
  setProp(
    key,
    (event.target as HTMLTextAreaElement).value
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
  )
}

function fieldsOf(value: { props: Record<string, any> }): FormField[] {
  return Array.isArray(value.props.fields) ? (value.props.fields as FormField[]) : []
}

function itemsOf(value: { props: Record<string, any> }): InfoItem[] {
  return Array.isArray(value.props.items) ? (value.props.items as InfoItem[]) : []
}

/* ------------------------------- 主题 ------------------------------- */

function onThemeColor(key: 'primary' | 'accent' | 'bg' | 'text', event: Event): void {
  store.updateTheme({ [key]: (event.target as HTMLInputElement).value })
}

function onThemeNumber(key: 'radius' | 'fontScale' | 'spacing', event: Event): void {
  store.updateTheme({ [key]: Number((event.target as HTMLInputElement).value) }, true)
}

function applyPreset(presetTheme: Partial<Theme>): void {
  store.updateTheme(presetTheme, true)
}

function shuffleTheme(): void {
  store.updateTheme(randomTheme(store.doc.theme), true)
}

function onPageRole(event: Event): void {
  if (!page.value) return
  store.setPageRole(page.value.id, (event.target as HTMLSelectElement).value as PageRole)
}
</script>

<template>
  <div class="insp scroll-y">
    <!-- 选中组件 -->
    <template v-if="block">
      <header class="insp__head">
        <span class="insp__icon"><Icon :name="metaOf(block.type).icon" :size="15" /></span>
        <div class="insp__titles">
          <p class="insp__name">{{ metaOf(block.type).label }}</p>
          <p class="insp__desc">{{ metaOf(block.type).desc }}</p>
        </div>
        <div class="insp__acts">
          <button type="button" class="icon-btn icon-btn--sm" title="复制" @click="store.duplicateBlock(block.id)">
            <Icon name="copy" :size="13" />
          </button>
          <button
            type="button"
            class="icon-btn icon-btn--sm"
            :title="block.hidden ? '取消隐藏' : '隐藏'"
            @click="store.toggleHidden(block.id)"
          >
            <Icon :name="block.hidden ? 'eye-off' : 'eye'" :size="13" />
          </button>
          <button
            type="button"
            class="icon-btn icon-btn--sm icon-btn--danger"
            title="删除"
            @click="store.removeBlock(block.id)"
          >
            <Icon name="trash" :size="13" />
          </button>
        </div>
      </header>

      <div v-if="tab === 'props'" class="insp__body">
        <template v-for="field in fields" :key="field.key">
          <label v-if="field.kind === 'text'" class="field">
            <span class="field__label">{{ field.label }}</span>
            <input
              class="input"
              :value="str(block.props[field.key])"
              :placeholder="field.placeholder"
              @input="onText(field.key, $event)"
            />
          </label>

          <label v-else-if="field.kind === 'textarea'" class="field">
            <span class="field__label">{{ field.label }}</span>
            <textarea
              class="textarea"
              rows="4"
              :value="str(block.props[field.key])"
              :placeholder="field.placeholder"
              @input="onText(field.key, $event)"
            />
          </label>

          <div v-else-if="field.kind === 'number'" class="field">
            <span class="field__label">
              {{ field.label }}
              <em v-if="field.unit" class="insp__unit">{{ field.unit }}</em>
            </span>
            <input
              class="input"
              type="number"
              :min="field.min"
              :max="field.max"
              :step="field.step"
              :value="Number(block.props[field.key]) || 0"
              @input="onNumber(field.key, $event)"
            />
          </div>

          <div v-else-if="field.kind === 'color'" class="field">
            <span class="field__label">{{ field.label }}</span>
            <div class="field__row">
              <input
                type="color"
                class="swatch"
                :value="str(block.props[field.key]) || '#ffffff'"
                @input="onText(field.key, $event)"
              />
              <input
                class="input"
                :value="str(block.props[field.key])"
                placeholder="留空用主题色"
                @input="onText(field.key, $event)"
              />
            </div>
          </div>

          <div v-else-if="field.kind === 'switch'" class="field field--switch">
            <span class="field__label">{{ field.label }}</span>
            <button
              type="button"
              class="toggle"
              :class="{ 'is-on': Boolean(block.props[field.key]) }"
              :aria-pressed="Boolean(block.props[field.key])"
              @click="onSwitch(field.key)"
            >
              <span class="toggle__dot" />
            </button>
          </div>

          <label v-else-if="field.kind === 'select'" class="field">
            <span class="field__label">{{ field.label }}</span>
            <select class="select" :value="str(block.props[field.key])" @change="onSelect(field.key, $event)">
              <option v-for="option in field.options" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-else-if="field.kind === 'lines'" class="field">
            <span class="field__label">{{ field.label }}</span>
            <textarea
              class="textarea"
              rows="4"
              :value="(block.props[field.key] || []).join('\n')"
              :placeholder="field.placeholder"
              @input="onLines(field.key, $event)"
            />
            <span v-if="field.hint" class="hint">{{ field.hint }}</span>
          </label>

          <div v-else-if="field.kind === 'fields'" class="field">
            <span class="field__label"
              >{{ field.label }}<em class="insp__unit">{{ fieldsOf(block).length }} 项</em></span
            >
            <FormFieldsEditor :model-value="fieldsOf(block)" @update:model-value="setProp('fields', $event, true)" />
          </div>

          <div v-else-if="field.kind === 'items'" class="field">
            <span class="field__label">{{ field.label }}</span>
            <InfoItemsEditor :model-value="itemsOf(block)" @update:model-value="setProp('items', $event, true)" />
          </div>

          <div v-else-if="field.kind === 'action'" class="field">
            <span class="field__label">{{ field.label }}</span>
            <ActionEditor
              :model-value="block.props.action as ButtonAction"
              :pages="doc.pages"
              @update:model-value="setProp('action', $event, true)"
            />
          </div>
        </template>

        <div class="insp__section">
          <p class="insp__sec-title">组件外框</p>
          <div class="insp__grid2">
            <label class="field">
              <span class="field__label">背景</span>
              <div class="field__row">
                <input
                  type="color"
                  class="swatch"
                  :value="block.style.bg || '#ffffff'"
                  @input="
                    store.updateBlockStyle(block.id, {
                      bg: ($event.target as HTMLInputElement).value,
                    })
                  "
                />
                <button
                  type="button"
                  class="btn btn--sm btn--ghost"
                  @click="store.updateBlockStyle(block.id, { bg: '' }, true)"
                >
                  透明
                </button>
              </div>
            </label>
            <label class="field">
              <span class="field__label">圆角 {{ block.style.radius }}px</span>
              <input
                type="range"
                min="0"
                max="32"
                step="1"
                :value="block.style.radius"
                class="range"
                @input="
                  store.updateBlockStyle(block.id, {
                    radius: Number(($event.target as HTMLInputElement).value),
                  })
                "
              />
            </label>
            <label class="field">
              <span class="field__label">左右留白 {{ block.style.paddingX }}</span>
              <input
                type="range"
                min="0"
                max="40"
                step="2"
                :value="block.style.paddingX"
                class="range"
                @input="
                  store.updateBlockStyle(block.id, {
                    paddingX: Number(($event.target as HTMLInputElement).value),
                  })
                "
              />
            </label>
            <label class="field">
              <span class="field__label">上下留白 {{ block.style.paddingY }}</span>
              <input
                type="range"
                min="0"
                max="40"
                step="2"
                :value="block.style.paddingY"
                class="range"
                @input="
                  store.updateBlockStyle(block.id, {
                    paddingY: Number(($event.target as HTMLInputElement).value),
                  })
                "
              />
            </label>
          </div>
          <div class="field">
            <span class="field__label">对齐</span>
            <div class="seg insp__seg">
              <button
                v-for="option in ALIGN_OPTIONS"
                :key="option.value"
                type="button"
                class="seg__item"
                :class="{ 'is-active': block.style.align === option.value }"
                @click="store.updateBlockStyle(block.id, { align: option.value as 'left' | 'center' | 'right' }, true)"
              >
                {{ option.label }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="insp__body">
        <div class="insp__section">
          <p class="insp__sec-title">配色</p>
          <div class="presets">
            <button
              v-for="preset in THEME_PRESETS"
              :key="preset.id"
              type="button"
              class="preset"
              :class="{ 'is-active': preset.theme.primary === theme.primary }"
              :title="preset.name"
              :style="{
                background: `linear-gradient(135deg, ${preset.theme.primary}, ${preset.theme.accent})`,
              }"
              @click="applyPreset(preset.theme)"
            />
            <button type="button" class="preset preset--shuffle" title="随机换一套" @click="shuffleTheme">
              <Icon name="refresh" :size="13" />
            </button>
          </div>
          <div class="insp__grid2">
            <label class="field">
              <span class="field__label">主色</span>
              <div class="field__row">
                <input type="color" class="swatch" :value="theme.primary" @input="onThemeColor('primary', $event)" />
                <input class="input" :value="theme.primary" @input="onThemeColor('primary', $event)" />
              </div>
            </label>
            <label class="field">
              <span class="field__label">辅助色</span>
              <div class="field__row">
                <input type="color" class="swatch" :value="theme.accent" @input="onThemeColor('accent', $event)" />
                <input class="input" :value="theme.accent" @input="onThemeColor('accent', $event)" />
              </div>
            </label>
            <label class="field">
              <span class="field__label">页面底色</span>
              <div class="field__row">
                <input type="color" class="swatch" :value="theme.bg" @input="onThemeColor('bg', $event)" />
                <input class="input" :value="theme.bg" @input="onThemeColor('bg', $event)" />
              </div>
            </label>
            <label class="field">
              <span class="field__label">文字色</span>
              <div class="field__row">
                <input type="color" class="swatch" :value="theme.text" @input="onThemeColor('text', $event)" />
                <input class="input" :value="theme.text" @input="onThemeColor('text', $event)" />
              </div>
            </label>
          </div>
        </div>

        <div class="insp__section">
          <p class="insp__sec-title">排版</p>
          <label class="field">
            <span class="field__label">圆角 {{ theme.radius }}px</span>
            <input
              type="range"
              min="0"
              max="28"
              step="1"
              :value="theme.radius"
              class="range"
              @input="onThemeNumber('radius', $event)"
            />
          </label>
          <label class="field">
            <span class="field__label">字号倍率 {{ theme.fontScale.toFixed(2) }}</span>
            <input
              type="range"
              min="0.9"
              max="1.2"
              step="0.02"
              :value="theme.fontScale"
              class="range"
              @input="onThemeNumber('fontScale', $event)"
            />
          </label>
          <label class="field">
            <span class="field__label">间距倍率 {{ theme.spacing.toFixed(2) }}</span>
            <input
              type="range"
              min="0.8"
              max="1.6"
              step="0.05"
              :value="theme.spacing"
              class="range"
              @input="onThemeNumber('spacing', $event)"
            />
          </label>
        </div>

        <div class="insp__section">
          <p class="insp__sec-title">按钮</p>
          <div class="seg insp__seg">
            <button
              v-for="option in [
                { value: 'solid', label: '实心' },
                { value: 'gradient', label: '渐变' },
                { value: 'outline', label: '描边' },
              ]"
              :key="option.value"
              type="button"
              class="seg__item"
              :class="{ 'is-active': theme.buttonStyle === option.value }"
              @click="store.updateTheme({ buttonStyle: option.value as Theme['buttonStyle'] }, true)"
            >
              {{ option.label }}
            </button>
          </div>
          <label class="field">
            <span class="field__label">字体</span>
            <select
              class="select"
              :value="theme.font"
              @change="store.updateTheme({ font: ($event.target as HTMLSelectElement).value as Theme['font'] }, true)"
            >
              <option value="sans">无衬线（默认）</option>
              <option value="serif">衬线（文艺）</option>
              <option value="round">圆润（亲和）</option>
            </select>
          </label>
        </div>
      </div>
    </template>

    <!-- 没选中：页面设置 -->
    <div v-else class="insp__body">
      <div class="insp__empty">
        <span class="insp__empty-icon"><Icon name="cursor" :size="17" /></span>
        <p>点画布里的组件，就能改它的文案和样式</p>
      </div>

      <div class="insp__section">
        <p class="insp__sec-title">当前页面</p>
        <label class="field">
          <span class="field__label">页面名称</span>
          <input
            class="input"
            :value="page?.name"
            @input="page && store.renamePage(page.id, ($event.target as HTMLInputElement).value)"
          />
        </label>
        <label class="field">
          <span class="field__label">页面角色</span>
          <select class="select" :value="page?.role" @change="onPageRole">
            <option value="home">首页</option>
            <option value="content">内容页</option>
            <option value="form">表单页</option>
            <option value="success">成功页</option>
          </select>
        </label>
        <p class="hint">AI 生成和「提交后跳转」都按角色找页面。</p>
      </div>

      <div class="insp__section">
        <p class="insp__sec-title">活动名称</p>
        <input class="input" :value="doc.title" @input="store.updateTitle(($event.target as HTMLInputElement).value)" />
        <p class="hint">会出现在浏览器标题、分享海报和二维码落地页。</p>
      </div>

      <PrivacySettingsPanel :project-id="doc.id" :slug="doc.slug" :organizer="doc.meta.org" />

      <div class="insp__section">
        <p class="insp__sec-title">快捷入口</p>
        <div class="insp__quick">
          <button type="button" class="btn btn--sm" @click="store.loadDemo()">
            <Icon name="spark" :size="12" /> 载入示例
          </button>
          <button type="button" class="btn btn--sm" @click="store.startBlank()">
            <Icon name="file" :size="12" /> 空白页
          </button>
        </div>
        <p class="hint">示例文档包含四页完整流程，适合演示。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.insp {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.insp__head {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 13px 14px 12px;
  border-bottom: 1px solid var(--line);
}

.insp__icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border-radius: 9px;
  background: var(--brand-50);
  color: var(--brand-600);
}

.insp__titles {
  flex: 1;
  min-width: 0;
}

.insp__name {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ink-800);
}

.insp__desc {
  font-size: 10.5px;
  color: var(--ink-400);
}

.insp__acts {
  display: flex;
  gap: 1px;
}

.insp__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 11px;
  padding: 13px 14px 22px;
}

.insp__body :deep(.field + .field) {
  margin-top: 0;
}

.insp__body :deep(.field) {
  gap: 5px;
}

.insp__section {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 11px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fcfcfe;
}

.insp__sec-title {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--ink-400);
}

.insp__grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.insp__unit {
  font-style: normal;
  font-weight: 500;
  color: var(--ink-400);
}

.insp__seg {
  width: 100%;
}

.insp__seg .seg__item {
  flex: 1;
  text-align: center;
}

.field--switch {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.toggle {
  position: relative;
  width: 34px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 99px;
  background: var(--ink-200);
  cursor: pointer;
  transition: background 0.18s ease;
  flex: 0 0 auto;
}

.toggle.is-on {
  background: var(--brand-600);
}

.toggle__dot {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.3);
  transition: transform 0.18s cubic-bezier(0.22, 1, 0.36, 1);
}

.toggle.is-on .toggle__dot {
  transform: translateX(14px);
}

.swatch {
  width: 32px;
  height: 30px;
  flex: 0 0 auto;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.range {
  width: 100%;
  height: 18px;
  accent-color: var(--brand-600);
  cursor: pointer;
}

.presets {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}

.preset {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 9px;
  border: 2px solid transparent;
  cursor: pointer;
  transition:
    transform 0.14s ease,
    box-shadow 0.14s ease;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.14);
}

.preset:hover {
  transform: translateY(-1px);
}

.preset.is-active {
  border-color: #fff;
  box-shadow: 0 0 0 2px var(--brand-300);
}

.preset--shuffle {
  display: grid;
  place-items: center;
  background: var(--ink-100);
  color: var(--ink-500);
  border: 1px dashed var(--ink-300);
}

.insp__empty {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px;
  border: 1px dashed var(--line-strong);
  border-radius: 12px;
  color: var(--ink-500);
  font-size: 11.5px;
  line-height: 1.6;
}

.insp__empty-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  border-radius: 9px;
  background: var(--brand-50);
  color: var(--brand-600);
}

.insp__quick {
  display: flex;
  gap: 6px;
}
</style>
