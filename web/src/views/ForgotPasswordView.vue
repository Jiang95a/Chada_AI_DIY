<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
import { requestPasswordReset } from '@/services/auth'

const email = ref('')
const loading = ref(false)
const submitted = ref(false)
const message = ref('')
const errorText = ref('')
const resendLeft = ref(0)
let resendTimer: number | undefined

function startCooldown(seconds = 60): void {
  if (resendTimer !== undefined) window.clearInterval(resendTimer)
  resendLeft.value = seconds
  resendTimer = window.setInterval(() => {
    resendLeft.value = Math.max(0, resendLeft.value - 1)
    if (resendLeft.value === 0 && resendTimer !== undefined) {
      window.clearInterval(resendTimer)
      resendTimer = undefined
    }
  }, 1000)
}

async function send(): Promise<void> {
  if (loading.value) return
  loading.value = true
  errorText.value = ''
  try {
    const result = await requestPasswordReset(email.value.trim())
    message.value = result.message
    submitted.value = true
    startCooldown()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '发送失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  await send()
}

async function resend(): Promise<void> {
  if (resendLeft.value > 0) return
  await send()
}

function editEmail(): void {
  submitted.value = false
  message.value = ''
  resendLeft.value = 0
  if (resendTimer !== undefined) {
    window.clearInterval(resendTimer)
    resendTimer = undefined
  }
}

onBeforeUnmount(() => {
  if (resendTimer !== undefined) window.clearInterval(resendTimer)
})
</script>

<template>
  <AuthShell title="找回密码" description="输入注册邮箱。若账号存在，我们会发送一次性的密码重置链接。">
    <div v-if="submitted" class="forgot-result">
      <p class="auth-success">{{ message }}</p>
      <p class="auth-hint">已提交邮箱：<strong>{{ email }}</strong></p>
      <p class="auth-hint">请检查收件箱和垃圾邮件目录。链接有时效，并且只能使用一次。</p>
      <p v-if="errorText" class="auth-error">{{ errorText }}</p>
      <button class="auth-submit" type="button" :disabled="loading || resendLeft > 0" @click="resend">
        {{ loading ? '正在重新发送…' : resendLeft > 0 ? `${resendLeft} 秒后可重新发送` : '重新发送' }}
      </button>
      <button class="auth-link" type="button" @click="editEmail">换一个邮箱</button>
    </div>
    <form v-else class="auth-form" @submit.prevent="submit">
      <label class="auth-field">
        <span>注册邮箱</span>
        <input
          v-model="email"
          required
          type="email"
          autocomplete="email"
          placeholder="name@example.com"
          :disabled="loading"
        />
      </label>
      <p class="auth-hint">无论邮箱是否注册，页面都会显示相同提示，避免泄露账号信息。</p>
      <p v-if="errorText" class="auth-error">{{ errorText }}</p>
      <button class="auth-submit" type="submit" :disabled="loading">
        {{ loading ? '正在提交…' : '发送重置方式' }}
      </button>
    </form>

    <div class="auth-links">
      <RouterLink class="auth-link" to="/login">返回登录</RouterLink>
      <RouterLink class="auth-link" to="/help">需要帮助</RouterLink>
    </div>
  </AuthShell>
</template>
