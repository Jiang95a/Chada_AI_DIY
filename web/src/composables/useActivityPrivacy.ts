export type PrivacyMode = 'real_name' | 'optional' | 'anonymous'

export interface ActivityPrivacySettings {
  privacyMode: PrivacyMode
  consentRequired: boolean
  consentText: string
  retentionDays: number
  allowExport: boolean
  allowLeave: boolean
  quietSpace: boolean
}

export function defaultActivityPrivacy(): ActivityPrivacySettings {
  return {
    privacyMode: 'optional',
    consentRequired: true,
    consentText: '我知道本次活动只收集参与所需的信息，信息仅由活动组织者查看，不用于心理诊断或未经同意的公开传播。',
    retentionDays: 30,
    allowExport: false,
    allowLeave: true,
    quietSpace: false,
  }
}

/**
 * 合并服务端返回的隐私设置。
 *
 * 老版本 `/public/activities/:slug/privacy` 没有 `consentRequired` 字段，
 * 如果直接整体替换，会把默认的 true 变成 undefined，页面就不渲染同意说明，
 * 但后端提交时仍然要求 consentId——结果就是「提交按钮点了没反应」。
 * 所以缺字段时按「需要同意」处理。
 */
export function normalizeActivityPrivacy(
  input?: Partial<ActivityPrivacySettings> | null
): ActivityPrivacySettings {
  const base = defaultActivityPrivacy()
  if (!input) return base
  return {
    privacyMode: input.privacyMode ?? base.privacyMode,
    consentRequired: input.consentRequired ?? true,
    consentText: input.consentText?.trim() || base.consentText,
    retentionDays: input.retentionDays ?? base.retentionDays,
    allowExport: input.allowExport ?? base.allowExport,
    allowLeave: input.allowLeave ?? base.allowLeave,
    quietSpace: input.quietSpace ?? base.quietSpace,
  }
}
