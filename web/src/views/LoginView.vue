<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import { useAuthStore } from '@/stores/auth'
import { getLandingPath } from '@/composables/usePreferences'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const mode = ref<'login' | 'register'>('login')
const email = ref('')
const password = ref('')
const displayName = ref('')
const localError = ref('')

async function submit(): Promise<void> {
  localError.value = ''
  try {
    if (mode.value === 'login') {
      await auth.login(email.value.trim(), password.value)
    } else {
      await auth.register(email.value.trim(), password.value, displayName.value.trim())
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : getLandingPath()
    await router.replace(redirect)
  } catch (err) {
    localError.value = err instanceof Error ? err.message : '操作失败'
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <div class="login-brand">
        <span><Icon name="spark" :size="20" /></span>
        <div><strong>心屿同行</strong><small>校园心理社团服务平台</small></div>
      </div>
      <p class="login-eyebrow">CAMPUS WELLBEING STUDIO</p>
      <h1>{{ mode === 'login' ? '登录心屿运营台' : '创建心晴账号' }}</h1>
      <p class="login-copy">登录后可组织心理活动、使用页面工坊、发布页面并查看参与数据。</p>

      <form class="login-form" @submit.prevent="submit">
        <label v-if="mode === 'register'">
          <span>昵称</span>
          <input v-model="displayName" required maxlength="80" placeholder="你的昵称" />
        </label>
        <label>
          <span>邮箱</span>
          <input v-model="email" required type="email" autocomplete="email" placeholder="name@example.com" />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            required
            type="password"
            minlength="8"
            autocomplete="current-password"
            placeholder="至少 8 位"
          />
        </label>
        <p v-if="localError || auth.error" class="login-error">{{ localError || auth.error }}</p>
        <button class="login-submit" type="submit" :disabled="auth.loading">
          {{ auth.loading ? '处理中...' : mode === 'login' ? '登录' : '注册' }}
        </button>
      </form>

      <button class="login-switch" type="button" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '还没有账号？立即注册' : '已有账号？返回登录' }}
      </button>
      <p class="login-server">心屿同行 · 关注参与，不做心理诊断</p>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at 15% 10%, rgb(45 80 70 / 12%), transparent 34%),
    radial-gradient(circle at 85% 90%, rgb(184 140 114 / 15%), transparent 30%), var(--bg);
  place-items: center;
}
.login-card {
  width: min(420px, 100%);
  padding: 34px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: rgb(255 255 255 / 92%);
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(18px);
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 11px;
}
.login-brand > span {
  display: grid;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  color: #fff;
  background: linear-gradient(145deg, var(--brand-500), var(--brand-700));
  place-items: center;
}
.login-brand > div {
  display: flex;
  flex-direction: column;
}
.login-brand strong {
  font-size: 15px;
}
.login-brand small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 9px;
}
.login-eyebrow {
  margin: 30px 0 8px;
  color: var(--primary);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.12em;
}
h1 {
  margin: 0;
  font-size: 29px;
  letter-spacing: -0.04em;
}
.login-copy {
  margin: 9px 0 22px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
}
.login-form {
  display: grid;
  gap: 14px;
}
.login-form label {
  display: grid;
  gap: 7px;
}
.login-form label span {
  color: var(--ink-soft);
  font-size: 10px;
  font-weight: 700;
}
.login-form input {
  height: 44px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  outline: none;
  background: var(--surface-soft);
  font-size: 12px;
}
.login-form input:focus {
  border-color: var(--brand-400);
  box-shadow: 0 0 0 3px rgb(45 80 70 / 9%);
}
.login-error {
  margin: 0;
  color: #c53b53;
  font-size: 10px;
}
.login-submit {
  height: 44px;
  margin-top: 3px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  background: var(--primary);
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
}
.login-submit:disabled {
  opacity: 0.6;
}
.login-switch {
  width: 100%;
  margin-top: 15px;
  padding: 0;
  border: 0;
  color: var(--primary);
  background: transparent;
  font-size: 10px;
  cursor: pointer;
}
.login-server {
  margin: 23px 0 0;
  color: #99a3b4;
  font-size: 8px;
  text-align: center;
}
</style>
