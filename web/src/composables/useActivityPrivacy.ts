export type PrivacyMode = 'real_name' | 'optional' | 'anonymous'

export interface ActivityPrivacySettings {
  privacyMode: PrivacyMode
  consentRequired: boolean
  consentText: string
  retentionDays: number
  exportAllowed: boolean
  organizerName: string
  organizerIntro: string
  allowLeave: boolean
  quietSpace: boolean
  updatedAt: number
}

const STORAGE_KEY = 'diy-campus:activity-privacy:v1'

function defaults(): ActivityPrivacySettings {
  return {
    privacyMode: 'optional',
    consentRequired: true,
    consentText: '我知道本次活动只收集参与所需的信息，信息仅由活动组织者查看，不用于心理诊断或未经同意的公开传播。',
    retentionDays: 30,
    exportAllowed: false,
    organizerName: '',
    organizerIntro: '',
    allowLeave: true,
    quietSpace: false,
    updatedAt: 0,
  }
}

function readStore(): Record<string, ActivityPrivacySettings> {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') as unknown
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, ActivityPrivacySettings>) : {}
  } catch {
    return {}
  }
}

export function activityPrivacyKeys(projectId: string, slug: string): string[] {
  return [projectId, slug].filter(Boolean)
}

export function getActivityPrivacy(projectId: string, slug: string): ActivityPrivacySettings {
  const store = readStore()
  for (const key of activityPrivacyKeys(projectId, slug)) {
    if (store[key]) return { ...defaults(), ...store[key] }
  }
  return defaults()
}

export function saveActivityPrivacy(
  projectId: string,
  slug: string,
  settings: ActivityPrivacySettings
): ActivityPrivacySettings {
  const store = readStore()
  const next = { ...settings, updatedAt: Date.now() }
  for (const key of activityPrivacyKeys(projectId, slug)) {
    store[key] = next
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
  return next
}

export function clearActivityPrivacy(projectId: string, slug: string): void {
  const store = readStore()
  for (const key of activityPrivacyKeys(projectId, slug)) delete store[key]
  localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
}
