import type { Pagination } from './analytics'
import { apiRequest } from './http'

export type SubmissionStatus = 'valid' | 'invalid'

export interface SubmissionRecord {
  id: string
  pageId: string
  formComponentId: string
  answers: Record<string, unknown>
  status: SubmissionStatus
  submittedAt: string
  privacyMode?: 'real_name' | 'optional' | 'anonymous'
  anonymous?: boolean
  anonymousToken?: string | null
  consentId?: string | null
  consentAt?: string | null
}

export interface SubmissionSummary {
  total: number
  anonymous: number
  consented: number
}

export interface SubmissionPage {
  items: SubmissionRecord[]
  pagination: Pagination
}

export async function listSubmissions(
  projectId: string,
  params: { status?: SubmissionStatus | ''; page?: number; pageSize?: number } = {}
): Promise<SubmissionPage> {
  const search = new URLSearchParams()
  if (params.status) search.set('status', params.status)
  search.set('page', String(params.page ?? 1))
  search.set('pageSize', String(params.pageSize ?? 20))
  const payload = await apiRequest<{
    data?: SubmissionRecord[]
    pagination?: Pagination
  }>(`/projects/${projectId}/submissions?${search.toString()}`)
  return {
    items: Array.isArray(payload.data) ? payload.data : [],
    pagination: payload.pagination ?? {
      page: params.page ?? 1,
      pageSize: params.pageSize ?? 20,
      total: 0,
    },
  }
}

export async function getSubmissionSummary(projectId: string): Promise<SubmissionSummary> {
  const payload = await apiRequest<{ data?: Partial<SubmissionSummary> }>(
    `/projects/${encodeURIComponent(projectId)}/submissions/summary`
  )
  return {
    total: Number(payload.data?.total) || 0,
    anonymous: Number(payload.data?.anonymous) || 0,
    consented: Number(payload.data?.consented) || 0,
  }
}
