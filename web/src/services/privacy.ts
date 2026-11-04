import type { ActivityPrivacySettings } from '@/composables/useActivityPrivacy'
import { apiRequest, readData } from './http'

export type ProjectPrivacySettings = ActivityPrivacySettings

function normalizePrivacy(input: Partial<ProjectPrivacySettings>): ProjectPrivacySettings {
  return {
    privacyMode: input.privacyMode ?? 'optional',
    consentRequired: Boolean(input.consentRequired),
    consentText: input.consentText ?? '',
    retentionDays: Math.max(1, Number(input.retentionDays) || 30),
    allowExport: Boolean(input.allowExport),
    allowLeave: input.allowLeave ?? true,
    quietSpace: Boolean(input.quietSpace),
  }
}

export async function getProjectPrivacy(projectId: string): Promise<ProjectPrivacySettings> {
  return normalizePrivacy(
    readData<Partial<ProjectPrivacySettings>>(await apiRequest(`/projects/${encodeURIComponent(projectId)}/privacy`))
  )
}

export async function updateProjectPrivacy(
  projectId: string,
  settings: ProjectPrivacySettings
): Promise<ProjectPrivacySettings> {
  return normalizePrivacy(
    readData<Partial<ProjectPrivacySettings>>(
      await apiRequest(`/projects/${encodeURIComponent(projectId)}/privacy`, {
        method: 'PUT',
        body: JSON.stringify({
          privacyMode: settings.privacyMode,
          consentRequired: settings.consentRequired,
          consentText: settings.consentText,
          retentionDays: settings.retentionDays,
          allowExport: settings.allowExport,
          allowLeave: settings.allowLeave,
          quietSpace: settings.quietSpace,
        }),
      })
    )
  )
}
