<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useEditorStore } from '@/stores/editor'
import Icon from '@/components/ui/Icon.vue'
import { formatTime } from '@/utils/misc'

const emit = defineEmits<{
  create: []
  preview: []
  publish: []
  audit: []
  shortcuts: []
  exportDoc: [kind: 'json' | 'html']
  importDoc: [text: string]
}>()

const store = useEditorStore()
const { doc, saving, savedAt, canUndo, canRedo, issueSummary } = storeToRefs(store)

const menuOpen = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const fileRef = ref<HTMLInputElement | null>(null)

const saveLabel = computed(() => {
  if (saving.value) return '保存中…'
  if (savedAt.value) return `已保存 ${formatTime(savedAt.value)}`
  return '未保存'
})

const issueAlert = computed(() => issueSummary.value.error + issueSummary.value.warn)

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

function closeMenu(): void {
  menuOpen.value = false
}

function createFromMenu(): void {
  closeMenu()
  emit('create')
}

function startBlankFromMenu(): void {
  closeMenu()
  store.startBlank()
}

function exportFromMenu(kind: 'json' | 'html'): void {
  closeMenu()
  emit('exportDoc', kind)
}

function shortcutsFromMenu(): void {
  closeMenu()
  emit('shortcuts')
}

function loadDemoFromMenu(): void {
  closeMenu()
  store.loadDemo()
}

function onDocumentClick(event: MouseEvent): void {
  if (!menuOpen.value) return
  const target = event.target as Node | null
  if (menuRef.value && target && !menuRef.value.contains(target)) closeMenu()
}

onMounted(() => document.addEventListener('mousedown', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocumentClick))

function pickImport(): void {
  closeMenu()
  fileRef.value?.click()
}

async function onFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const text = await file.text()
  emit('importDoc', text)
}

function onTitleInput(event: Event): void {
  store.updateTitle((event.target as HTMLInputElement).value)
}
</script>

<template>
  <header class="hd">
    <div class="hd__left">
      <span class="hd__logo"><Icon name="spark" :size="17" /></span>
      <div class="hd__brand">
        <p class="hd__brand-name">心晴页面工坊</p>
        <p class="hd__brand-sub">心屿同行 · 活动创作工具</p>
      </div>
      <span class="hd__sep" />
      <div class="hd__project">
        <input class="hd__title" :value="doc.title" spellcheck="false" @input="onTitleInput" />
        <span class="hd__save" :class="{ 'is-saving': saving }">
          <Icon :name="saving ? 'refresh' : 'cloud'" :size="11" />
          {{ saveLabel }}
        </span>
      </div>
    </div>

    <div class="hd__right">
      <div class="hd__group">
        <button type="button" class="icon-btn" title="撤销 (Ctrl+Z)" :disabled="!canUndo" @click="store.undo()">
          <Icon name="undo" :size="16" />
        </button>
        <button type="button" class="icon-btn" title="重做 (Ctrl+Shift+Z)" :disabled="!canRedo" @click="store.redo()">
          <Icon name="redo" :size="16" />
        </button>
      </div>

      <button type="button" class="btn hd__audit" @click="emit('audit')">
        <Icon name="check-circle" :size="14" />
        质量检查
        <span
          v-if="issueAlert"
          class="hd__dot"
          :class="{ 'is-error': issueSummary.error > 0 }"
          :title="`${issueSummary.error} 个错误 / ${issueSummary.warn} 个提醒`"
        />
      </button>

      <button type="button" class="btn" @click="emit('preview')"><Icon name="phone" :size="14" /> 预览</button>

      <button type="button" class="btn btn--primary" @click="emit('publish')">
        <Icon name="rocket" :size="14" /> 发布
      </button>

      <div ref="menuRef" class="hd__menu">
        <button type="button" class="icon-btn" title="更多" @click="toggleMenu">
          <Icon name="more" :size="16" />
        </button>
        <Transition name="fade">
          <div v-if="menuOpen" class="menu">
            <p class="menu__label">创建</p>
            <button type="button" class="menu__item" @click="createFromMenu">
              <Icon name="wand" :size="14" /> AI 创建 / 选模板
            </button>
            <button type="button" class="menu__item" @click="startBlankFromMenu">
              <Icon name="file" :size="14" /> 新空白项目
            </button>
            <p class="menu__label">文件</p>
            <button type="button" class="menu__item" @click="pickImport">
              <Icon name="upload" :size="14" /> 导入项目 JSON
            </button>
            <button type="button" class="menu__item" @click="exportFromMenu('json')">
              <Icon name="download" :size="14" /> 导出项目 JSON
            </button>
            <button type="button" class="menu__item" @click="exportFromMenu('html')">
              <Icon name="external" :size="14" /> 导出当前页 HTML
            </button>
            <p class="menu__label">其它</p>
            <button type="button" class="menu__item" @click="shortcutsFromMenu">
              <Icon name="bolt" :size="14" /> 快捷键
            </button>
            <button type="button" class="menu__item" @click="loadDemoFromMenu">
              <Icon name="layers" :size="14" /> 载入示例文档
            </button>
          </div>
        </Transition>
      </div>

      <input ref="fileRef" type="file" accept=".json,application/json" class="hd__file" @change="onFileChange" />
    </div>
  </header>
</template>

<style scoped>
.hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  height: 58px;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
  position: relative;
  z-index: 40;
}

.hd__left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.hd__logo {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--brand-600), var(--violet-500));
  color: #fff;
  box-shadow: 0 8px 18px -10px rgba(79, 70, 229, 0.9);
}

.hd__brand-name {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--ink-900);
  line-height: 1.2;
}

.hd__brand-sub {
  font-size: 10px;
  color: var(--ink-400);
  line-height: 1.4;
}

.hd__sep {
  width: 1px;
  height: 22px;
  background: var(--line);
  margin: 0 2px;
}

.hd__project {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.hd__title {
  width: 220px;
  max-width: 34vw;
  padding: 5px 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-700);
  outline: none;
  text-overflow: ellipsis;
}

.hd__title:hover {
  background: var(--ink-50);
}

.hd__title:focus {
  border-color: var(--brand-200);
  background: #fff;
  box-shadow: 0 0 0 3px var(--brand-50);
}

.hd__save {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 99px;
  background: var(--mint-50);
  color: #047857;
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.hd__save.is-saving {
  background: var(--amber-50);
  color: #b45309;
}

.hd__right {
  display: flex;
  align-items: center;
  gap: 7px;
  flex: 0 0 auto;
}

.hd__group {
  display: flex;
  gap: 1px;
  padding-right: 4px;
  margin-right: 2px;
  border-right: 1px solid var(--line);
}

.hd__audit {
  position: relative;
}

.hd__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--amber-500);
  margin-left: 1px;
}

.hd__dot.is-error {
  background: var(--rose-500);
}

.hd__menu {
  position: relative;
}

.menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 214px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--shadow-lg);
  z-index: 50;
}

.menu__label {
  padding: 6px 8px 4px;
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--ink-400);
}

.menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink-700);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.menu__item:hover {
  background: var(--brand-50);
  color: var(--brand-700);
}

.hd__file {
  display: none;
}

@media (max-width: 1180px) {
  .hd__brand,
  .hd__sep {
    display: none;
  }

  .hd__title {
    width: 150px;
  }
}

/* 手机 / 平板竖屏：允许头部横向滚动，别把按钮挤出屏幕 */
@media (max-width: 760px) {
  .hd {
    overflow-x: auto;
    gap: 10px;
    padding: 0 10px;
  }

  .hd__left,
  .hd__right {
    flex: 0 0 auto;
  }

  .hd__title {
    width: 128px;
  }

  .hd__save {
    display: none;
  }
}
</style>
