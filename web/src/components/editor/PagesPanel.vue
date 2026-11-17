<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useEditorStore } from '@/stores/editor'
import type { PageRole } from '@/types/editor'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'

/** 重命名时自动聚焦并全选，省一次点击 */
const vFocus = {
  mounted(el: HTMLInputElement) {
    el.focus()
    el.select()
  },
}

const store = useEditorStore()
const { doc, currentPageId } = storeToRefs(store)
const toast = useToast()

const ROLE_LABEL: Record<PageRole, string> = {
  home: '首页',
  content: '内容页',
  form: '表单页',
  success: '成功页',
}

const editingId = ref<string | null>(null)
const draftName = ref('')

const pages = computed(() => doc.value.pages)

function startRename(pageId: string, name: string): void {
  editingId.value = pageId
  draftName.value = name
}

function commitRename(): void {
  if (!editingId.value) return
  const name = draftName.value.trim()
  if (name) store.renamePage(editingId.value, name)
  editingId.value = null
}

function onDelete(pageId: string): void {
  if (!store.removePage(pageId)) toast.pushToast('至少保留一个页面', 'warn')
}

function setRole(event: Event): void {
  const role = (event.target as HTMLSelectElement).value as PageRole
  if (store.currentPage) store.setPageRole(store.currentPage.id, role)
}

function updateMetaField(key: 'org' | 'startAt' | 'place' | 'deadline' | 'contact', event: Event): void {
  const value = (event.target as HTMLInputElement).value
  store.updateMeta({ [key]: value })
}

function updateCapacity(event: Event): void {
  const value = Number((event.target as HTMLInputElement).value)
  store.updateMeta({ capacity: Number.isFinite(value) ? Math.max(0, value) : 0 })
}
</script>

<template>
  <div class="pages scroll-y">
    <div class="pages__head">
      <p class="pages__title">
        页面<span>{{ pages.length }}</span>
      </p>
      <button type="button" class="btn btn--sm" @click="store.addPage()"><Icon name="plus" :size="12" /> 新页面</button>
    </div>

    <ul class="plist">
      <li
        v-for="(page, index) in pages"
        :key="page.id"
        class="pitem"
        :class="{ 'is-active': page.id === currentPageId }"
        @click="store.selectPage(page.id)"
      >
        <span class="pitem__index">{{ String(index + 1).padStart(2, '0') }}</span>
        <span class="pitem__main">
          <input
            v-if="editingId === page.id"
            v-focus
            v-model="draftName"
            class="pitem__rename"
            @click.stop
            @blur="commitRename"
            @keydown.enter.prevent="commitRename"
            @keydown.esc="editingId = null"
          />
          <span v-else class="pitem__name" @dblclick.stop="startRename(page.id, page.name)">
            {{ page.name }}
          </span>
          <span class="pitem__meta">{{ ROLE_LABEL[page.role] }} · {{ page.blocks.length }} 个组件</span>
        </span>
        <span class="pitem__acts" @click.stop>
          <button
            type="button"
            class="icon-btn icon-btn--sm"
            title="上移"
            :disabled="index === 0"
            @click="store.movePage(page.id, -1)"
          >
            <Icon name="up" :size="12" />
          </button>
          <button
            type="button"
            class="icon-btn icon-btn--sm"
            title="下移"
            :disabled="index === pages.length - 1"
            @click="store.movePage(page.id, 1)"
          >
            <Icon name="down" :size="12" />
          </button>
          <button
            type="button"
            class="icon-btn icon-btn--sm"
            title="复制页面"
            @click="store.duplicatePage(page.id)"
          >
            <Icon name="copy" :size="12" />
          </button>
          <button
            type="button"
            class="icon-btn icon-btn--sm icon-btn--danger"
            title="删除页面"
            :disabled="pages.length <= 1"
            @click="onDelete(page.id)"
          >
            <Icon name="trash" :size="12" />
          </button>
        </span>
      </li>
    </ul>

    <div class="pages__current">
      <p class="pages__sub">当前页</p>
      <div class="field">
        <span class="field__label">页面角色</span>
        <select class="select" :value="store.currentPage?.role" @change="setRole">
          <option value="home">首页</option>
          <option value="content">内容页</option>
          <option value="form">表单页</option>
          <option value="success">成功页</option>
        </select>
        <span class="hint">AI 生成和「提交后跳转」都靠角色认页面，别选错。</span>
      </div>
      <div class="field">
        <span class="field__label">页面背景</span>
        <div class="field__row">
          <input
            type="color"
            class="swatch-input"
            :value="store.currentPage?.bg || doc.theme.bg"
            @input="store.updatePageBg(store.currentPageId, ($event.target as HTMLInputElement).value)"
          />
          <button type="button" class="btn btn--sm btn--ghost" @click="store.updatePageBg(store.currentPageId, '')">
            跟随主题
          </button>
        </div>
      </div>
    </div>

    <div class="pages__meta">
      <p class="pages__sub">
        活动信息
        <span class="tag" :class="doc.published ? 'tag--mint' : ''">{{ doc.published ? '已发布' : '草稿' }}</span>
      </p>
      <label class="field">
        <span class="field__label">主办方</span>
        <input
          class="input"
          :value="doc.meta.org"
          placeholder="如 光影摄影社"
          @input="updateMetaField('org', $event)"
        />
      </label>
      <label class="field">
        <span class="field__label">时间</span>
        <input
          class="input"
          :value="doc.meta.startAt"
          placeholder="如 2026年10月26日 19:00"
          @input="updateMetaField('startAt', $event)"
        />
      </label>
      <label class="field">
        <span class="field__label">地点</span>
        <input
          class="input"
          :value="doc.meta.place"
          placeholder="如 三教 402"
          @input="updateMetaField('place', $event)"
        />
      </label>
      <div class="pages__row2">
        <label class="field">
          <span class="field__label">报名截止</span>
          <input
            class="input"
            :value="doc.meta.deadline"
            placeholder="2026-10-25 22:00"
            @input="updateMetaField('deadline', $event)"
          />
        </label>
        <label class="field">
          <span class="field__label">人数上限</span>
          <input class="input" type="number" min="0" :value="doc.meta.capacity" @input="updateCapacity" />
        </label>
      </div>
      <label class="field">
        <span class="field__label">联系方式</span>
        <input
          class="input"
          :value="doc.meta.contact"
          placeholder="微信 / 手机号 / 群名"
          @input="updateMetaField('contact', $event)"
        />
      </label>
      <p class="hint">这几项会用在信息卡片、分享海报和发布页脚，别留空。</p>
    </div>
  </div>
</template>

<style scoped>
.pages {
  height: 100%;
  padding: 14px;
}

.pages__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.pages__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--ink-400);
}

.pages__title span {
  padding: 0 5px;
  border-radius: 99px;
  background: var(--ink-100);
}

.plist {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.pitem {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 9px;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition:
    background 0.14s ease,
    border-color 0.14s ease;
}

.pitem:hover {
  background: var(--ink-50);
}

.pitem.is-active {
  border-color: var(--brand-200);
  background: var(--brand-50);
}

.pitem__index {
  font-size: 11px;
  font-weight: 700;
  color: var(--ink-300);
  font-variant-numeric: tabular-nums;
}

.pitem.is-active .pitem__index {
  color: var(--brand-500);
}

.pitem__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.pitem__name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pitem__rename {
  width: 100%;
  border: 0;
  border-bottom: 1px solid var(--brand-300);
  background: transparent;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-900);
  outline: none;
  padding: 0 0 1px;
}

.pitem__meta {
  font-size: 11px;
  color: var(--ink-400);
}

.pitem__acts {
  display: flex;
  gap: 1px;
  opacity: 0;
  transition: opacity 0.14s ease;
}

.pitem:hover .pitem__acts,
.pitem.is-active .pitem__acts {
  opacity: 1;
}

.pages__current {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border-radius: 8px;
  background: var(--ink-50);
}

.pages__sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--ink-400);
}

.pages__meta {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
}

.pages__row2 {
  display: grid;
  grid-template-columns: 1fr 82px;
  gap: 8px;
}

.swatch-input {
  width: 34px;
  height: 28px;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}
</style>
