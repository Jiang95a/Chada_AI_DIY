import type { InjectionKey } from 'vue'

/**
 * 预览态的表单上下文：答案 + 校验错误由 PhoneScreen 统一持有，
 * 各个表单组件只负责读写，避免十来层 prop 透传。
 */
export interface FormContext {
  answers: Record<string, string | string[]>
  errors: Record<string, boolean>
  setAnswer: (key: string, value: string | string[]) => void
  clearError: (key: string) => void
  /** 公开活动页才有：同意说明直接渲染在表单提交按钮上方 */
  readonly consentRequired?: boolean
  readonly consentAccepted?: boolean
  readonly consentText?: string
  setConsent?: (accepted: boolean) => void
  /** 校验失败时展示在提交按钮上方的一句汇总 */
  readonly errorSummary?: string
  readonly submitting?: boolean
}

export const FORM_CONTEXT: InjectionKey<FormContext> = Symbol('form-context')
