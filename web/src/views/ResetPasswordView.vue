<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { resetPassword } from '@/services/auth'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const newPassword = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const loading = ref(false)
const completed = ref(false)
const errorText = ref('')

const token = computed(() => (typeof route.query.token === 'string' ? route.query.token.trim() : ''))
const mismatch = computed(() => Boolean(confirmPassword.value) && newPassword.value !== confirmPassword.value)
const passwordChecks = computed(() => ({
  length: newPassword.value.length >= 8,
  letter: /[A-Za-z]/.test(newPassword.value),
  number: /\d/.test(newPassword.value),
}))
const strength = computed(() => Object.values(passwordChecks.value).filter(Boolean).length)
const strengthLabel = computed(() => (strength.value >= 3 ? '较强' : strength.value === 2 ? '一般' : '较弱'))

async function submit(): Promise<void> {
  errorText.value = ''
  if (!token.value) {
    errorText.value = '重置链接缺少有效令牌，请重新申请'
    return
  }
  if (newPassword.value.length < 8) {
    errorText.value = '新密码至少需要 8 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    errorText.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    await resetPassword(token.value, newPassword.value)
    completed.value = true
    toast.pushToast('密码已更新，请重新登录', 'success')
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '密码重置失败，请重新申请'
  } finally {
    loading.value = false
  }
}

function goLogin(): void {
  void router.push('/login')
}
</script>

<template>
  <AuthShell title="设置新密码" description="重置成功后，旧密码将失效。请使用新密码重新登录。">
    <div v-if="completed" class="reset-result">
      <span><Icon name="check" :size="22" /></span>
      <p class="auth-success">密码已经更新。</p>
      <button class="auth-submit" type="button" @click="goLogin">返回登录</button>
    </div>

    <div v-else-if="!token" class="reset-result">
      <p class="auth-error">这个重置链接无效或已过期。</p>
      <RouterLink class="auth-submit reset-link-button" to="/forgot-password">重新申请</RouterLink>
    </div>

    <form v-else class="auth-form" @submit.prevent="submit">
      <label class="auth-field">
        <span>新密码</span>
        <span class="auth-password-field">
          <input
            v-model="newPassword"
            required
            :type="showPassword ? 'text' : 'password'"
            minlength="8"
            maxlength="128"
            autocomplete="new-password"
            placeholder="至少 8 位"
            :disabled="loading"
          />
          <button
            class="auth-password-toggle"
            type="button"
            :aria-label="showPassword ? '隐藏新密码' : '显示新密码'"
            @click="showPassword = !showPassword"
          >
            <Icon name="eye" :size="16" />
          </button>
        </span>
      </label>
      <ul class="auth-rules">
        <li :class="{ 'is-pass': passwordChecks.length }">至少 8 位</li>
        <li :class="{ 'is-pass': passwordChecks.letter }">包含字母</li>
        <li :class="{ 'is-pass': passwordChecks.number }">包含数字</li>
      </ul>
      <div v-if="newPassword" class="auth-strength">
        <span>密码强度：{{ strengthLabel }}</span>
        <div class="auth-strength-bars" aria-hidden="true">
          <i v-for="index in 3" :key="index" :class="{ 'is-pass': index <= strength }" />
        </div>
      </div>
      <label class="auth-field">
        <span>确认新密码</span>
        <span class="auth-password-field">
          <input
            v-model="confirmPassword"
            required
            :type="showConfirmPassword ? 'text' : 'password'"
            minlength="8"
            maxlength="128"
            autocomplete="new-password"
            placeholder="再次输入新密码"
            :disabled="loading"
            :aria-invalid="mismatch"
          />
          <button
            class="auth-password-toggle"
            type="button"
            :aria-label="showConfirmPassword ? '隐藏确认密码' : '显示确认密码'"
            @click="showConfirmPassword = !showConfirmPassword"
          >
            <Icon name="eye" :size="16" />
          </button>
        </span>
      </label>
      <p v-if="mismatch" class="auth-error">两次输入的密码不一致</p>
      <p v-if="errorText" class="auth-error">{{ errorText }}</p>
      <p class="auth-hint">链接仅可使用一次。重置成功后，请使用新密码重新登录。</p>
      <button class="auth-submit" type="submit" :disabled="loading">
        {{ loading ? '正在更新…' : '更新密码' }}
      </button>
    </form>

    <div class="auth-links">
      <RouterLink class="auth-link" to="/login">返回登录</RouterLink>
      <RouterLink class="auth-link" to="/forgot-password">重新申请</RouterLink>
    </div>
  </AuthShell>
</template>

<style scoped>
.reset-result {
  display: grid;
  gap: 12px;
}

.reset-result > span {
  display: grid;
  width: 44px;
  height: 44px;
  color: var(--brand-700);
  background: var(--brand-100);
  place-items: center;
}

.reset-link-button {
  display: flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
}
</style>
