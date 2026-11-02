<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useEditorStore } from '@/stores/editor'
import PhoneScreen from '@/components/editor/PhoneScreen.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const store = useEditorStore()
const { doc } = storeToRefs(store)
const toast = useToast()

const pageId = ref('')
const scale = ref(1)

const page = computed(() => doc.value.pages.find((item) => item.id === pageId.value) ?? doc.value.pages[0])
const shareUrl = computed(() => store.shareUrl)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    pageId.value = doc.value.pages[0]?.id ?? ''
    updateScale()
  }
)

function updateScale(): void {
  const availableHeight = window.innerHeight - 132
  const availableWidth = window.innerWidth - 120
  scale.value = Math.min(1, availableHeight / 845, availableWidth / 400)
}

function go(id: string): void {
  pageId.value = id
}

function restart(): void {
  const home = doc.value.pages.find((item) => item.role === 'home') ?? doc.value.pages[0]
  if (home) pageId.value = home.id
}

function onSubmit(): void {
  const success = doc.value.pages.find((item) => item.role === 'success')
  if (success) {
    pageId.value = success.id
    toast.pushToast('表单校验通过（预览模式，报名数据未提交）', 'success')
  } else {
    toast.pushToast('表单校验通过（这个项目还没有「成功页」）', 'warn')
  }
}

function onInvalid(): void {
  toast.pushToast('有必填项没填，红色提示的那几个', 'warn')
}

onMounted(() => {
  updateScale()
  window.addEventListener('resize', updateScale)
  window.addEventListener('keydown', onKey)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateScale)
  window.removeEventListener('keydown', onKey)
})

function onKey(event: KeyboardEvent): void {
  if (!props.open) return
  if (event.key === 'Escape') emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="pv">
      <div v-if="open" class="pv">
        <header class="pv__bar">
          <div class="pv__brand">
            <span class="pv__logo"><Icon name="phone" :size="14" /></span>
            <div>
              <p class="pv__title">{{ doc.title }}</p>
              <p class="pv__sub">参与者视角 · 编辑器本地预览</p>
            </div>
          </div>

          <div class="pv__pages">
            <button
              v-for="item in doc.pages"
              :key="item.id"
              type="button"
              class="pv__page"
              :class="{ 'is-active': item.id === page?.id }"
              @click="go(item.id)"
            >
              {{ item.name }}
            </button>
          </div>

          <div class="pv__actions">
            <button type="button" class="btn btn--sm pv__btn" @click="restart">
              <Icon name="refresh" :size="13" /> 重新开始
            </button>
            <button type="button" class="icon-btn pv__close" aria-label="关闭预览" @click="emit('close')">
              <Icon name="close" :size="17" />
            </button>
          </div>
        </header>

        <div class="pv__stage-wrap">
          <div class="pv__stage" :style="{ width: `${390 * scale}px`, height: `${845 * scale}px` }">
            <div class="pv__scaler" :style="{ transform: `scale(${scale})` }">
              <PhoneScreen
                v-if="page"
                :page="page"
                :theme="doc.theme"
                :pages="doc.pages"
                :share-url="shareUrl"
                mode="preview"
                :width="390"
                @navigate="go"
                @submit="onSubmit"
                @invalid="onInvalid"
              />
            </div>
          </div>
        </div>

        <p class="pv__hint">这是编辑器预览，只检查页面和表单交互，不会写入报名数据；真实报名请发布后从公开链接进入。</p>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.pv {
  position: fixed;
  inset: 0;
  z-index: 2500;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 0 18px;
  background: radial-gradient(circle at 50% 0%, #26304a 0%, #10141f 58%, #0a0d14 100%);
  color: #e8ecf4;
}

.pv__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex: 0 0 auto;
}

.pv__brand {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.pv__logo {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.1);
  color: #a5b4fc;
}

.pv__title {
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 260px;
}

.pv__sub {
  font-size: 10px;
  color: rgba(232, 236, 244, 0.5);
}

.pv__pages {
  display: flex;
  gap: 3px;
  padding: 3px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  overflow-x: auto;
  max-width: 46vw;
}

.pv__page {
  padding: 5px 11px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: rgba(232, 236, 244, 0.62);
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.pv__page:hover {
  color: #fff;
}

.pv__page.is-active {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}

.pv__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.pv__btn {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.14);
  color: #e8ecf4;
}

.pv__btn:hover {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.pv__close {
  color: rgba(232, 236, 244, 0.7);
}

.pv__close:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.pv__stage-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  overflow: hidden;
}

.pv__stage {
  position: relative;
  margin-top: 6px;
}

.pv__scaler {
  width: 390px;
  transform-origin: top left;
}

.pv__hint {
  flex: 0 0 auto;
  text-align: center;
  font-size: 10.5px;
  color: rgba(232, 236, 244, 0.42);
}

.pv-enter-active,
.pv-leave-active {
  transition: opacity 0.22s ease;
}

.pv-enter-from,
.pv-leave-to {
  opacity: 0;
}
</style>
