<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import Icon from './Icon.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    subtitle?: string
    width?: number | string
    /** 预览这类需要占满屏幕的弹窗 */
    bare?: boolean
    closeOnBackdrop?: boolean
  }>(),
  { title: '', subtitle: '', width: 720, bare: false, closeOnBackdrop: true }
)

const emit = defineEmits<{ close: [] }>()

let previousOverflow = ''

watch(
  () => props.open,
  (open) => {
    if (open) {
      previousOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      window.addEventListener('keydown', onKeydown)
    } else {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeydown)
    }
  }
)

onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow
  window.removeEventListener('keydown', onKeydown)
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.stopPropagation()
    emit('close')
  }
}

function onBackdrop() {
  if (props.closeOnBackdrop) emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="modal" :class="{ 'modal--bare': bare }" @mousedown.self="onBackdrop">
        <div
          class="modal__panel"
          :class="{ 'modal__panel--bare': bare }"
          :style="{ width: typeof width === 'number' ? `${width}px` : width }"
          role="dialog"
          aria-modal="true"
        >
          <header v-if="title || $slots.head" class="modal__head">
            <slot name="head">
              <div class="modal__titles">
                <h2 class="modal__title">{{ title }}</h2>
                <p v-if="subtitle" class="modal__sub">{{ subtitle }}</p>
              </div>
            </slot>
            <button class="icon-btn" type="button" aria-label="关闭" @click="emit('close')">
              <Icon name="close" :size="16" />
            </button>
          </header>
          <div class="modal__body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="modal__foot">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(3px);
}

.modal--bare {
  padding: 0;
  background: rgba(15, 23, 42, 0.5);
}

.modal__panel {
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 56px);
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 40px 80px -30px rgba(15, 23, 42, 0.45);
  overflow: hidden;
}

.modal__panel--bare {
  width: 100vw;
  max-width: 100vw;
  height: 100vh;
  max-height: 100vh;
  border-radius: 0;
  box-shadow: none;
}

.modal__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 18px 14px 22px;
  border-bottom: 1px solid var(--line);
}

.modal__titles {
  min-width: 0;
}

.modal__title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--ink-900);
}

.modal__sub {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--ink-500);
}

.modal__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 22px;
}

.modal__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 13px 22px;
  border-top: 1px solid var(--line);
  background: #fcfcfe;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .modal__panel,
.modal-leave-active .modal__panel {
  transition: transform 0.24s cubic-bezier(0.22, 1, 0.36, 1);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal__panel,
.modal-leave-to .modal__panel {
  transform: translateY(14px) scale(0.985);
}
</style>
