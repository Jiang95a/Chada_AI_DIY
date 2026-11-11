<script setup lang="ts">
import { computed, provide, reactive, ref } from 'vue'
import type { Block, BlockType, Page, Theme } from '@/types/editor'
import BlockView from './BlockView.vue'
import Icon from '@/components/ui/Icon.vue'
import { FORM_CONTEXT } from '@/composables/formContext'
import { metaOf } from '@/data/blockCatalog'
import { themeCssVars } from '@/utils/theme'

const props = withDefaults(
  defineProps<{
    page: Page
    theme: Theme
    pages?: Page[]
    mode?: 'edit' | 'preview'
    selectedId?: string | null
    width?: number
    shareUrl?: string
  }>(),
  { mode: 'edit', pages: () => [], selectedId: null, width: 375, shareUrl: '' }
)

const emit = defineEmits<{
  select: [id: string | null]
  insert: [type: BlockType, index: number]
  move: [id: string, index: number]
  moveBy: [id: string, delta: number]
  remove: [id: string]
  duplicate: [id: string]
  toggleHidden: [id: string]
  navigate: [pageId: string]
  submit: [answers: Record<string, string | string[]>]
  invalid: []
  requestPalette: []
}>()

const isEdit = computed(() => props.mode === 'edit')
const screenHeight = computed(() => Math.round(props.width * 2.166))
const vars = computed(() => themeCssVars(props.theme))

/* ------------------------------- 表单上下文 ------------------------------- */

const answers = reactive<Record<string, string | string[]>>({})
const errors = reactive<Record<string, boolean>>({})

function setAnswer(key: string, value: string | string[]): void {
  answers[key] = value
  if (errors[key]) delete errors[key]
}

function clearError(key: string): void {
  if (errors[key]) delete errors[key]
}

provide(FORM_CONTEXT, { answers, errors, setAnswer, clearError })

const pageEl = ref<HTMLElement | null>(null)

function isEmpty(value: string | string[] | undefined): boolean {
  if (value === undefined) return true
  if (Array.isArray(value)) return value.length === 0
  return !value.trim()
}

function validate(): boolean {
  let ok = true
  for (const block of props.page.blocks) {
    if (block.hidden) continue
    if (block.type === 'form') {
      const fields = Array.isArray(block.props.fields) ? block.props.fields : []
      for (const field of fields) {
        if (!field.required) continue
        const key = `${block.id}:${field.id}`
        if (isEmpty(answers[key])) {
          errors[key] = true
          ok = false
        }
      }
    }
    if ((block.type === 'radio' || block.type === 'checkbox') && block.props.required) {
      if (isEmpty(answers[block.id])) {
        errors[block.id] = true
        ok = false
      }
    }
  }
  return ok
}

function submit(): void {
  if (!validate()) {
    emit('invalid')
    return
  }
  emit('submit', { ...answers })
}

function onAction(payload: any): void {
  if (!payload) return
  if (payload.type === 'url' && payload.url) {
    window.open(String(payload.url), '_blank', 'noopener')
    return
  }
  if (payload.type === 'page') {
    const target =
      props.pages.find((page) => page.id === payload.pageId) ??
      props.pages.find((page) => page.role === payload.targetRole)
    if (target) emit('navigate', target.id)
    return
  }
  if (payload.type === 'submit') submit()
}

/* ------------------------------- 拖拽 ------------------------------- */

const dragId = ref<string | null>(null)
const dropIndex = ref<number | null>(null)

function onDragStart(event: DragEvent, block: Block): void {
  if (!isEdit.value) return
  dragId.value = block.id
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('application/x-block-id', block.id)
    // 某些浏览器只认 text/plain
    event.dataTransfer.setData('text/plain', block.id)
  }
  emit('select', block.id)
}

function onDragEnd(): void {
  dragId.value = null
  dropIndex.value = null
}

function onDragOver(event: DragEvent, index: number): void {
  if (!isEdit.value) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dropIndex.value = index
}

function onDragLeave(index: number): void {
  if (dropIndex.value === index) dropIndex.value = null
}

function onDrop(event: DragEvent, index: number): void {
  if (!isEdit.value) return
  event.preventDefault()
  event.stopPropagation()
  const type = event.dataTransfer?.getData('application/x-block-type')
  const id = event.dataTransfer?.getData('application/x-block-id')
  dropIndex.value = null
  dragId.value = null

  if (type) {
    emit('insert', type as BlockType, index)
    return
  }
  if (id) emit('move', id, index)
}

function select(id: string | null): void {
  if (!isEdit.value) return
  emit('select', id)
}

defineExpose({ pageEl, submit })
</script>

<template>
  <div class="phone" :style="{ width: `${width}px` }">
    <div class="phone__frame">
      <div class="screen" :style="[vars, { height: `${screenHeight}px` }]">
        <div class="screen__notch" aria-hidden="true" />
        <div class="screen__status">
          <span>9:41</span>
          <span class="screen__status-right">
            <i class="bar" /><i class="bar" /><i class="bar" /><i class="battery" />
          </span>
        </div>

        <div class="screen__body scroll-y" :class="{ 'is-edit': isEdit }">
          <div
            ref="pageEl"
            class="page"
            :style="{ background: page.bg || 'var(--page-bg)', minHeight: '100%' }"
            @click.self="select(null)"
          >
            <div v-for="(block, index) in page.blocks" :key="block.id" class="slot-wrap">
              <div
                v-if="isEdit"
                class="dropzone"
                :class="{ 'is-active': dropIndex === index }"
                @dragover="onDragOver($event, index)"
                @dragleave="onDragLeave(index)"
                @drop="onDrop($event, index)"
              >
                <span class="dropzone__line" />
              </div>

              <div
                v-if="isEdit"
                class="slot"
                :class="{
                  'is-selected': selectedId === block.id,
                  'is-dragging': dragId === block.id,
                  'is-hidden': block.hidden,
                  'slot--first': index === 0,
                }"
                :draggable="true"
                @dragstart="onDragStart($event, block)"
                @dragend="onDragEnd"
                @dragover="onDragOver($event, index)"
                @drop="onDrop($event, index)"
                @click.stop="select(block.id)"
              >
                <span class="slot__badge">
                  <Icon :name="metaOf(block.type).icon" :size="12" />
                  {{ metaOf(block.type).label }}
                </span>
                <div class="slot__toolbar" @click.stop>
                  <button
                    type="button"
                    class="icon-btn icon-btn--sm"
                    title="上移"
                    :disabled="index === 0"
                    @click="emit('moveBy', block.id, -1)"
                  >
                    <Icon name="up" :size="13" />
                  </button>
                  <button
                    type="button"
                    class="icon-btn icon-btn--sm"
                    title="下移"
                    :disabled="index === page.blocks.length - 1"
                    @click="emit('moveBy', block.id, 1)"
                  >
                    <Icon name="down" :size="13" />
                  </button>
                  <button
                    type="button"
                    class="icon-btn icon-btn--sm"
                    :title="block.hidden ? '显示' : '隐藏'"
                    @click="emit('toggleHidden', block.id)"
                  >
                    <Icon :name="block.hidden ? 'eye-off' : 'eye'" :size="13" />
                  </button>
                  <button type="button" class="icon-btn icon-btn--sm" title="复制" @click="emit('duplicate', block.id)">
                    <Icon name="copy" :size="13" />
                  </button>
                  <button
                    type="button"
                    class="icon-btn icon-btn--sm icon-btn--danger"
                    title="删除"
                    @click="emit('remove', block.id)"
                  >
                    <Icon name="trash" :size="13" />
                  </button>
                </div>
                <div class="slot__content">
                  <BlockView :block="block" :theme="theme" :pages="pages" :share-url="shareUrl" mode="edit" />
                </div>
              </div>

              <BlockView
                v-else
                :block="block"
                :theme="theme"
                :pages="pages"
                :share-url="shareUrl"
                mode="preview"
                @action="onAction"
              />
            </div>

            <div
              v-if="isEdit"
              class="dropzone dropzone--tail"
              :class="{ 'is-active': dropIndex === page.blocks.length }"
              @dragover="onDragOver($event, page.blocks.length)"
              @dragleave="onDragLeave(page.blocks.length)"
              @drop="onDrop($event, page.blocks.length)"
            />

            <div v-if="isEdit && !page.blocks.length" class="canvas-empty">
              <Icon name="layers" :size="26" />
              <p class="canvas-empty__title">这页还是空的</p>
              <p class="canvas-empty__hint">从左侧点一个组件，或者让它自己长出来</p>
              <button type="button" class="btn btn--sm" @click="emit('requestPalette')">
                <Icon name="plus" :size="13" /> 打开组件库
              </button>
            </div>

            <button v-if="isEdit" type="button" class="canvas-add" @click="emit('requestPalette')">
              <Icon name="plus" :size="13" /> 添加组件
            </button>
          </div>
        </div>

        <div class="screen__home" aria-hidden="true" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.phone {
  position: relative;
  flex: 0 0 auto;
}

.phone__frame {
  position: relative;
  padding: 10px;
  border-radius: 48px;
  background: linear-gradient(155deg, #2a3040, #0b0e14 55%);
  box-shadow:
    0 40px 80px -40px rgba(15, 23, 42, 0.65),
    0 2px 0 rgba(255, 255, 255, 0.12) inset,
    0 0 0 1px rgba(255, 255, 255, 0.06);
}

.screen {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 38px;
  background: var(--page-bg);
  font-family: var(--page-font);
  color: var(--ink);
}

.screen__notch {
  position: absolute;
  top: 8px;
  left: 50%;
  z-index: 20;
  width: 96px;
  height: 22px;
  transform: translateX(-50%);
  border-radius: 12px;
  background: #0b0e14;
}

.screen__status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 34px;
  padding: 0 22px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--ink);
  background: var(--page-bg);
  flex: 0 0 auto;
}

.screen__status-right {
  display: flex;
  align-items: center;
  gap: 3px;
}

.bar {
  width: 3px;
  border-radius: 2px;
  background: currentColor;
  opacity: 0.8;
}

.bar:nth-child(1) {
  height: 4px;
}

.bar:nth-child(2) {
  height: 6px;
}

.bar:nth-child(3) {
  height: 8px;
}

.battery {
  width: 16px;
  height: 8px;
  margin-left: 3px;
  border: 1px solid currentColor;
  border-radius: 3px;
  opacity: 0.7;
  position: relative;
}

.battery::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 1.5px;
  background: currentColor;
}

.screen__body {
  flex: 1;
  min-height: 0;
  position: relative;
  background: var(--page-bg);
}

.screen__home {
  flex: 0 0 auto;
  height: 16px;
  background: var(--page-bg);
  position: relative;
}

.screen__home::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 5px;
  width: 118px;
  height: 4px;
  transform: translateX(-50%);
  border-radius: 99px;
  background: color-mix(in srgb, var(--ink) 26%, transparent);
}

.page {
  position: relative;
  padding-bottom: 26px;
  transition: background 0.2s ease;
}

/* ------------------------------- 编辑态 ------------------------------- */

.dropzone {
  position: relative;
  height: 12px;
  margin: -6px 0;
  z-index: 6;
}

.dropzone__line {
  position: absolute;
  left: 10px;
  right: 10px;
  top: 50%;
  height: 2px;
  transform: translateY(-50%);
  border-radius: 99px;
  background: var(--brand-500);
  opacity: 0;
  transition: opacity 0.12s ease;
}

.dropzone.is-active .dropzone__line {
  opacity: 1;
}

.dropzone.is-active {
  height: 18px;
  margin: -9px 0;
}

.dropzone--tail {
  height: 22px;
  margin: 0;
}

.slot {
  position: relative;
  cursor: pointer;
  outline: 1px dashed transparent;
  outline-offset: -1px;
  transition:
    outline-color 0.12s ease,
    opacity 0.2s ease;
}

.slot:hover {
  outline-color: rgba(99, 102, 241, 0.5);
}

.slot.is-selected {
  outline: 1.5px solid var(--brand-500);
  outline-offset: -1px;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
  border-radius: 6px;
}

.slot.is-dragging {
  opacity: 0.4;
}

.slot.is-hidden .slot__content {
  opacity: 0.28;
  filter: grayscale(0.6);
}

.slot__content {
  pointer-events: none;
}

.slot__badge {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 7;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 18px;
  padding: 0 6px;
  border-radius: 6px 0 6px 0;
  background: var(--brand-500);
  color: #fff;
  font-family: var(--font);
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
  opacity: 0;
  /* 浮在组件上方，避免遮住组件自己的内容；第一个组件没有上方空间，退回内部 */
  transform: translateY(-100%);
  transition: opacity 0.12s ease;
  pointer-events: none;
}

.slot__toolbar {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 8;
  display: flex;
  gap: 1px;
  padding: 2px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 6px 18px -8px rgba(15, 23, 42, 0.45);
  opacity: 0;
  transform: translateY(-100%);
  transition: opacity 0.12s ease;
  pointer-events: none;
}

.slot--first .slot__badge,
.slot--first .slot__toolbar {
  transform: none;
}

.slot--first .slot__toolbar {
  top: 2px;
  right: 2px;
}

.slot:hover .slot__badge,
.slot.is-selected .slot__badge {
  opacity: 1;
}

.slot:hover .slot__toolbar,
.slot.is-selected .slot__toolbar {
  opacity: 1;
  pointer-events: auto;
}

.slot:not(.slot--first):hover .slot__toolbar,
.slot:not(.slot--first).is-selected .slot__toolbar {
  transform: translateY(-100%);
}

.slot:not(.slot--first):hover .slot__badge,
.slot:not(.slot--first).is-selected .slot__badge {
  transform: translateY(-100%);
}

.slot__toolbar :deep(svg) {
  color: #475467;
}

.canvas-add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: calc(100% - 32px);
  margin: 4px 16px 10px;
  height: 38px;
  border: 1.5px dashed color-mix(in srgb, var(--ink) 18%, transparent);
  border-radius: 12px;
  background: transparent;
  color: color-mix(in srgb, var(--ink) 45%, transparent);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    color 0.15s ease,
    background 0.15s ease;
}

.canvas-add:hover {
  border-color: var(--brand-400);
  color: var(--brand-600);
  background: color-mix(in srgb, var(--brand) 5%, transparent);
}

.canvas-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  margin: 40px 20px 20px;
  padding: 26px 18px;
  border: 1.5px dashed color-mix(in srgb, var(--ink) 14%, transparent);
  border-radius: 16px;
  color: color-mix(in srgb, var(--ink) 40%, transparent);
  text-align: center;
}

.canvas-empty__title {
  font-size: 13px;
  font-weight: 700;
  color: color-mix(in srgb, var(--ink) 62%, transparent);
}

.canvas-empty__hint {
  font-size: 11.5px;
  line-height: 1.6;
  margin-bottom: 4px;
}
</style>
