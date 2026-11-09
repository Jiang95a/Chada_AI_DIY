import { afterEach, describe, expect, it, vi } from 'vitest'
import { getPortfolio } from '@/services/portfolio'
import { requestReferral } from '@/services/public'
import { listReferrals, updateReferralStatus } from '@/services/referrals'

function jsonResponse(data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('方案 B 前端服务', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('转介工单只提交联系意愿', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        jsonResponse({
          data: { referralId: 'r1', status: 'pending', createdAt: '2026-11-09T10:00:00Z', message: '已收到' },
        })
      )
    vi.stubGlobal('fetch', fetchMock)

    await requestReferral('heart-garden', {
      wantsContact: true,
      contact: '微信 xinyu',
      preferredTime: '周五下午',
    })

    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/public/activities/heart-garden/referrals')
    const body = JSON.parse(String((fetchMock.mock.calls[0]?.[1] as RequestInit).body)) as Record<string, unknown>
    expect(body.wantsContact).toBe(true)
    expect(body).not.toHaveProperty('answers')
  })

  it('组织者读取转介待办并更新状态', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          data: {
            items: [
              {
                id: 'r1',
                activityTitle: '心晴团辅',
                publicSlug: 'heart-group',
                contact: '微信 xinyu',
                preferredTime: '周五下午',
                status: 'pending',
                createdAt: '2026-11-09T10:00:00Z',
                handledAt: null,
                pendingHours: 30,
              },
            ],
          },
        })
      )
      .mockResolvedValueOnce(
        jsonResponse({ data: { id: 'r1', status: 'contacted', handledAt: '2026-11-09T12:00:00Z' } })
      )
    vi.stubGlobal('fetch', fetchMock)

    const items = await listReferrals({ status: 'pending' })
    expect(items[0]?.pendingHours).toBe(30)
    await updateReferralStatus('r1', 'contacted')

    const body = JSON.parse(String((fetchMock.mock.calls[1]?.[1] as RequestInit).body)) as Record<string, unknown>
    expect(body.status).toBe('contacted')
  })

  it('个人成长档案只返回本人活动', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          userId: 'u1',
          displayName: '同学',
          totalActivities: 3,
          wellbeingCount: 2,
          careerCount: 1,
          byMonth: [],
          items: [],
        },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const portfolio = await getPortfolio()
    expect(portfolio.totalActivities).toBe(3)
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/me/portfolio')
  })
})
