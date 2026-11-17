<script setup lang="ts">
import { computed, ref } from 'vue'
import type { BlockType } from '@/types/editor'
import { BLOCK_GROUPS, BLOCK_META } from '@/data/blockCatalog'
import Icon from '@/components/ui/Icon.vue'

const emit = defineEmits<{ add: [type: BlockType] }>()

const keyword = ref('')

const filtered = computed(() => {
  const key = keyword.value.trim()
  if (!key) return BLOCK_META
  return BLOCK_META.filter((item) => item.label.includes(key) || item.desc.includes(key) || item.type.includes(key))
})

const groups = computed(() =>
  BLOCK_GROUPS.map((name) => ({
    name,
    items: filtered.value.filter((item) => item.group === name),
  })).filter((group) => group.items.length > 0)
)

function onDragStart(event: DragEvent, type: BlockType): void {
  if (!event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-block-type', type)
  event.dataTransfer.setData('text/plain', type)
}
</script>

<template>
  <div class="palette">
    <div class="palette__search">
      <Icon name="search" :size="14" class="palette__search-icon" />
      <input v-model="keyword" class="input palette__input" placeholder="找组件：表单、Banner…" />
    </div>

    <div class="palette__scroll scroll-y">
      <section v-for="group in groups" :key="group.name" class="palette__group">
        <p class="palette__group-title">
          {{ group.name }}
          <span>{{ group.items.length }}</span>
        </p>
        <div class="palette__grid">
          <button
            v-for="item in group.items"
            :key="item.type"
            type="button"
            class="pitem"
            :title="`拖到画布，或点击添加：${item.desc}`"
            draggable="true"
            @dragstart="onDragStart($event, item.type)"
            @click="emit('add', item.type)"
          >
            <span class="pitem__icon"><Icon :name="item.icon" :size="16" /></span>
            <span class="pitem__body">
              <span class="pitem__label">{{ item.label }}</span>
              <small class="pitem__desc">{{ item.desc }}</small>
            </span>
            <span class="pitem__drag"><Icon name="grip" :size="12" /></span>
          </button>
        </div>
      </section>

      <p v-if="!groups.length" class="empty">没找到这个组件，换个词试试</p>
    </div>

    <p class="palette__hint">拖动可以插到任意位置，点击直接加到末尾</p>
  </div>
</template>

<style scoped>
.palette {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.palette__search {
  position: relative;
  padding: 12px 14px 8px;
}

.palette__search-icon {
  position: absolute;
  left: 24px;
  top: 50%;
  transform: translateY(-30%);
  color: var(--ink-400);
  pointer-events: none;
}

.palette__input {
  padding-left: 28px;
  height: 32px;
  font-size: 12.5px;
  background: var(--ink-50);
  border-color: transparent;
}

.palette__input:focus {
  background: #fff;
}

.palette__scroll {
  flex: 1;
  min-height: 0;
  padding: 0 14px 6px;
}

.palette__group + .palette__group {
  margin-top: 14px;
}

.palette__group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 7px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--ink-400);
}

.palette__group-title span {
  padding: 0 5px;
  border-radius: 99px;
  background: var(--ink-100);
  color: var(--ink-400);
  letter-spacing: 0;
}

.palette__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
}

.pitem {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 9px 8px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  color: var(--ink-700);
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  cursor: grab;
  transition:
    border-color 0.14s ease,
    box-shadow 0.14s ease,
    transform 0.14s ease;
}

.pitem__body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 1px;
}

.pitem__desc {
  overflow: hidden;
  color: var(--ink-400);
  font-size: 11px;
  font-weight: 400;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pitem:hover {
  border-color: var(--brand-200);
  box-shadow: 0 6px 16px -12px rgba(79, 70, 229, 0.6);
  transform: translateY(-1px);
}

.pitem:active {
  cursor: grabbing;
  transform: translateY(0);
}

.pitem__icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  border-radius: 8px;
  background: var(--brand-50);
  color: var(--brand-600);
}

.pitem__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pitem__drag {
  opacity: 0;
  color: var(--ink-300);
  transition: opacity 0.14s ease;
}

.pitem:hover .pitem__drag {
  opacity: 1;
}

.palette__hint {
  padding: 10px 14px 12px;
  border-top: 1px dashed var(--line);
  font-size: 11px;
  line-height: 1.6;
  color: var(--ink-400);
}
</style>
