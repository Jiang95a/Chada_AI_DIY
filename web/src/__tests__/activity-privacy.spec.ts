import { beforeEach, describe, expect, it } from 'vitest'
import { clearActivityPrivacy, getActivityPrivacy, saveActivityPrivacy } from '@/composables/useActivityPrivacy'

describe('活动隐私设置', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('按项目和 slug 保存并恢复隐私配置', () => {
    const saved = saveActivityPrivacy('project-1', 'heart-garden', {
      ...getActivityPrivacy('project-1', 'heart-garden'),
      privacyMode: 'anonymous',
      consentRequired: true,
      retentionDays: 7,
      allowLeave: true,
      quietSpace: true,
    })

    expect(saved.privacyMode).toBe('anonymous')
    expect(getActivityPrivacy('project-1', 'heart-garden').quietSpace).toBe(true)
    expect(getActivityPrivacy('other-project', 'heart-garden').retentionDays).toBe(7)
  })

  it('删除隐私配置后恢复默认值', () => {
    saveActivityPrivacy('project-2', 'self-care', {
      ...getActivityPrivacy('project-2', 'self-care'),
      privacyMode: 'real_name',
    })
    clearActivityPrivacy('project-2', 'self-care')

    expect(getActivityPrivacy('project-2', 'self-care').privacyMode).toBe('optional')
  })
})
