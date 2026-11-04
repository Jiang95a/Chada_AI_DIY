import type { Doc } from '@/types/editor'
import { normalizeDoc } from '@/utils/doc'
import { apiRequest, readData } from './http'

export interface PublicActivity {
  activity: {
    name: string
    slug: string
    status: string
    publishedAt?: string
  }
  document: Doc
}

export interface PublicActivitySummary {
  slug: string
  title: string
  description?: string
  activityType?: string
  coverTone?: string
  coverCode?: string
  coverTitle?: string
  status: string
  updatedAt?: string
}

export async function getPublicActivity(slug: string): Promise<PublicActivity> {
  const data = readData<{
    slug?: string
    title?: string
    status?: string
    activity?: PublicActivity['activity']
    document?: unknown
  }>(await apiRequest(`/public/activities/${encodeURIComponent(slug)}`, {}, { auth: false }))
  const document = normalizeDoc(data.document)
  if (!document) throw new Error('活动不存在或发布内容无效')
  return {
    activity: data.activity ?? {
      name: data.title ?? document.title,
      slug: data.slug ?? slug,
      status: data.status ?? 'published',
    },
    document,
  }
}

export async function listPublicActivities(
  params: {
    q?: string
    activityType?: string
    page?: number
    pageSize?: number
  } = {}
): Promise<PublicActivitySummary[]> {
  const search = new URLSearchParams()
  if (params.q) search.set('q', params.q)
  if (params.activityType) search.set('activityType', params.activityType)
  search.set('page', String(params.page ?? 1))
  search.set('pageSize', String(params.pageSize ?? 50))
  const payload = await apiRequest<{ data?: PublicActivitySummary[] }>(
    `/public/activities?${search.toString()}`,
    {},
    { auth: false }
  )
  return Array.isArray(payload.data) ? payload.data : []
}

export async function submitForm(
  slug: string,
  input: {
    pageId: string
    formComponentId: string
    answers: Record<string, string | string[]>
    privacy?: { consentId?: string; anonymous?: boolean }
  }
): Promise<{ submissionId: string; status: string }> {
  const idempotencyKey = crypto.randomUUID()
  return readData(
    await apiRequest(
      `/public/activities/${encodeURIComponent(slug)}/submissions`,
      {
        method: 'POST',
        body: JSON.stringify({ ...input, idempotencyKey }),
      },
      { auth: false }
    )
  )
}

export async function getPublicPrivacy(slug: string) {
  return readData<{
    privacyMode: 'real_name' | 'optional' | 'anonymous'
    consentRequired: boolean
    consentText: string
    retentionDays: number
    allowExport: boolean
    allowLeave: boolean
    quietSpace: boolean
  }>(await apiRequest(`/public/activities/${encodeURIComponent(slug)}/privacy`, {}, { auth: false }))
}

export async function recordConsent(slug: string, consentText?: string) {
  return readData<{ consentId: string; consentAt: string }>(
    await apiRequest(
      `/public/activities/${encodeURIComponent(slug)}/consents`,
      {
        method: 'POST',
        body: JSON.stringify({ consentText }),
      },
      { auth: false }
    )
  )
}

export async function submitActivityFeedback(
  slug: string,
  input: { choice: 'helpful' | 'neutral' | 'unhelpful'; comment?: string; anonymous?: boolean }
): Promise<void> {
  await apiRequest(
    `/public/activities/${encodeURIComponent(slug)}/feedback`,
    {
      method: 'POST',
      body: JSON.stringify(input),
    },
    { auth: false }
  )
}

export async function recordPublicEvent(
  slug: string,
  input: {
    eventType: 'form_started'
    pageId: string
    metadata?: Record<string, unknown>
  }
): Promise<void> {
  await apiRequest(
    `/public/activities/${encodeURIComponent(slug)}/events`,
    {
      method: 'POST',
      body: JSON.stringify(input),
    },
    { auth: false }
  )
}
