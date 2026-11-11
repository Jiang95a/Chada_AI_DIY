import { apiRequest, clearTokens, readData, setTokens } from './http'

export interface AuthUser {
  id: string
  displayName: string
  email: string
  status?: string
}

interface AuthPayload {
  accessToken: string
  refreshToken?: string
  user: AuthUser
}

export interface PasswordResetRequestResult {
  message: string
  expiresInMinutes?: number
}

export async function login(email: string, password: string): Promise<AuthPayload> {
  const payload = readData<AuthPayload>(
    await apiRequest(
      '/auth/login',
      {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      },
      { auth: false }
    )
  )
  setTokens(payload.accessToken, payload.refreshToken)
  return payload
}

export async function register(email: string, password: string, displayName: string): Promise<AuthPayload> {
  const payload = readData<AuthPayload>(
    await apiRequest(
      '/auth/register',
      {
        method: 'POST',
        body: JSON.stringify({ email, password, displayName }),
      },
      { auth: false }
    )
  )
  setTokens(payload.accessToken, payload.refreshToken)
  return payload
}

export async function getMe(): Promise<AuthUser> {
  return readData<AuthUser>(await apiRequest('/auth/me'))
}

export async function updateMe(displayName: string): Promise<AuthUser> {
  return readData<AuthUser>(
    await apiRequest('/auth/me', {
      method: 'PUT',
      body: JSON.stringify({ displayName }),
    })
  )
}

/**
 * 请求发送密码重置邮件。
 *
 * 后端必须对“邮箱存在”和“邮箱不存在”返回同样的成功文案，避免账号枚举。
 * 令牌只出现在邮件链接中，不会返回给前端页面。
 */
export async function requestPasswordReset(email: string): Promise<PasswordResetRequestResult> {
  const data = readData<Partial<PasswordResetRequestResult>>(
    await apiRequest(
      '/auth/forgot-password',
      {
        method: 'POST',
        body: JSON.stringify({ email }),
      },
      { auth: false }
    )
  )
  return {
    message: data?.message || '如果该邮箱已注册，我们会发送密码重置方式。',
    expiresInMinutes: data?.expiresInMinutes,
  }
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await apiRequest(
    '/auth/reset-password',
    {
      method: 'POST',
      body: JSON.stringify({ token, newPassword }),
    },
    { auth: false }
  )
}

export async function logout(): Promise<void> {
  try {
    await apiRequest('/auth/logout', { method: 'POST' })
  } finally {
    clearTokens()
  }
}
