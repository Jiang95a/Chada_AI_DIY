<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { getAccessToken } from '@/services/http'
import { usePreferences, type ThemePreference } from '@/composables/usePreferences'
import { useToast } from '@/composables/useToast'
import atmosphereImage from '@/assets/photos/activity-garden.jpg'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const authenticated = computed(() => Boolean(getAccessToken()))
const { theme, setTheme } = usePreferences()
const themeOptions: Array<{ value: ThemePreference; label: string }> = [
  { value: 'sage', label: '鼠尾草绿' },
  { value: 'oat', label: '燕麦米黄' },
  { value: 'mist', label: '雾青' },
  { value: 'slate', label: '雾蓝石板' },
  { value: 'peach', label: '浅杏桃' },
  { value: 'butter', label: '奶油黄' },
  { value: 'sky', label: '晴空蓝' },
]
const themeLabel = computed(
  () => themeOptions.find((item) => item.value === theme.value)?.label ?? '鼠尾草绿'
)
const showAtmosphere = computed(() =>
  ['/', '/campus', '/tools', '/resources', '/about', '/trace'].includes(route.path)
)

function goWorkspace(): void {
  void router.push(authenticated.value ? '/projects' : { path: '/login', query: { redirect: '/projects' } })
}

function cycleTheme(): void {
  const index = themeOptions.findIndex((item) => item.value === theme.value)
  const next = themeOptions[(index + 1) % themeOptions.length]
  if (!next) return
  setTheme(next.value)
  toast.pushToast(`已切换为${next.label}`, 'success')
}
</script>

<template>
  <div class="public-shell">
    <div v-if="showAtmosphere" class="public-atmosphere" aria-hidden="true">
      <img :src="atmosphereImage" alt="" />
    </div>
    <header class="public-nav">
      <RouterLink class="public-brand" to="/">
        <span><i /><b /></span>
        <div><strong>心屿同行</strong><small>校园心理社团服务平台</small></div>
      </RouterLink>
      <nav>
        <RouterLink to="/" :class="{ 'is-active': route.path === '/' }">首页</RouterLink>
        <RouterLink to="/campus" :class="{ 'is-active': route.path === '/campus' }">
          <Icon name="calendar" :size="14" />心理活动
        </RouterLink>
        <RouterLink to="/tools" :class="{ 'is-active': route.path === '/tools' }">
          <Icon name="spark" :size="14" />心晴工具
        </RouterLink>
        <RouterLink to="/resources" :class="{ 'is-active': route.path === '/resources' }">
          <Icon name="help" :size="14" />心理资源
        </RouterLink>
        <RouterLink to="/trace" :class="{ 'is-active': route.path === '/trace' }">
          <Icon name="clock" :size="14" />我的轨迹
        </RouterLink>
        <RouterLink to="/about" :class="{ 'is-active': route.path === '/about' }">
          <Icon name="users" :size="14" />关于心屿
        </RouterLink>
      </nav>
      <div class="public-nav-actions">
        <button
          class="public-theme"
          type="button"
          :aria-label="`切换配色，当前为${themeLabel}`"
          :title="`切换配色：${themeLabel}`"
          @click="cycleTheme"
        >
          <span :class="`is-${theme}`" />
        </button>
        <button class="public-workspace" type="button" @click="goWorkspace">
          {{ authenticated ? '进入运营台' : '登录' }}
          <Icon name="arrow-right" :size="14" />
        </button>
      </div>
    </header>

    <slot />

    <nav class="public-mobile-nav" aria-label="移动端公共导航">
      <RouterLink to="/" :class="{ 'is-active': route.path === '/' }"><Icon name="home" :size="17" />首页</RouterLink>
      <RouterLink to="/campus" :class="{ 'is-active': route.path === '/campus' }"
        ><Icon name="calendar" :size="17" />活动</RouterLink
      >
      <RouterLink to="/tools" :class="{ 'is-active': route.path === '/tools' }"
        ><Icon name="spark" :size="17" />工具</RouterLink
      >
      <RouterLink to="/resources" :class="{ 'is-active': route.path === '/resources' }"
        ><Icon name="help" :size="17" />资源</RouterLink
      >
      <RouterLink to="/trace" :class="{ 'is-active': route.path === '/trace' }"
        ><Icon name="clock" :size="17" />轨迹</RouterLink
      >
      <RouterLink to="/about" :class="{ 'is-active': route.path === '/about' }"
        ><Icon name="users" :size="17" />关于</RouterLink
      >
    </nav>

    <footer class="public-footer">
      <div>
        <RouterLink class="public-brand" to="/">
          <span><i /><b /></span>
          <div><strong>心屿同行</strong><small>让参与更安心</small></div>
        </RouterLink>
        <p>我们支持校园心理活动的组织与参与，不提供心理诊断或治疗建议。</p>
      </div>
      <div class="public-footer-links">
        <RouterLink to="/campus">心晴活动</RouterLink>
        <RouterLink to="/tools">心晴工具</RouterLink>
        <RouterLink to="/resources">心理资源</RouterLink>
        <RouterLink to="/about">关于心屿</RouterLink>
        <RouterLink to="/help">使用帮助</RouterLink>
      </div>
      <div class="public-emergency">
        <Icon name="alert" :size="15" />
        <span>如有立即危险，请联系身边可信任的人，并拨打 <strong>120 / 110</strong>。</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.public-shell {
  position: relative;
  min-height: 100vh;
  padding-top: 96px;
  background: transparent;
}

.public-atmosphere {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.public-atmosphere img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 42%;
  opacity: 0.42;
  filter: saturate(0.74) contrast(0.94);
  -webkit-mask-image: radial-gradient(ellipse at center, #000 0%, rgb(0 0 0 / 82%) 52%, transparent 100%);
  mask-image: radial-gradient(ellipse at center, #000 0%, rgb(0 0 0 / 82%) 52%, transparent 100%);
}

.public-atmosphere::after {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--brand-100) 52%, transparent);
  content: '';
}

.public-shell > :not(.public-atmosphere):not(.public-nav):not(.public-mobile-nav) {
  position: relative;
  z-index: 1;
}

.public-nav {
  position: fixed;
  z-index: 50;
  top: 8px;
  left: 50%;
  display: grid;
  width: min(1280px, calc(100% - 36px));
  min-height: 74px;
  align-items: center;
  gap: 22px;
  padding: 0 28px;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--ink-900);
  background: var(--surface);
  box-shadow: 0 16px 40px -32px rgb(24 24 27 / 28%);
  backdrop-filter: blur(20px);
  transform: translateX(-50%);
  grid-template-columns: auto 1fr auto;
}

.public-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.public-brand > span {
  position: relative;
  display: grid;
  width: 42px;
  height: 42px;
  border-radius: 50% 50% 46% 54%;
  background: var(--brand-400);
  place-items: center;
}

.public-brand > span i,
.public-brand > span b {
  position: absolute;
  width: 16px;
  height: 8px;
  border: 1.5px solid #fff;
  border-radius: 50%;
}

.public-brand > span i {
  transform: translate(-5px, -2px) rotate(-28deg);
}

.public-brand > span b {
  transform: translate(5px, 3px) rotate(-28deg);
}

.public-brand > div {
  display: flex;
  flex-direction: column;
}

.public-brand strong {
  color: var(--ink-900);
  font-size: 15px;
}

.public-brand small {
  margin-top: 2px;
  color: var(--ink-500);
  font-size: 11px;
}

.public-nav nav {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.public-nav nav a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 34px;
  padding: 0 2px;
  color: var(--ink-600);
  font-size: 12px;
  font-weight: 650;
}

.public-nav nav a:hover,
.public-nav nav a.is-active {
  color: var(--ink-900);
}

.public-nav nav a.is-active {
  box-shadow: inset 0 -2px var(--brand-500);
}

.public-workspace {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  gap: 6px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-700);
  background: var(--surface-soft);
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition:
    background-color 160ms ease,
    border-color 160ms ease,
    color 160ms ease;
}

.public-workspace:hover {
  border-color: var(--line-strong);
  color: var(--brand-700);
  background: var(--brand-50);
}

.public-nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.public-theme {
  display: grid;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: var(--surface-soft);
  cursor: pointer;
  place-items: center;
}

.public-theme > span {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #8fb49a;
}

.public-theme > span.is-oat {
  background: #d8b88c;
}

.public-theme > span.is-mist {
  background: #79a5a1;
}

.public-theme > span.is-slate {
  background: #6c8391;
}

.public-theme > span.is-peach {
  background: #dfa18d;
}

.public-theme > span.is-butter {
  background: #d8c27c;
}

.public-theme > span.is-sky {
  background: #8ec2dd;
}

.public-footer {
  display: grid;
  width: 100%;
  max-width: none;
  gap: 26px;
  margin: 56px 0 0;
  padding: 32px max(24px, calc((100vw - 1240px) / 2)) 28px;
  border-top: 1px solid var(--line);
  border-radius: 0;
  color: var(--ink-600);
  background: var(--surface-soft);
  grid-template-columns: 1fr auto;
}

.public-footer .public-brand strong {
  color: var(--ink-900);
}

.public-footer .public-brand small {
  color: var(--ink-500);
}

.public-footer > div:first-child > p {
  max-width: 520px;
  margin-top: 14px;
  color: var(--ink-600);
  font-size: 12.5px;
  line-height: 1.8;
}

.public-footer-links {
  display: flex;
  align-content: flex-start;
  gap: 16px;
}

.public-footer-links a {
  color: var(--ink-600);
  font-size: 12px;
}

.public-footer-links a:hover {
  color: var(--brand-700);
}

.public-emergency {
  display: flex;
  align-items: center;
  gap: 7px;
  grid-column: 1 / -1;
  padding: 11px 12px;
  border: 1px solid var(--line);
  border-radius: 0;
  color: var(--ink-600);
  background: transparent;
  font-size: 12px;
}

.public-mobile-nav {
  display: none;
}

@media (max-width: 820px) {
  .public-nav {
    grid-template-columns: 1fr auto;
    padding-inline: 18px;
  }

  .public-nav nav {
    display: none;
  }

  .public-footer {
    width: 100%;
    padding-inline: 24px;
    padding-bottom: 96px;
    grid-template-columns: 1fr;
  }

  .public-mobile-nav {
    position: fixed;
    z-index: 60;
    right: 12px;
    bottom: 12px;
    left: 12px;
    display: grid;
    padding: 6px;
    border: 1px solid var(--line);
    border-radius: 10px;
    background: var(--surface);
    box-shadow: 0 18px 45px -28px rgb(24 24 27 / 32%);
    backdrop-filter: blur(20px);
    grid-template-columns: repeat(6, 1fr);
  }

  .public-mobile-nav a {
    display: flex;
    min-height: 46px;
    align-items: center;
    justify-content: center;
    gap: 3px;
    border-radius: 8px;
    color: var(--ink-500);
    font-size: 11px;
    flex-direction: column;
  }

  .public-mobile-nav a.is-active {
    color: var(--brand-700);
    background: var(--brand-50);
  }

  .public-footer-links {
    flex-wrap: wrap;
  }
}
</style>
