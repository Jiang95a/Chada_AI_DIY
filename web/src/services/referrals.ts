import { apiRequest, readData } from './http'

export interface ReferralTodo {
  id: string
  activityTitle: string
  publicSlug: string
  contact: string | null
  preferredTime: string | null
  status: 'pending' | 'contacted' | 'closed'
  createdAt: string
  handledAt: string | null
  pendingHours: number
}

export async function listReferrals(params?: { status?: string; limit?: number }): Promise<ReferralTodo[]> {
  const query = new URLSearchParams()
  if (params?.status) query.set('status', params.status)
  if (params?.limit) query.set('limit', String(params.limit))
  const suffix = query.toString() ? `?${query}` : ''
  const data = readData<{ items?: ReferralTodo[] }>(await apiRequest(`/referrals${suffix}`))
  return Array.isArray(data.items) ? data.items : []
}

export async function updateReferralStatus(id: string, status: 'contacted' | 'closed') {
  return readData<{ id: string; status: string; handledAt: string }>(
    await apiRequest(`/referrals/${encodeURIComponent(id)}/status`, {
      method: 'POST',
      body: JSON.stringify({ status }),
    })
  )
}
