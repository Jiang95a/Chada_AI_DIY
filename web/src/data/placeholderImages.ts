import photoGarden from '@/assets/photos/activity-garden.jpg'
import photoWorkshop from '@/assets/photos/activity-workshop.jpg'
import photoClub from '@/assets/photos/activity-club.jpg'
import photoSelfCare from '@/assets/photos/activity-selfcare.jpg'
import photoCareerTalk from '@/assets/photos/career-talk.jpg'
import photoCareerResume from '@/assets/photos/career-resume.jpg'

/**
 * 编辑器与模板文档里的默认图。
 *
 * 这些是本地真实照片，不是渐变占位图；页面首次打开时也能看到有内容的封面。
 * 上传自己的活动照片后仍会覆盖。
 */
export const PLACEHOLDER_IMAGES = {
  dusk: photoClub,
  dawn: photoGarden,
  forest: photoSelfCare,
  ocean: photoWorkshop,
  ink: photoCareerTalk,
  clay: photoCareerResume,
} as const

export type PlaceholderKey = keyof typeof PLACEHOLDER_IMAGES

export function placeholder(key: PlaceholderKey): string {
  return PLACEHOLDER_IMAGES[key]
}
