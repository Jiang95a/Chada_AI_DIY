import campusHero from '@/assets/photos/hero-campus.jpg'
import garden from '@/assets/photos/activity-garden.jpg'
import workshop from '@/assets/photos/activity-workshop.jpg'
import club from '@/assets/photos/activity-club.jpg'
import selfcare from '@/assets/photos/activity-selfcare.jpg'
import careerTalk from '@/assets/photos/career-talk.jpg'
import careerInterview from '@/assets/photos/career-interview.jpg'
import careerResume from '@/assets/photos/career-resume.jpg'
import careerTest from '@/assets/photos/career-test.jpg'

export interface ProjectCoverInput {
  id?: string
  slug?: string
  title?: string
  description?: string
  activityType?: string
  coverTone?: string
}

const tonePools: Record<string, string[]> = {
  read: [garden, campusHero, club],
  photo: [selfcare, garden],
  volunteer: [club, garden, campusHero],
  music: [workshop, careerTalk],
  vote: [workshop, careerTalk],
  checkin: [selfcare, garden],
}

export const PROJECT_COVERS = [
  garden,
  campusHero,
  selfcare,
  workshop,
  club,
  careerTalk,
  careerInterview,
  careerResume,
  careerTest,
]

function hash(value: string): number {
  let result = 0
  for (let index = 0; index < value.length; index += 1) {
    result = (result * 31 + value.charCodeAt(index)) >>> 0
  }
  return result
}

/**
 * 项目封面的统一选择器。
 *
 * 之前项目卡只按 4 个 coverTone 取一张固定图，所以同模板生成的活动全长得一样。
 * 现在先按标题/类型找语义图，再按项目 id 在同类图池里稳定地选一张：
 * 同一个项目每次刷新不变，不同项目大多不会撞图。
 */
export function coverForProject(project: ProjectCoverInput): string {
  const text = `${project.title ?? ''} ${project.description ?? ''} ${project.activityType ?? ''}`
  let pool: string[] | null = null

  if (/面试/.test(text)) pool = [careerInterview, careerTalk]
  else if (/简历/.test(text)) pool = [careerResume, workshop]
  else if (/职业|生涯|兴趣|测评|方向/.test(text)) pool = [careerTest, careerTalk, workshop]
  else if (/打卡|关怀|睡眠|情绪|正念/.test(text)) pool = [selfcare, garden]
  else if (/问答|知识|科普|讲座|分享/.test(text)) pool = [workshop, careerTalk]
  else if (/招新|社团|志愿|加入|纳新/.test(text)) pool = [club, garden, campusHero]
  else if (/游园|市集|户外|运动|活动/.test(text)) pool = [garden, campusHero, club]

  const fallback = tonePools[project.coverTone ?? ''] ?? [garden, selfcare, workshop]
  const candidates = pool ?? fallback
  const key = project.id ?? project.slug ?? project.title ?? 'xinyu'
  return candidates[hash(key) % candidates.length] ?? garden
}

/**
 * 同一屏里的活动卡用不重复的封面。
 *
 * coverForProject 只保证单个项目稳定，不能保证两张卡不撞图；
 * 这里在结果重复时从整组图里取下一张未使用的，保证一卡一图。
 */
export function uniqueCoversForProjects(projects: ProjectCoverInput[]): Map<string, string> {
  const used = new Set<string>()
  const result = new Map<string, string>()

  for (const project of projects) {
    const key = project.slug ?? project.id ?? project.title ?? ''
    let cover = coverForProject(project)
    if (used.has(cover)) {
      cover = PROJECT_COVERS.find((candidate) => !used.has(candidate)) ?? cover
    }
    used.add(cover)
    if (key) result.set(key, cover)
  }

  return result
}
