<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { getAccessToken } from '@/services/http'

const route = useRoute()
const router = useRouter()
const authenticated = computed(() => Boolean(getAccessToken()))

function goWorkspace(): void {
  void router.push(authenticated.value ? '/projects' : { path: '/login', query: { redirect: '/projects' } })
}
</script>

<template>
  <div class="public-shell">
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
        <RouterLink to="/about" :class="{ 'is-active': route.path === '/about' }">
          <Icon name="users" :size="14" />关于心屿
        </RouterLink>
      </nav>
      <button class="public-workspace" type="button" @click="goWorkspace">
        {{ authenticated ? '进入运营台' : '登录' }}
        <Icon name="arrow-right" :size="14" />
      </button>
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
  min-height: 100vh;
  padding-top: 94px;
  background:
    radial-gradient(circle at 8% 2%, rgb(95 142 110 / 14%), transparent 24%),
    radial-gradient(circle at 92% 0%, rgb(185 130 102 / 12%), transparent 22%), #f6f3eb;
}

.public-nav {
  position: fixed;
  z-index: 50;
  top: 18px;
  left: 50%;
  display: grid;
  width: min(1280px, calc(100% - 36px));
  min-height: 62px;
  align-items: center;
  gap: 22px;
  padding: 0 24px;
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 18px;
  color: #fff;
  background: rgb(20 37 29 / 90%);
  box-shadow: 0 22px 55px -35px rgb(14 31 23 / 72%);
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
  width: 38px;
  height: 38px;
  border-radius: 50% 50% 46% 54%;
  background: linear-gradient(145deg, #d5e4d2, #6e9b7d);
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
  font-size: 15px;
}

.public-brand small {
  margin-top: 2px;
  color: rgb(255 255 255 / 38%);
  font-size: 8px;
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
  color: rgb(255 255 255 / 60%);
  font-size: 10px;
  font-weight: 650;
}

.public-nav nav a:hover,
.public-nav nav a.is-active {
  color: #fff;
}

.public-nav nav a.is-active {
  box-shadow: inset 0 -2px #b8dac4;
}

.public-workspace {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  gap: 6px;
  padding: 0 13px;
  border: 1px solid rgb(255 255 255 / 17%);
  border-radius: 999px;
  color: #fff;
  background: rgb(255 255 255 / 8%);
  font-size: 9px;
  font-weight: 700;
  cursor: pointer;
}

.public-footer {
  display: grid;
  width: 100%;
  max-width: none;
  gap: 28px;
  margin-top: 30px;
  padding: 54px max(34px, calc((100vw - 1320px) / 2)) 38px;
  border-radius: 30px 30px 0 0;
  color: rgb(255 255 255 / 46%);
  background:
    radial-gradient(circle at 82% 0%, rgb(151 188 164 / 14%), transparent 28%),
    linear-gradient(145deg, #182e23, #102219 72%);
  grid-template-columns: 1fr auto;
}

.public-mobile-nav {
  display: none;
}

.public-footer > div:first-child > p {
  max-width: 520px;
  margin-top: 19px;
  font-size: 9px;
  line-height: 1.8;
}

.public-footer-links {
  display: flex;
  align-content: flex-start;
  gap: 16px;
}

.public-footer-links a {
  color: rgb(255 255 255 / 58%);
  font-size: 9px;
}

.public-emergency {
  display: flex;
  align-items: center;
  gap: 7px;
  grid-column: 1 / -1;
  padding: 10px 12px;
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 10px;
  color: rgb(255 255 255 / 54%);
  background: rgb(255 255 255 / 5%);
  font-size: 9px;
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
    border: 1px solid rgb(255 255 255 / 9%);
    border-radius: 18px;
    background: rgb(20 37 29 / 92%);
    box-shadow: 0 18px 45px -24px rgb(14 31 23 / 60%);
    backdrop-filter: blur(20px);
    grid-template-columns: repeat(5, 1fr);
  }

  .public-mobile-nav a {
    display: flex;
    min-height: 46px;
    align-items: center;
    justify-content: center;
    gap: 3px;
    border-radius: 12px;
    color: rgb(255 255 255 / 55%);
    font-size: 7px;
    flex-direction: column;
  }

  .public-mobile-nav a.is-active {
    color: #fff;
    background: rgb(255 255 255 / 9%);
  }

  .public-footer-links {
    flex-wrap: wrap;
  }
}
</style>
