<script setup lang="ts">
import { computed } from 'vue'

/**
 * 极简线性图标集。全部是手写的几何路径，静态字符串（不是用户内容），
 * 用 v-html 注入是安全的。
 */
const props = withDefaults(
  defineProps<{
    name: string
    size?: number | string
    strokeWidth?: number
    filled?: boolean
  }>(),
  { size: 16, strokeWidth: 1.7, filled: false }
)

const ICONS: Record<string, string> = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M9 21v-6h6v6"/>',
  chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/>',
  'arrow-right': '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
  monitor: '<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8M12 17v4"/>',
  help: '<circle cx="12" cy="12" r="9"/><path d="M9.6 9a2.6 2.6 0 1 1 3.9 2.3c-.9.5-1.5 1-1.5 2.2M12 17h.01"/>',
  spark:
    '<path d="M12 3.2l1.9 5.3 5.3 1.9-5.3 1.9L12 17.6l-1.9-5.3L4.8 10.4l5.3-1.9z"/><path d="M18.4 15.6l.7 1.9 1.9.7-1.9.7-.7 1.9-.7-1.9-1.9-.7 1.9-.7z"/>',
  cursor: '<path d="M6 3.6l13 7.6-6 1.6-1.7 6z"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  trash: '<path d="M4 7h16M9.5 7V4.8h5V7M7 7l1 13h8l1-13"/><path d="M10.5 11v5.5M13.5 11v5.5"/>',
  copy: '<rect x="9" y="9" width="10.5" height="10.5" rx="2.2"/><path d="M15 5.5H6.5A1.5 1.5 0 0 0 5 7v8.5"/>',
  up: '<path d="M12 19.5V5M6 11l6-6 6 6"/>',
  down: '<path d="M12 4.5V19M18 13l-6 6-6-6"/>',
  grip: '<circle cx="9" cy="7" r="1.25" fill="currentColor" stroke="none"/><circle cx="15" cy="7" r="1.25" fill="currentColor" stroke="none"/><circle cx="9" cy="12" r="1.25" fill="currentColor" stroke="none"/><circle cx="15" cy="12" r="1.25" fill="currentColor" stroke="none"/><circle cx="9" cy="17" r="1.25" fill="currentColor" stroke="none"/><circle cx="15" cy="17" r="1.25" fill="currentColor" stroke="none"/>',
  eye: '<path d="M2.5 12S6 5.8 12 5.8 21.5 12 21.5 12 18 18.2 12 18.2 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.6"/>',
  'eye-off':
    '<path d="M3 3l18 18"/><path d="M10.6 6.1A9.6 9.6 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-2.8 3.6M6.4 8A17.4 17.4 0 0 0 2.5 12S6 18 12 18c1 0 2-.2 2.8-.5"/><path d="M9.9 10a3 3 0 0 0 4.2 4.2"/>',
  undo: '<path d="M4.5 9.5H13a5.5 5.5 0 0 1 0 11H8"/><path d="M8.5 5.5l-4 4 4 4"/>',
  redo: '<path d="M19.5 9.5H11a5.5 5.5 0 0 0 0 11h5"/><path d="M15.5 5.5l4 4-4 4"/>',
  cloud:
    '<path d="M6.2 17.5a4.2 4.2 0 0 1 .5-8.4 5.6 5.6 0 0 1 10.8 1.1 3.7 3.7 0 0 1 .4 7.3z"/><path d="M9.6 13.4l1.9 1.9 3.5-3.7"/>',
  phone: '<rect x="7" y="2.6" width="10" height="18.8" rx="2.6"/><path d="M10.6 5.2h2.8"/>',
  qr: '<rect x="3.5" y="3.5" width="6.4" height="6.4" rx="1.4"/><rect x="14.1" y="3.5" width="6.4" height="6.4" rx="1.4"/><rect x="3.5" y="14.1" width="6.4" height="6.4" rx="1.4"/><path d="M14.1 14.1h2.4v2.4h-2.4z"/><path d="M18.4 14.1h2.1M14.1 18.4v2.1M20.5 17.4v3.1h-3.1"/>',
  download: '<path d="M12 3.6v11.2M7.8 10.8L12 15l4.2-4.2M4.8 20.2h14.4"/>',
  upload: '<path d="M12 20.4V9.2M7.8 13.2L12 9l4.2 4.2M4.8 3.8h14.4"/>',
  image:
    '<rect x="3" y="5" width="18" height="14" rx="2.6"/><circle cx="8.6" cy="10" r="1.7"/><path d="M4 17.6l4.8-4.4 3.4 3.1 2.7-2.4 4.1 3.7"/>',
  text: '<path d="M4 6.2h16M4 11.2h11.5M4 16.2h14.5M4 21h8"/>',
  heading: '<path d="M6 4.6v14.8M18 4.6v14.8M6 12h12"/>',
  nav: '<rect x="3" y="4.5" width="18" height="5.2" rx="1.8"/><path d="M3 13.6h18M3 17.6h11"/>',
  banner: '<rect x="3" y="4.2" width="18" height="9.2" rx="2.2"/><path d="M6 17h12M6 20.2h7.4"/>',
  card: '<rect x="3" y="4.6" width="18" height="14.8" rx="2.6"/><path d="M7 9.2h6.2M7 13.2h10M7 16.4h4.2"/>',
  button: '<rect x="3" y="8.2" width="18" height="7.6" rx="3.8"/><path d="M9 12h6"/>',
  form: '<rect x="3" y="4" width="18" height="16" rx="2.6"/><path d="M6.6 8.6h10.8M6.6 12h10.8M6.6 15.4h6"/>',
  radio: '<circle cx="12" cy="12" r="8.2"/><circle cx="12" cy="12" r="3.2" fill="currentColor" stroke="none"/>',
  checkbox: '<rect x="3.6" y="3.6" width="16.8" height="16.8" rx="3.4"/><path d="M7.8 12.2l2.8 2.8 5.6-6"/>',
  divider: '<path d="M3 12h18"/><path d="M7 6.4h10M7 17.6h10" opacity=".38"/>',
  calendar:
    '<rect x="3.5" y="5" width="17" height="15" rx="2.3"/><path d="M7.5 3v4M16.5 3v4M3.5 9.5h17M8 13h2M14 13h2M8 16.5h2M14 16.5h2"/>',
  clock: '<circle cx="12" cy="12" r="8.4"/><path d="M12 7.4V12l3.1 2"/>',
  alert: '<path d="M12 3.6l8.8 15.4H3.2z"/><path d="M12 9.2v4.4M12 16.4v.4"/>',
  check: '<path d="M4.6 12.6l5 5 9.8-10.4"/>',
  'check-circle': '<circle cx="12" cy="12" r="8.4"/><path d="M8.2 12.2l2.6 2.6 5-5.3"/>',
  info: '<circle cx="12" cy="12" r="8.4"/><path d="M12 11.2v5.6M12 7.9v.4"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  palette:
    '<path d="M12 3.2a8.8 8.8 0 1 0 0 17.6c1.4 0 2-.9 2-2s-.6-2 .7-2H17a4 4 0 0 0 4-4c0-4.9-4-9.6-9-9.6z"/><circle cx="8.4" cy="9.6" r="1.2" fill="currentColor" stroke="none"/><circle cx="12" cy="7" r="1.2" fill="currentColor" stroke="none"/><circle cx="15.6" cy="9.6" r="1.2" fill="currentColor" stroke="none"/>',
  send: '<path d="M21 3L10.4 13.6M21 3l-6.6 18-3.9-7.4L3 9.6z"/>',
  chevron: '<path d="M6 9.5l6 6 6-6"/>',
  grid: '<rect x="3.2" y="3.2" width="7.2" height="7.2" rx="1.8"/><rect x="13.6" y="3.2" width="7.2" height="7.2" rx="1.8"/><rect x="3.2" y="13.6" width="7.2" height="7.2" rx="1.8"/><rect x="13.6" y="13.6" width="7.2" height="7.2" rx="1.8"/>',
  file: '<path d="M13.6 3.2H7a2 2 0 0 0-2 2v13.6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8.6z"/><path d="M13.6 3.2v5.4h5.4"/>',
  refresh: '<path d="M20.6 12a8.6 8.6 0 1 1-2.7-6.2"/><path d="M20.6 4.6V10h-5.4"/>',
  users:
    '<circle cx="9.2" cy="8.2" r="3.2"/><path d="M3.6 20a5.6 5.6 0 0 1 11.2 0"/><path d="M16.2 5.4a3.2 3.2 0 0 1 0 6.2M17.8 14.8a5.6 5.6 0 0 1 3 5.2"/>',
  pin: '<path d="M12 21c4.2-4.6 6.4-7.6 6.4-10.4A6.4 6.4 0 0 0 5.6 10.6C5.6 13.4 7.8 16.4 12 21z"/><circle cx="12" cy="10.4" r="2.4"/>',
  external:
    '<path d="M14 3.8h6.2v6.2M20.2 3.8l-9 9"/><path d="M18.8 14v4.4a2 2 0 0 1-2 2H5.6a2 2 0 0 1-2-2V7.2a2 2 0 0 1 2-2H10"/>',
  search: '<circle cx="11" cy="11" r="6.6"/><path d="M15.8 15.8l4.6 4.6"/>',
  sliders:
    '<path d="M4 7.2h8.4M17.6 7.2H20M4 16.8h5.2M14.4 16.8H20"/><circle cx="15.2" cy="7.2" r="2.1"/><circle cx="12" cy="16.8" r="2.1"/>',
  layout:
    '<rect x="3.2" y="3.6" width="7" height="16.8" rx="2"/><rect x="13.4" y="3.6" width="7.4" height="8" rx="2"/><rect x="13.4" y="14.4" width="7.4" height="6" rx="2"/>',
  zap: '<path d="M13.4 3.2L5.4 14h5.4l-1 6.8 8-10.8h-5.4z"/>',
  wand: '<path d="M5 19L15.6 8.4"/><path d="M14.2 4.2l.9 2.7 2.7.9-2.7.9-.9 2.7-.9-2.7-2.7-.9 2.7-.9z"/><path d="M18.4 13.6l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z"/>',
  layers: '<path d="M12 3.4l8.6 4.8-8.6 4.8L3.4 8.2z"/><path d="M3.4 12.4l8.6 4.8 8.6-4.8M3.4 16.4l8.6 4.8 8.6-4.8"/>',
  rocket:
    '<path d="M13.6 4.4c3.2-1.4 6-.6 6-.6s.8 2.8-.6 6c-1.2 2.8-4.2 5.4-6.4 6.6l-5.6-5.6c1.2-2.2 3.8-5.2 6.6-6.4z"/><circle cx="14.6" cy="9.4" r="1.7"/><path d="M7.8 15.2c-1.6.6-2.6 3.4-2.6 3.4s2.8-1 3.4-2.6"/>',
  more: '<circle cx="5.5" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="18.5" cy="12" r="1.4" fill="currentColor" stroke="none"/>',
  lock: '<rect x="4.8" y="10.4" width="14.4" height="10" rx="2.4"/><path d="M8.2 10.4V7.8a3.8 3.8 0 0 1 7.6 0v2.6"/>',
  tag: '<path d="M12.4 3.4H20v7.6l-8.6 8.6a1.8 1.8 0 0 1-2.6 0L3.4 14.2a1.8 1.8 0 0 1 0-2.6z"/><circle cx="16.4" cy="7.6" r="1.3"/>',
  play: '<path d="M8 5.2l10 6.8-10 6.8z"/>',
  bolt: '<path d="M12 2.6v5M12 16.4v5M4.6 12h4.8M14.6 12h4.8M6.8 6.8l3.2 3.2M14 14l3.2 3.2M17.2 6.8L14 10M10 14l-3.2 3.2"/>',
  dot: '<circle cx="12" cy="12" r="4" fill="currentColor" stroke="none"/>',
}

const markup = computed(() => ICONS[props.name] ?? ICONS.dot ?? '')
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    :fill="filled ? 'currentColor' : 'none'"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    v-html="markup"
  />
</template>
