<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import type { Block, Page, Theme } from '@/types/editor'
import Icon from '@/components/ui/Icon.vue'
import QrCode from '@/components/ui/QrCode.vue'
import { FORM_CONTEXT } from '@/composables/formContext'
import { INFO_ICON_MAP } from '@/data/blockCatalog'
import { isDarkColor, mixColor } from '@/utils/theme'
import { parseDate } from '@/utils/misc'

const props = withDefaults(
  defineProps<{
    block: Block
    theme: Theme
    mode?: 'edit' | 'preview'
    pages?: Page[]
    shareUrl?: string
  }>(),
  { mode: 'edit', pages: () => [], shareUrl: '' }
)

const emit = defineEmits<{ action: [payload: any] }>()

const form = inject(FORM_CONTEXT, null)
const p = computed<Record<string, any>>(() => props.block.props)
const isPreview = computed(() => props.mode === 'preview')

const surface = computed(() =>
  isDarkColor(props.theme.bg) ? mixColor(props.theme.text, props.theme.bg, 0.9) : '#ffffff'
)
const surfaceInk = computed(() => (isDarkColor(props.theme.bg) ? props.theme.text : '#111827'))
const surfaceMuted = computed(() => mixColor(surfaceInk.value, surface.value, 0.45))

const wrapperStyle = computed(() => {
  const style = props.block.style
  const fullBleed = props.block.type === 'banner' || props.block.type === 'nav'
  return {
    background: style.bg || undefined,
    borderRadius: fullBleed ? undefined : `${style.radius}px`,
    padding: fullBleed ? '0' : `calc(${style.paddingY}px * var(--sp)) calc(${style.paddingX}px * var(--sp))`,
    textAlign: style.align,
    '--surface': surface.value,
    '--surface-ink': surfaceInk.value,
    '--surface-muted': surfaceMuted.value,
  } as Record<string, string | undefined>
})

/* ------------------------------- 文案 / 尺寸 ------------------------------- */

const headingSize = computed(() => {
  const map: Record<string, string> = { xl: '26px', lg: '21px', md: '17px' }
  return map[String(p.value.size)] ?? '21px'
})

const bodySize = computed(() => (p.value.size === 'sm' ? '12.5px' : '14px'))

const bannerStyle = computed(() => {
  const height = Number(p.value.height) || 220
  const image = String(p.value.image ?? '').trim()
  return {
    height: `${height}px`,
    backgroundImage: image
      ? `linear-gradient(180deg, rgba(9,12,20,0.12), rgba(9,12,20,0.62)), url(${JSON.stringify(image)})`
      : `linear-gradient(135deg, ${props.theme.primary}, ${props.theme.accent})`,
  }
})

const ratioClass = computed(() => {
  const map: Record<string, string> = {
    '4:3': 'ratio-4-3',
    '1:1': 'ratio-1-1',
    '16:9': 'ratio-16-9',
    '3:4': 'ratio-3-4',
  }
  return map[String(p.value.ratio)] ?? 'ratio-4-3'
})

const imageUrl = computed(() => {
  const url = String(p.value.url ?? '').trim()
  if (url) return url
  return ''
})

const qrValue = computed(() => String(p.value.url ?? '').trim() || props.shareUrl || 'https://campus.local')

/* ------------------------------- 按钮 ------------------------------- */

const action = computed(() => p.value.action ?? { type: 'none', pageId: '', url: '' })

const actionLabel = computed(() => {
  const current = action.value
  if (current.type === 'url') return `打开链接 ${String(current.url || '').slice(0, 24)}`
  if (current.type === 'submit') return '提交表单'
  if (current.type === 'page') {
    const target =
      props.pages.find((page) => page.id === current.pageId) ??
      props.pages.find((page) => page.role === current.targetRole)
    return target ? `跳转「${target.name}」` : '跳转（未指定页面）'
  }
  return '无动作'
})

function onButtonClick() {
  if (!isPreview.value) return
  emit('action', action.value)
}

/* ------------------------------- 表单 ------------------------------- */

function fieldKey(field: any): string {
  return `${props.block.id}:${field.id}`
}

function getAnswer(key: string): string | string[] {
  return form?.answers[key] ?? ''
}

function setAnswer(key: string, value: string | string[]) {
  form?.setAnswer(key, value)
}

function onInput(key: string, event: Event) {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
  setAnswer(key, target.value)
}

const groupValue = computed<string[]>(() => {
  const raw = form?.answers[props.block.id]
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string' && raw) return [raw]
  return []
})

function toggleOption(option: string) {
  if (!isPreview.value || !form) return
  if (props.block.type === 'radio') {
    form.setAnswer(props.block.id, option)
    return
  }
  const current = groupValue.value.includes(option)
  form.setAnswer(
    props.block.id,
    current ? groupValue.value.filter((item) => item !== option) : [...groupValue.value, option]
  )
}

function optionState(option: string): boolean {
  return groupValue.value.includes(option)
}

const groupError = computed(() => Boolean(form?.errors[props.block.id]))
const fieldError = (field: any) => Boolean(form?.errors[fieldKey(field)])

function onFormSubmit() {
  if (!isPreview.value) return
  emit('action', { type: 'submit' })
}

function onConsentChange(event: Event) {
  form?.setConsent?.((event.target as HTMLInputElement).checked)
}

/* ------------------------------- 倒计时 ------------------------------- */

const now = ref(Date.now())
let timer: number | undefined

onMounted(() => {
  if (props.block.type !== 'countdown') return
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer !== undefined) window.clearInterval(timer)
})

const countdown = computed(() => {
  const stamp = parseDate(String(p.value.target ?? ''))
  if (stamp === null) return null
  const diff = stamp - now.value
  if (diff <= 0) return null
  const totalSeconds = Math.floor(diff / 1000)
  return {
    days: Math.floor(totalSeconds / 86400),
    hours: Math.floor((totalSeconds % 86400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
  }
})
</script>

<template>
  <!-- 导航栏 -->
  <div v-if="block.type === 'nav'" class="vb vb--nav" :style="wrapperStyle">
    <div class="nav">
      <span class="nav__side">
        <Icon v-if="p.showBack" name="chevron" :size="17" class="nav__back" />
      </span>
      <span class="nav__title">{{ p.title || '活动标题' }}</span>
      <span class="nav__side nav__side--right">{{ p.right }}</span>
    </div>
  </div>

  <!-- Banner -->
  <div v-else-if="block.type === 'banner'" class="vb vb--banner" :style="wrapperStyle">
    <div class="banner" :style="bannerStyle">
      <div class="banner__glow" aria-hidden="true" />
      <div class="banner__text">
        <p class="banner__title">{{ p.title }}</p>
        <p v-if="p.subtitle" class="banner__sub">{{ p.subtitle }}</p>
      </div>
    </div>
  </div>

  <!-- 标题 -->
  <div v-else-if="block.type === 'heading'" class="vb vb--heading" :style="wrapperStyle">
    <p class="heading__text" :style="{ fontSize: headingSize }">{{ p.text }}</p>
    <p v-if="p.sub" class="heading__sub">{{ p.sub }}</p>
  </div>

  <!-- 文本 -->
  <div v-else-if="block.type === 'text'" class="vb vb--text" :style="wrapperStyle">
    <p class="text__body" :style="{ fontSize: bodySize }">{{ p.text }}</p>
  </div>

  <!-- 图片 -->
  <div v-else-if="block.type === 'image'" class="vb vb--image" :style="wrapperStyle">
    <figure class="image" :class="ratioClass">
      <img v-if="imageUrl" :src="imageUrl" alt="" />
      <div v-else class="image__empty">
        <Icon name="image" :size="22" />
        <span>在右侧属性里填写图片地址</span>
      </div>
    </figure>
    <figcaption v-if="p.caption" class="image__caption">{{ p.caption }}</figcaption>
  </div>

  <!-- 信息卡片 -->
  <div v-else-if="block.type === 'infoCard'" class="vb vb--info" :style="wrapperStyle">
    <div class="icard">
      <p v-if="p.title" class="icard__title">{{ p.title }}</p>
      <ul class="icard__list">
        <li v-for="item in p.items" :key="item.id" class="icard__row">
          <span class="icard__icon"><Icon :name="INFO_ICON_MAP[item.icon] || 'dot'" :size="15" /></span>
          <span class="icard__label">{{ item.label }}</span>
          <span class="icard__value">{{ item.value }}</span>
        </li>
      </ul>
    </div>
  </div>

  <!-- 按钮 -->
  <div v-else-if="block.type === 'button'" class="vb vb--button" :style="wrapperStyle">
    <button
      type="button"
      class="cta"
      :class="[
        `cta--${theme.buttonStyle}`,
        {
          'cta--full': p.full,
          'cta--soft': p.variant === 'soft',
          'cta--outline': p.variant === 'outline',
        },
      ]"
      @click="onButtonClick"
    >
      {{ p.text || '按钮文案' }}
    </button>
    <p v-if="!isPreview" class="cta__meta">{{ actionLabel }}</p>
  </div>

  <!-- 表单 -->
  <div v-else-if="block.type === 'form'" class="vb vb--form" :style="wrapperStyle">
    <div class="formcard">
      <p v-if="p.title" class="formcard__title">{{ p.title }}</p>
      <div class="formcard__fields">
        <label v-for="field in p.fields" :key="field.id" class="ff">
          <span class="ff__label">
            {{ field.label || '未命名字段' }}
            <em v-if="field.required" class="ff__required">必填</em>
          </span>

          <select
            v-if="field.type === 'select'"
            class="ff__control"
            :class="{ 'is-error': fieldError(field) }"
            :value="String(getAnswer(fieldKey(field)))"
            :disabled="!isPreview"
            @change="onInput(fieldKey(field), $event)"
          >
            <option value="">{{ field.placeholder || '请选择' }}</option>
            <option v-for="option in field.options" :key="option" :value="option">
              {{ option }}
            </option>
          </select>

          <textarea
            v-else-if="field.type === 'textarea'"
            class="ff__control ff__control--area"
            :class="{ 'is-error': fieldError(field) }"
            :value="String(getAnswer(fieldKey(field)))"
            :placeholder="field.placeholder"
            :readonly="!isPreview"
            rows="3"
            @input="onInput(fieldKey(field), $event)"
          />

          <input
            v-else
            class="ff__control"
            :class="{ 'is-error': fieldError(field) }"
            :type="field.type === 'tel' ? 'tel' : field.type === 'email' ? 'email' : 'text'"
            :value="String(getAnswer(fieldKey(field)))"
            :placeholder="field.placeholder || '请输入'"
            :readonly="!isPreview"
            @input="onInput(fieldKey(field), $event)"
          />

          <em v-if="fieldError(field)" class="ff__error">这一项要填</em>
        </label>
      </div>

      <label v-if="form?.consentRequired" class="form-consent">
        <input type="checkbox" :checked="form.consentAccepted" @change="onConsentChange" />
        <span>
          <strong>参与前请了解</strong>
          {{ form.consentText }}
        </span>
      </label>

      <button type="button" class="cta cta--submit" :class="`cta--${theme.buttonStyle}`" @click="onFormSubmit">
        {{ p.buttonText || '提交' }}
      </button>
      <p v-if="p.successHint" class="formcard__hint">{{ p.successHint }}</p>
    </div>
  </div>

  <!-- 单选框 / 多选框 -->
  <div v-else-if="block.type === 'radio' || block.type === 'checkbox'" class="vb vb--choice" :style="wrapperStyle">
    <div class="choice" :class="{ 'is-error': groupError }">
      <p class="choice__question">
        {{ p.question || '未命名问题' }}
        <em v-if="p.required" class="ff__required">必选</em>
      </p>
      <p v-if="p.hint" class="choice__hint">{{ p.hint }}</p>
      <ul class="choice__list">
        <li v-for="option in p.options" :key="option">
          <button
            type="button"
            class="opt"
            :class="{
              'is-on': optionState(option),
              'opt--radio': block.type === 'radio',
              'opt--check': block.type === 'checkbox',
            }"
            @click="toggleOption(option)"
          >
            <span class="opt__mark">
              <Icon v-if="optionState(option)" name="check" :size="11" />
            </span>
            <span class="opt__text">{{ option }}</span>
          </button>
        </li>
      </ul>
      <p v-if="groupError" class="ff__error">这一项要选</p>
    </div>
  </div>

  <!-- 二维码 -->
  <div v-else-if="block.type === 'qrcode'" class="vb vb--qr" :style="wrapperStyle">
    <div class="qrbox">
      <div class="qrbox__code">
        <QrCode :value="qrValue" :size="Number(p.size) || 132" />
      </div>
      <p v-if="p.label" class="qrbox__label">{{ p.label }}</p>
    </div>
  </div>

  <!-- 分割线 -->
  <div v-else-if="block.type === 'divider'" class="vb vb--divider" :style="wrapperStyle">
    <div class="hr">
      <span class="hr__line" />
      <span v-if="p.label" class="hr__text">{{ p.label }}</span>
      <span class="hr__line" />
    </div>
  </div>

  <!-- 倒计时 -->
  <div v-else-if="block.type === 'countdown'" class="vb vb--countdown" :style="wrapperStyle">
    <div class="cd">
      <p class="cd__label">
        {{ countdown ? p.label || '倒计时' : p.doneText || '已结束' }}
      </p>
      <div v-if="countdown" class="cd__nums">
        <span class="cd__num">{{ countdown.days }}<em>天</em></span>
        <span class="cd__num">{{ String(countdown.hours).padStart(2, '0') }}<em>时</em></span>
        <span class="cd__num">{{ String(countdown.minutes).padStart(2, '0') }}<em>分</em></span>
        <span class="cd__num">{{ String(countdown.seconds).padStart(2, '0') }}<em>秒</em></span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.vb {
  position: relative;
  font-family: var(--page-font);
  color: var(--ink);
  font-size: calc(15px * var(--fs));
}

/* ------------------------------- 导航 ------------------------------- */
.nav {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 50px;
  padding: 0 12px;
  position: sticky;
  top: 0;
  z-index: 5;
  background: color-mix(in srgb, var(--page-bg) 86%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid color-mix(in srgb, var(--ink) 8%, transparent);
}

.nav__side {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 56px;
  min-width: 56px;
}

.nav__side--right {
  justify-content: flex-end;
  font-size: 11.5px;
  color: color-mix(in srgb, var(--ink) 55%, transparent);
}

.nav__back {
  transform: rotate(90deg);
  color: color-mix(in srgb, var(--ink) 70%, transparent);
}

.nav__title {
  flex: 1;
  min-width: 0;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ------------------------------- Banner ------------------------------- */
.banner {
  position: relative;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
  border-radius: 0 0 calc(var(--radius) + 10px) calc(var(--radius) + 10px);
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.banner__glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 88% 12%, rgba(255, 255, 255, 0.32), transparent 46%),
    radial-gradient(circle at 12% 88%, rgba(0, 0, 0, 0.18), transparent 52%);
  pointer-events: none;
}

.banner__text {
  position: relative;
  padding: 22px 20px 24px;
  color: #fff;
  text-shadow: 0 2px 12px rgba(8, 12, 20, 0.28);
}

.banner__title {
  font-size: 22px;
  font-weight: 800;
  line-height: 1.32;
  letter-spacing: -0.01em;
}

.banner__sub {
  margin-top: 6px;
  font-size: 12.5px;
  font-weight: 500;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.86);
}

/* ------------------------------- 标题 / 文本 ------------------------------- */
.heading__text {
  font-weight: 750;
  line-height: 1.36;
  letter-spacing: -0.012em;
}

.heading__sub {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--surface-muted);
}

.text__body {
  white-space: pre-wrap;
  line-height: 1.85;
  color: color-mix(in srgb, var(--ink) 76%, transparent);
}

/* ------------------------------- 图片 ------------------------------- */
.image {
  margin: 0;
  overflow: hidden;
  border-radius: calc(var(--radius) * 0.72);
  background: color-mix(in srgb, var(--ink) 8%, transparent);
}

.image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ratio-4-3 {
  aspect-ratio: 4 / 3;
}

.ratio-1-1 {
  aspect-ratio: 1 / 1;
}

.ratio-16-9 {
  aspect-ratio: 16 / 9;
}

.ratio-3-4 {
  aspect-ratio: 3 / 4;
}

.image__empty {
  width: 100%;
  height: 100%;
  min-height: 96px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: color-mix(in srgb, var(--ink) 42%, transparent);
  font-size: 11.5px;
}

.image__caption {
  margin-top: 7px;
  text-align: center;
  font-size: 11px;
  color: var(--surface-muted);
}

/* ------------------------------- 信息卡片 ------------------------------- */
.icard {
  background: var(--surface);
  border-radius: var(--radius);
  padding: 14px 16px;
  color: var(--surface-ink);
  border: 1px solid color-mix(in srgb, var(--ink) 7%, transparent);
  box-shadow: 0 6px 18px -14px rgba(15, 23, 42, 0.4);
}

.icard__title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 10px;
  color: var(--surface-ink);
}

.icard__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.icard__row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  min-width: 0;
}

.icard__icon {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--brand) 12%, transparent);
  color: var(--brand);
  flex: 0 0 auto;
}

.icard__label {
  color: var(--surface-muted);
  flex: 0 0 auto;
}

.icard__value {
  margin-left: auto;
  font-weight: 600;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ------------------------------- 按钮 ------------------------------- */
.cta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: fit-content;
  min-height: 46px;
  padding: 0 22px;
  border: 0;
  border-radius: calc(var(--radius) * 0.8);
  font-size: 14.5px;
  font-weight: 700;
  letter-spacing: 0.01em;
  cursor: pointer;
  transition:
    transform 0.15s ease,
    filter 0.15s ease;
}

.cta--full {
  width: 100%;
}

.cta--solid {
  background: var(--brand);
  color: #fff;
}

.cta--gradient {
  background: linear-gradient(135deg, var(--brand), var(--accent));
  color: #fff;
}

.cta--outline {
  background: transparent;
  color: var(--brand);
  border: 1.5px solid color-mix(in srgb, var(--brand) 70%, transparent);
}

.cta--soft {
  background: color-mix(in srgb, var(--brand) 12%, transparent);
  color: var(--brand);
}

.cta:active {
  transform: translateY(1px);
}

.cta__meta {
  margin-top: 6px;
  text-align: center;
  font-size: 10.5px;
  color: var(--surface-muted);
}

/* ------------------------------- 表单 ------------------------------- */
.formcard {
  background: var(--surface);
  border-radius: var(--radius);
  padding: 16px;
  color: var(--surface-ink);
  border: 1px solid color-mix(in srgb, var(--ink) 7%, transparent);
}

.formcard__title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
}

.formcard__fields {
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.ff {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.ff__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--surface-muted);
}

.ff__required {
  font-style: normal;
  font-size: 9.5px;
  font-weight: 700;
  color: color-mix(in srgb, var(--brand) 80%, #f43f5e);
  background: color-mix(in srgb, var(--brand) 10%, transparent);
  border-radius: 4px;
  padding: 1px 4px;
}

.ff__control {
  width: 100%;
  min-height: 40px;
  padding: 9px 11px;
  border: 1px solid color-mix(in srgb, var(--ink) 14%, transparent);
  border-radius: calc(var(--radius) * 0.6);
  background: color-mix(in srgb, var(--surface-ink) 3%, #fff);
  color: var(--surface-ink);
  font-size: 13px;
  font-family: inherit;
  outline: none;
}

.ff__control::placeholder {
  color: color-mix(in srgb, var(--surface-ink) 34%, transparent);
}

.ff__control--area {
  resize: none;
  min-height: 72px;
  line-height: 1.6;
}

.ff__control:focus {
  border-color: color-mix(in srgb, var(--brand) 60%, transparent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand) 12%, transparent);
}

.ff__control.is-error {
  border-color: #f43f5e;
  box-shadow: 0 0 0 3px rgba(244, 63, 94, 0.14);
}

.ff__error {
  font-style: normal;
  font-size: 10.5px;
  color: #f43f5e;
}

.ff__control[readonly] {
  cursor: default;
}

.form-consent {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 14px;
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--ink) 12%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--ink) 4%, var(--surface));
  color: var(--surface-muted);
  font-size: 12.5px;
  line-height: 1.65;
  cursor: pointer;
}

.form-consent input {
  width: 16px;
  height: 16px;
  flex: none;
  margin-top: 1px;
  accent-color: var(--brand-600);
}

.form-consent strong {
  display: block;
  margin-bottom: 2px;
  color: var(--surface-ink);
  font-size: 12px;
}

.cta--submit {
  width: 100%;
  margin-top: 14px;
}

.formcard__hint {
  margin-top: 9px;
  text-align: center;
  font-size: 10.5px;
  color: var(--surface-muted);
}

/* ------------------------------- 单选 / 多选 ------------------------------- */
.choice {
  background: var(--surface);
  border-radius: var(--radius);
  padding: 15px 16px;
  color: var(--surface-ink);
  border: 1px solid color-mix(in srgb, var(--ink) 7%, transparent);
}

.choice.is-error {
  border-color: #f43f5e;
}

.choice__question {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  font-weight: 700;
}

.choice__hint {
  margin-top: 5px;
  font-size: 10.5px;
  color: var(--surface-muted);
}

.choice__list {
  list-style: none;
  margin: 11px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.opt {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--ink) 10%, transparent);
  border-radius: calc(var(--radius) * 0.6);
  background: transparent;
  color: var(--surface-ink);
  font-size: 13px;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}

.opt.is-on {
  border-color: color-mix(in srgb, var(--brand) 55%, transparent);
  background: color-mix(in srgb, var(--brand) 8%, transparent);
}

.opt__mark {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  border: 1.5px solid color-mix(in srgb, var(--ink) 24%, transparent);
  border-radius: 50%;
  color: #fff;
  background: transparent;
}

.opt--check .opt__mark {
  border-radius: 5px;
}

.opt.is-on .opt__mark {
  border-color: var(--brand);
  background: var(--brand);
}

.opt__text {
  min-width: 0;
}

/* ------------------------------- 二维码 ------------------------------- */
.qrbox {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.qrbox__code {
  padding: 10px;
  border-radius: calc(var(--radius) * 0.7);
  background: #fff;
  box-shadow: 0 10px 24px -16px rgba(15, 23, 42, 0.5);
  border: 1px solid color-mix(in srgb, var(--ink) 6%, transparent);
}

.qrbox__label {
  font-size: 11.5px;
  color: var(--surface-muted);
  text-align: center;
}

/* ------------------------------- 分割线 ------------------------------- */
.hr {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hr__line {
  flex: 1;
  height: 1px;
  background: color-mix(in srgb, var(--ink) 14%, transparent);
}

.hr__text {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--surface-muted);
}

/* ------------------------------- 倒计时 ------------------------------- */
.cd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 15px;
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--brand), var(--accent));
  color: #fff;
  overflow: hidden;
}

.cd__label {
  font-size: 11.5px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.82);
}

.cd__nums {
  display: flex;
  gap: 5px;
}

.cd__num {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  padding: 3px 6px;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.cd__num em {
  font-style: normal;
  font-size: 9px;
  font-weight: 500;
  opacity: 0.78;
}
</style>
