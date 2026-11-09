import { apiRequest, readData } from './http'

export interface PortfolioItem {
  projectId: string
  title: string
  activityType: string
  category: string
  slug: string
  publicUrl: string
  role: 'organizer' | 'participant' | 'anonymous'
  participationPath: string
  firstJoinedAt: string
  lastJoinedAt: string
  joinedCount: number
  privacyMode: 'real_name' | 'optional' | 'anonymous'
  consentRecorded: boolean
}

export interface Portfolio {
  userId: string
  displayName: string
  totalActivities: number
  wellbeingCount: number
  careerCount: number
  byMonth: Array<Record<string, unknown>>
  items: PortfolioItem[]
}

export async function getPortfolio(): Promise<Portfolio> {
  const data = readData<Portfolio>(await apiRequest('/me/portfolio'))
  return {
    ...data,
    byMonth: Array.isArray(data.byMonth) ? data.byMonth : [],
    items: Array.isArray(data.items) ? data.items : [],
  }
}
