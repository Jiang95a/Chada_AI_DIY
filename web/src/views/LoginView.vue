<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useAuthStore } from '@/stores/auth'
import { getLandingPath } from '@/composables/usePreferences'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const mode = ref<'login' | 'register'>('login')
const email = ref('')
const password = ref('')
const showPassword = ref(false)
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
  } catch (error) {
    localError.value = error instanceof Error ? error.message : '操作失败'
  }
}
</script>

<template>
  <AuthShell
    :title="mode === 'login' ? '登录心屿运营台' : '创建心屿账号'"
    description="登录后可组织心理活动、使用页面工坊、发布页面并查看参与数据。"
  >
    <form class="auth-form" @submit.prevent="submit">
      <label v-if="mode === 'register'" class="auth-field">
        <span>昵称</span>
        <input v-model="displayName" required maxlength="80" placeholder="你的昵称" />
      </label>
      <label class="auth-field">
        <span>邮箱</span>
        <input v-model="email" required type="email" autocomplete="email" placeholder="name@example.com" />
      </label>
      <label class="auth-field">
        <span>密码</span>
        <span class="auth-password-field">
          <input
            v-model="password"
            required
            :type="showPassword ? 'text' : 'password'"
            minlength="8"
            autocomplete="current-password"
            placeholder="至少 8 位"
          />
          <button
            class="auth-password-toggle"
            type="button"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >
            <Icon name="eye" :size="16" />
          </button>
        </span>
      </label>
      <p v-if="localError || auth.error" class="auth-error">{{ localError || auth.error }}</p>
      <button class="auth-submit" type="submit" :disabled="auth.loading">
        {{ auth.loading ? '处理中…' : mode === 'login' ? '登录' : '注册' }}
      </button>
    </form>

    <div class="auth-links">
      <button class="auth-link" type="button" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '创建账号' : '返回登录' }}
      </button>
      <RouterLink v-if="mode === 'login'" class="auth-link" to="/forgot-password">忘记密码？</RouterLink>
    </div>
    <p class="auth-footer-note">心屿同行 · 校园心理活动运营平台</p>
  </AuthShell>
</template>
