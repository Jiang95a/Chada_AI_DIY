import { afterEach, describe, expect, it, vi } from 'vitest'
import { defaultActivityPrivacy } from '@/composables/useActivityPrivacy'
import { getProjectPrivacy, updateProjectPrivacy } from '@/services/privacy'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('活动隐私设置', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('从后端读取并统一使用 allowExport', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          privacyMode: 'anonymous',
          consentRequired: true,
          consentText: '参与说明',
          retentionDays: 7,
          allowExport: false,
          allowLeave: true,
          quietSpace: true,
        },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const settings = await getProjectPrivacy('project-1')
    expect(settings.privacyMode).toBe('anonymous')
    expect(settings.allowExport).toBe(false)
    expect(settings.quietSpace).toBe(true)
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/projects/project-1/privacy')
  })

  it('保存时提交后端字段名', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: { ...defaultActivityPrivacy(), privacyMode: 'real_name', allowExport: true },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    await updateProjectPrivacy('project-2', {
      ...defaultActivityPrivacy(),
      privacyMode: 'real_name',
      allowExport: true,
    })

    const body = JSON.parse(String((fetchMock.mock.calls[0]?.[1] as RequestInit).body)) as Record<string, unknown>
    expect(body.allowExport).toBe(true)
    expect(body).not.toHaveProperty('exportAllowed')
  })
})
