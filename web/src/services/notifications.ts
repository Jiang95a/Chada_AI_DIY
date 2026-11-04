import { apiRequest, readData } from './http'

export interface NotificationRecord {
  id: string
  type: string
  title: string
  body: string
  read: boolean
  createdAt: string
  targetPath?: string
}

export async function listNotifications(): Promise<{ items: NotificationRecord[]; unread: number }> {
  const data = readData<{ items?: NotificationRecord[]; unread?: number }>(await apiRequest('/notifications'))
  return {
    items: Array.isArray(data.items) ? data.items : [],
    unread: Number(data.unread) || 0,
  }
}

export async function markNotificationRead(id: string): Promise<void> {
  await apiRequest(`/notifications/${encodeURIComponent(id)}/read`, { method: 'POST' })
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiRequest('/notifications/read-all', { method: 'POST' })
}
