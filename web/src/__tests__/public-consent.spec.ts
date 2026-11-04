import { afterEach, describe, expect, it, vi } from 'vitest'
import { recordConsent, submitForm } from '@/services/public'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('公开活动同意记录', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('提交前创建 consentId 并随报名发送', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ data: { consentId: 'consent-1', consentAt: '2026-11-04T10:00:00Z' } }))
      .mockResolvedValueOnce(jsonResponse({ data: { submissionId: 'submission-1', status: 'valid' } }))
    vi.stubGlobal('fetch', fetchMock)

    const consent = await recordConsent('heart-garden', '参与说明')
    await submitForm('heart-garden', {
      pageId: 'page-1',
      formComponentId: 'form-1',
      answers: {},
      privacy: { consentId: consent.consentId },
    })

    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/public/activities/heart-garden/consents')
    expect(String(fetchMock.mock.calls[1]?.[0])).toContain('/public/activities/heart-garden/submissions')
    const body = JSON.parse(String((fetchMock.mock.calls[1]?.[1] as RequestInit).body)) as {
      privacy?: { consentId?: string }
    }
    expect(body.privacy?.consentId).toBe('consent-1')
  })
})
