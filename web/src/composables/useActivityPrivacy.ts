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
