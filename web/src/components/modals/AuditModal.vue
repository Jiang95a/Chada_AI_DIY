<script setup lang="ts">
import { computed } from 'vue'
import { useEditorStore } from '@/stores/editor'
import type { Issue } from '@/types/editor'
import BaseModal from '@/components/ui/BaseModal.vue'
import Icon from '@/components/ui/Icon.vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; locate: [issue: Issue] }>()

const store = useEditorStore()
const issues = computed(() => store.issues)
const summary = computed(() => store.issueSummary)

const LEVEL_META = {
  error: { label: '必须修', icon: 'alert', className: 'is-error' },
  warn: { label: '建议改', icon: 'info', className: 'is-warn' },
  info: { label: '可以看看', icon: 'dot', className: 'is-info' },
} as const

const grouped = computed(() =>
  (['error', 'warn', 'info'] as const)
    .map((level) => ({
      level,
      meta: LEVEL_META[level],
      items: issues.value.filter((item) => item.level === level),
    }))
    .filter((group) => group.items.length > 0)
)

function pageName(pageId: string): string {
  return store.doc.pages.find((page) => page.id === pageId)?.name ?? '项目'
}

function locate(issue: Issue): void {
  if (issue.pageId) store.selectPage(issue.pageId)
  if (issue.blockId) store.selectBlock(issue.blockId)
  emit('locate', issue)
  emit('close')
}
</script>

<template>
  <BaseModal
    :open="props.open"
    title="发布前质量检查"
    subtitle="规则由前端本地执行：表单完整性、文案长度、对比度、移动端适配"
    :width="660"
    @close="emit('close')"
  >
    <div class="audit">
      <div class="audit__summary">
        <div class="audit__stat" :class="{ 'is-error': summary.error > 0 }">
          <span class="audit__num">{{ summary.error }}</span>
          <span class="audit__label">必须修</span>
        </div>
        <div class="audit__stat" :class="{ 'is-warn': summary.warn > 0 }">
          <span class="audit__num">{{ summary.warn }}</span>
          <span class="audit__label">建议改</span>
        </div>
        <div class="audit__stat">
          <span class="audit__num">{{ summary.info }}</span>
          <span class="audit__label">可以看看</span>
        </div>
        <p class="audit__verdict">
          {{
            summary.error
              ? '有硬伤，先修完再发布。'
              : summary.warn
                ? '能发，但建议把标红的那几条也改掉。'
                : '这版看着没问题，可以发。'
          }}
        </p>
      </div>

      <div v-if="!issues.length" class="audit__empty">
        <Icon name="check-circle" :size="26" />
        <p>没查出问题。文案、表单、对比度都在线。</p>
      </div>

      <section v-for="group in grouped" :key="group.level" class="audit__group">
        <p class="audit__group-title" :class="group.meta.className">
          <Icon :name="group.meta.icon" :size="12" />
          {{ group.meta.label }} · {{ group.items.length }}
        </p>
        <ul class="audit__list">
          <li v-for="issue in group.items" :key="issue.id" class="aitem" :class="group.meta.className">
            <div class="aitem__main">
              <p class="aitem__title">{{ issue.title }}</p>
              <p class="aitem__detail">{{ issue.detail }}</p>
              <p class="aitem__where">{{ pageName(issue.pageId) }}</p>
            </div>
            <button v-if="issue.pageId" type="button" class="btn btn--sm" @click="locate(issue)">定位</button>
          </li>
        </ul>
      </section>

      <p class="audit__note">这些规则是纯前端判断，不消耗 AI 调用；「定位」会切到对应页面并选中组件。</p>
    </div>
  </BaseModal>
</template>

<style scoped>
.audit {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.audit__summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fcfcfe;
}

.audit__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  min-width: 62px;
  padding: 7px 10px;
  border-radius: 10px;
  background: var(--ink-50);
}

.audit__stat.is-error {
  background: var(--rose-50);
}

.audit__stat.is-warn {
  background: var(--amber-50);
}

.audit__num {
  font-size: 18px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--ink-800);
  font-variant-numeric: tabular-nums;
}

.audit__stat.is-error .audit__num {
  color: #b42318;
}

.audit__stat.is-warn .audit__num {
  color: #b45309;
}

.audit__label {
  font-size: 10px;
  color: var(--ink-400);
}

.audit__verdict {
  flex: 1;
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--ink-600);
}

.audit__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 30px;
  color: var(--mint-500);
  font-size: 12px;
  text-align: center;
}

.audit__empty p {
  color: var(--ink-500);
}

.audit__group {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.audit__group-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--ink-400);
}

.audit__group-title.is-error {
  color: #b42318;
}

.audit__group-title.is-warn {
  color: #b45309;
}

.audit__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.aitem {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-left-width: 3px;
  border-radius: 10px;
  background: #fff;
}

.aitem.is-error {
  border-left-color: var(--rose-500);
}

.aitem.is-warn {
  border-left-color: var(--amber-500);
}

.aitem.is-info {
  border-left-color: var(--sky-500);
}

.aitem__main {
  flex: 1;
  min-width: 0;
}

.aitem__title {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ink-800);
}

.aitem__detail {
  margin-top: 2px;
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--ink-500);
}

.aitem__where {
  margin-top: 3px;
  font-size: 10px;
  color: var(--ink-400);
}

.audit__note {
  font-size: 10.5px;
  line-height: 1.7;
  color: var(--ink-400);
}
</style>
