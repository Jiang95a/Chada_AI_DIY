import campusHero from '@/assets/photos/hero-campus.jpg'
import garden from '@/assets/photos/activity-garden.jpg'
import workshop from '@/assets/photos/activity-workshop.jpg'
import club from '@/assets/photos/activity-club.jpg'
import selfcare from '@/assets/photos/activity-selfcare.jpg'
import careerTalk from '@/assets/photos/career-talk.jpg'
import careerInterview from '@/assets/photos/career-interview.jpg'
import careerResume from '@/assets/photos/career-resume.jpg'
import careerTest from '@/assets/photos/career-test.jpg'

export interface LibraryPhoto {
  id: string
  label: string
  url: string
  alt: string
}

/** 编辑器可直接选的本地照片库；上传图片会以 data URL 存进当前文档。 */
export const PHOTO_LIBRARY: LibraryPhoto[] = [
  { id: 'campus-hero', label: '校园秋日', url: campusHero, alt: '两名学生走在秋日校园里' },
  { id: 'garden', label: '校园活动', url: garden, alt: '校园里的学生活动' },
  { id: 'workshop', label: '小组讨论', url: workshop, alt: '学生围坐讨论' },
  { id: 'club', label: '同伴交流', url: club, alt: '两名学生交流' },
  { id: 'selfcare', label: '自我关怀', url: selfcare, alt: '在窗边写东西' },
  { id: 'career-talk', label: '生涯分享', url: careerTalk, alt: '学生一起看资料' },
  { id: 'career-interview', label: '模拟面试', url: careerInterview, alt: '两个人面试交流' },
  { id: 'career-resume', label: '简历整理', url: careerResume, alt: '桌面上的简历和电脑' },
  { id: 'career-test', label: '生涯测评', url: careerTest, alt: '学生在课桌前写东西' },
]
