<script setup lang="ts">
import { ref } from 'vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import Icon from '@/components/ui/Icon.vue'
import { PHOTO_LIBRARY } from '@/data/photoLibrary'

const props = withDefaults(
  defineProps<{
    open: boolean
    current?: string
  }>(),
  { current: '' }
)

const emit = defineEmits<{ close: []; select: [url: string] }>()
const uploading = ref(false)
const error = ref('')

function choose(url: string): void {
  emit('select', url)
  emit('close')
}

async function readFile(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('读取图片失败'))
    reader.readAsDataURL(file)
  })
}

async function compress(file: File): Promise<string> {
  const raw = await readFile(file)
  const image = new Image()
  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve()
    image.onerror = () => reject(new Error('图片格式无法识别'))
    image.src = raw
  })

  const maxSide = 1600
  const scale = Math.min(1, maxSide / Math.max(image.width, image.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(image.width * scale))
  canvas.height = Math.max(1, Math.round(image.height * scale))
  const context = canvas.getContext('2d')
  if (!context) throw new Error('浏览器无法处理这张图片')
  context.fillStyle = '#fffdf9'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.drawImage(image, 0, 0, canvas.width, canvas.height)
  return canvas.toDataURL('image/jpeg', 0.82)
}

async function onFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  error.value = ''
  if (!file.type.startsWith('image/')) {
    error.value = '请选择图片文件'
    return
  }
  if (file.size > 12 * 1024 * 1024) {
    error.value = '图片不要超过 12MB'
    return
  }
  uploading.value = true
  try {
    const url = await compress(file)
    choose(url)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '图片处理失败'
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <BaseModal
    :open="props.open"
    title="选择图片"
    subtitle="从图库选一张，或上传自己的活动照片。上传图会自动压到 1600px 以内。"
    :width="680"
    @close="emit('close')"
  >
    <div class="picker">
      <div class="picker__gallery">
        <button
          v-for="photo in PHOTO_LIBRARY"
          :key="photo.id"
          type="button"
          class="photo"
          :class="{ 'is-current': current === photo.url }"
          @click="choose(photo.url)"
        >
          <img :src="photo.url" :alt="photo.alt" loading="lazy" />
          <span>{{ photo.label }}</span>
          <i v-if="current === photo.url"><Icon name="check" :size="12" /></i>
        </button>
      </div>

      <label class="upload">
        <input type="file" accept="image/*" @change="onFile" />
        <span class="upload__icon"><Icon name="image" :size="18" /></span>
        <span>
          <strong>{{ uploading ? '正在处理…' : '上传自己的图片' }}</strong>
          <small>JPG / PNG / WebP，最大 12MB</small>
        </span>
        <Icon name="arrow-right" :size="15" />
      </label>
      <p v-if="error" class="picker__error">{{ error }}</p>
    </div>
  </BaseModal>
</template>

<style scoped>
.picker {
  display: grid;
  gap: 14px;
}

.picker__gallery {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.photo {
  position: relative;
  display: flex;
  overflow: hidden;
  min-height: 124px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 9px;
  color: #fff;
  background: var(--surface-soft);
  flex-direction: column;
  justify-content: flex-end;
  text-align: left;
  cursor: pointer;
}

.photo img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 40%, rgb(20 28 24 / 68%));
  content: '';
}

.photo > span {
  position: relative;
  z-index: 1;
  padding: 9px;
  font-size: 12px;
  font-weight: 700;
}

.photo > i {
  position: absolute;
  z-index: 2;
  top: 8px;
  right: 8px;
  display: grid;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  color: var(--brand-800);
  background: #fff;
  place-items: center;
}

.photo.is-current {
  border-color: var(--brand-500);
  box-shadow: 0 0 0 2px var(--brand-200);
}

.upload {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 13px;
  border: 1px dashed var(--line-strong);
  border-radius: 9px;
  color: var(--ink-600);
  background: var(--surface-soft);
  cursor: pointer;
}

.upload input {
  display: none;
}

.upload__icon {
  display: grid;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: var(--brand-700);
  background: var(--surface);
  place-items: center;
}

.upload > span:nth-child(3) {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.upload strong {
  color: var(--ink-800);
  font-size: 12px;
}

.upload small {
  color: var(--muted);
  font-size: 11px;
}

.picker__error {
  color: var(--danger);
  font-size: 12px;
}

@media (max-width: 560px) {
  .picker__gallery {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
