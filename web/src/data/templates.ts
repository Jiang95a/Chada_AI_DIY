import type { Doc, DocMeta, Page, Theme } from '@/types/editor'
import { createBlock, DEFAULT_THEME, THEME_PRESETS } from '@/data/blockCatalog'
import { placeholder } from '@/data/placeholderImages'
import { linkPages, makePage } from '@/utils/doc'
import { uid, slugify } from '@/utils/misc'

export interface TemplateBuild {
  title: string
  theme: Theme
  meta: DocMeta
  pages: Page[]
}

export interface TemplateDef {
  id: string
  name: string
  scene: string
  tagline: string
  icon: string
  gradient: [string, string]
  build: () => TemplateBuild
}

const block = createBlock

/**
 * 模板里的日期一律按「今天 + N 天」算。
 * 写死 2026-10-26 这种日期，过两个月演示就成了「报名已截止」，很致命。
 */
function dateOffset(offsetDays: number, hour = 19, minute = 0) {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  date.setHours(hour, minute, 0, 0)
  const pad = (value: number) => String(value).padStart(2, '0')
  const month = date.getMonth() + 1
  const day = date.getDate()
  const weekday = '日一二三四五六'.charAt(date.getDay())
  const clock = `${pad(hour)}:${pad(minute)}`
  return {
    clock,
    label: `${month}月${day}日 ${clock}`,
    labelWithWeek: `${month}月${day}日 周${weekday} ${clock}`,
    iso: `${date.getFullYear()}-${pad(month)}-${pad(day)} ${clock}`,
    withYear: `${date.getFullYear()}年${month}月${day}日 ${clock}`,
  }
}

function themeOf(presetId: string, patch: Partial<Theme> = {}): Theme {
  const preset = THEME_PRESETS.find((item) => item.id === presetId)
  return { ...DEFAULT_THEME, ...(preset?.theme ?? {}), ...patch }
}

function meta(patch: Partial<DocMeta> = {}): DocMeta {
  return {
    org: '校园社团',
    startAt: '',
    place: '',
    deadline: '',
    capacity: 0,
    contact: '',
    ...patch,
  }
}

/* ------------------------------------------------------------------------- */
/* 演示文档：摄影社招新（四页，13 种组件全用上）                                  */
/* ------------------------------------------------------------------------- */

function buildPhotographyRecruit(): TemplateBuild {
  const interview = dateOffset(5, 19, 0)
  const deadline = dateOffset(4, 22, 0)

  const home = makePage('首页', 'home', [
    block('nav', { title: '光影摄影社', right: '2026 秋招', showBack: false }),
    block('banner', {
      title: '把秋天装进相机里',
      subtitle: '光影摄影社 2026 秋季招新',
      image: placeholder('ink'),
      height: 258,
    }),
    block('countdown', {
      target: deadline.iso,
      label: '报名截止还有',
      doneText: '报名已截止',
    }),
    block('infoCard', {
      title: '招新信息',
      items: [
        { id: uid('it'), icon: 'time', label: '面试时间', value: interview.label },
        { id: uid('it'), icon: 'place', label: '地点', value: '三教 402 阶梯教室' },
        { id: uid('it'), icon: 'people', label: '名额', value: '60 人（不限年级）' },
        { id: uid('it'), icon: 'contact', label: '联系', value: '微信 shiguang2026' },
      ],
    }),
    block('text', {
      text: '我们不只有长枪短炮。手机也能拍。\n每周一次夜拍，每月一次外拍，期末办一次展。',
    }),
    block('button', {
      text: '立即报名',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])

  const about = makePage('社团介绍', 'content', [
    block('nav', { title: '关于光影', right: '', showBack: true }),
    block('heading', {
      text: '一群爱拍照的人',
      sub: '成立于 2016 年 · 累计 240+ 成员',
      size: 'lg',
    }),
    block('text', {
      text: '光影摄影社是校内最早成立的影像社团。我们有暗房、有器材柜、有每月一次的评片会。\n\n不管你是刚买第一台相机，还是已经在接商单，这里都有人陪你把照片拍得更好。',
    }),
    block('image', {
      url: placeholder('ocean'),
      caption: '2025 年秋 · 东山外拍',
      ratio: '4:3',
    }),
    block('divider', { label: '我们拍什么' }),
    block('text', {
      text: '人像 · 街拍 · 风光 · 胶片冲洗 · 短片剪辑\n每周三晚 19:00 在 3 号楼 501 有固定活动，随时来蹭。',
    }),
  ])

  const form = makePage('报名表单', 'form', [
    block('nav', { title: '填写报名信息', right: '', showBack: true }),
    block('text', {
      text: '填完提交就行。我们会在 24 小时内微信联系你，面试当天带上你的三张作品（手机拍的也算）。',
    }),
    block('radio', {
      question: '想加入哪个组？',
      options: ['人像组', '街拍组', '风光组', '后期组'],
      required: true,
      hint: '没想好也没关系，面试时可以改',
    }),
    block('checkbox', {
      question: '你感兴趣的方向',
      options: ['外拍活动', '暗房冲洗', '短片拍摄', '社团宣传'],
      required: false,
      max: 2,
      hint: '最多选 2 项',
    }),
    block('form', {
      title: '基本信息',
      buttonText: '提交报名',
      successHint: '提交后请留意微信消息',
      fields: [
        {
          id: uid('f'),
          label: '姓名',
          type: 'text',
          required: true,
          placeholder: '怎么称呼你',
          options: [],
        },
        {
          id: uid('f'),
          label: '学号',
          type: 'text',
          required: true,
          placeholder: '用于核对身份',
          options: [],
        },
        {
          id: uid('f'),
          label: '手机号',
          type: 'tel',
          required: true,
          placeholder: '11 位手机号',
          options: [],
        },
        {
          id: uid('f'),
          label: '微信号',
          type: 'text',
          required: true,
          placeholder: '方便拉你进面试群',
          options: [],
        },
        {
          id: uid('f'),
          label: '你有相机吗？',
          type: 'select',
          required: true,
          placeholder: '请选择',
          options: ['有单反/微单', '只有手机', '想借社团的器材'],
        },
        {
          id: uid('f'),
          label: '说点什么',
          type: 'textarea',
          required: false,
          placeholder: '最近拍的一张照片是怎样的？',
          options: [],
        },
      ],
    }),
  ])

  const success = makePage('报名成功', 'success', [
    block('nav', { title: '报名成功', right: '', showBack: false }),
    block('heading', {
      text: '收到啦，欢迎加入',
      sub: '我们已经记下你的报名信息',
      size: 'xl',
    }),
    block('text', {
      text: `面试时间：${interview.label}\n地点：三教 402 阶梯教室\n\n记得带上三张作品，手机拍的也算。`,
    }),
    block('qrcode', { url: '', label: '扫码进面试通知群', size: 138 }),
    block('button', {
      text: '返回首页',
      variant: 'outline',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'home' },
    }),
  ])

  const pages = [home, about, form, success]
  linkPages(pages)

  return {
    title: '光影摄影社 · 2026 秋季招新',
    theme: themeOf('graphite', {
      primary: '#1f2937',
      accent: '#f59e0b',
      bg: '#faf8f5',
      radius: 14,
      buttonStyle: 'solid',
      fontScale: 1,
      spacing: 1,
    }),
    meta: meta({
      org: '光影摄影社',
      startAt: interview.withYear,
      place: '三教 402 阶梯教室',
      deadline: deadline.iso,
      capacity: 60,
      contact: '微信 shiguang2026',
    }),
    pages,
  }
}

/* ------------------------------------------------------------------------- */
/* 四个场景模板                                                                */
/* ------------------------------------------------------------------------- */

function buildSignup(): TemplateBuild {
  const run = dateOffset(9, 18, 30)
  const deadline = dateOffset(8, 20, 0)

  const home = makePage('首页', 'home', [
    block('nav', { title: '校园荧光夜跑', right: '', showBack: false }),
    block('banner', {
      title: '周五夜跑，一起发光',
      subtitle: '第四届校园荧光夜跑 · 3 公里',
      image: placeholder('dusk'),
      height: 246,
    }),
    block('infoCard', {
      title: '活动信息',
      items: [
        { id: uid('it'), icon: 'time', label: '时间', value: run.labelWithWeek },
        { id: uid('it'), icon: 'place', label: '集合', value: '东区操场看台下' },
        { id: uid('it'), icon: 'ticket', label: '费用', value: '免费（含荧光手环）' },
      ],
    }),
    block('countdown', {
      target: deadline.iso,
      label: '报名截止还有',
      doneText: '报名已截止',
    }),
    block('button', {
      text: '我要参加',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])

  const detail = makePage('路线说明', 'content', [
    block('nav', { title: '路线与规则', right: '', showBack: true }),
    block('heading', { text: '3 公里 · 两圈半', size: 'lg' }),
    block('image', {
      url: placeholder('forest'),
      caption: '沿途经过图书馆、樱花园、南门',
      ratio: '16:9',
    }),
    block('text', {
      text: '1. 18:00 检录领手环，18:30 统一出发；\n2. 每个补给点可以打卡，集齐 3 个章有纪念徽章；\n3. 不计名次，走完全程也算完成。',
    }),
    block('divider', { label: '' }),
    block('text', { text: '安全提示：穿反光衣物，膝盖有伤的别硬撑。' }),
  ])

  const form = makePage('报名', 'form', [
    block('nav', { title: '报名信息', right: '', showBack: true }),
    block('form', {
      title: '参加者信息',
      buttonText: '确认报名',
      successHint: '提交后凭手机号现场检录',
      fields: [
        {
          id: uid('f'),
          label: '姓名',
          type: 'text',
          required: true,
          placeholder: '请输入姓名',
          options: [],
        },
        {
          id: uid('f'),
          label: '学号',
          type: 'text',
          required: true,
          placeholder: '请输入学号',
          options: [],
        },
        {
          id: uid('f'),
          label: '手机号',
          type: 'tel',
          required: true,
          placeholder: '紧急联系人会用',
          options: [],
        },
        {
          id: uid('f'),
          label: 'T 恤尺码',
          type: 'select',
          required: true,
          placeholder: '请选择',
          options: ['S', 'M', 'L', 'XL'],
        },
      ],
    }),
    block('checkbox', {
      question: '有需要提前说明的情况吗',
      options: ['膝踝旧伤', '哮喘/心脏问题', '当天会晚到'],
      required: false,
      max: 3,
      hint: '可多选，我们会单独联系你',
    }),
  ])

  const success = makePage('报名成功', 'success', [
    block('nav', { title: '报名成功', right: '', showBack: false }),
    block('heading', { text: '周五见', sub: '凭手机号现场检录', size: 'xl' }),
    block('text', {
      text: '18:00 东区操场看台下领荧光手环，别迟到。\n下雨顺延到周六同一时间。',
    }),
    block('qrcode', { url: '', label: '扫码加入活动群', size: 132 }),
  ])

  const pages = [home, detail, form, success]
  linkPages(pages)
  return {
    title: '第四届校园荧光夜跑',
    theme: themeOf('sunset', { radius: 18 }),
    meta: meta({
      org: '校学生会体育部',
      startAt: run.withYear,
      place: '东区操场',
      deadline: deadline.iso,
      capacity: 200,
      contact: '体育部 张同学',
    }),
    pages,
  }
}

function buildVote(): TemplateBuild {
  const deadline = dateOffset(6, 20, 0)

  const home = makePage('首页', 'home', [
    block('nav', { title: '摄影大赛 · 人气票选', right: '', showBack: false }),
    block('banner', {
      title: '2026 校园摄影大赛',
      subtitle: '人气作品投票 · 每人 1 票',
      image: placeholder('clay'),
      height: 230,
    }),
    block('text', {
      text: `从 126 份投稿里选出了 8 组入围作品。投出你心里的第一张。\n投票截止到 ${deadline.label}。`,
    }),
    block('infoCard', {
      title: '投票说明',
      items: [
        { id: uid('it'), icon: 'time', label: '截止', value: deadline.label },
        { id: uid('it'), icon: 'org', label: '主办', value: '光影摄影社' },
        { id: uid('it'), icon: 'people', label: '参与', value: '全校师生' },
      ],
    }),
    block('button', {
      text: '去投票',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])

  const vote = makePage('投票', 'form', [
    block('nav', { title: '选择作品', right: '', showBack: true }),
    block('heading', { text: '只投一票，投完不能改', size: 'md' }),
    block('radio', {
      question: '你最喜欢哪一组？',
      options: ['01 · 图书馆的午后', '02 · 雨后的操场', '03 · 食堂窗口', '04 · 深夜自习室', '05 · 樱花道'],
      required: true,
      hint: '作品详情见展板',
    }),
    block('form', {
      title: '投票人信息',
      buttonText: '提交投票',
      successHint: '每人限投一次，重复提交以第一次为准',
      fields: [
        {
          id: uid('f'),
          label: '学号',
          type: 'text',
          required: true,
          placeholder: '用于去重',
          options: [],
        },
        {
          id: uid('f'),
          label: '姓名',
          type: 'text',
          required: true,
          placeholder: '可选填',
          options: [],
        },
      ],
    }),
  ])

  const result = makePage('投票成功', 'success', [
    block('nav', { title: '投票成功', right: '', showBack: false }),
    block('heading', { text: '投出去了', sub: '结果会在截止后 3 天内公布', size: 'xl' }),
    block('text', {
      text: '结果会贴在社团公告栏，也会发到公众号。颁奖礼在结果公布后一周。',
    }),
    block('qrcode', { url: '', label: '扫码看实时排名', size: 132 }),
  ])

  const pages = [home, vote, result]
  linkPages(pages)
  return {
    title: '2026 校园摄影大赛 · 人气票选',
    theme: themeOf('rose', { radius: 20, buttonStyle: 'gradient' }),
    meta: meta({
      org: '光影摄影社',
      startAt: deadline.withYear,
      place: '线上投票',
      deadline: deadline.iso,
      capacity: 0,
      contact: '公众号「光影摄影社」',
    }),
    pages,
  }
}

function buildCheckin(): TemplateBuild {
  const start = dateOffset(4, 6, 30)
  const deadline = dateOffset(3, 22, 0)

  const home = makePage('首页', 'home', [
    block('nav', { title: '14 天早起挑战', right: '', showBack: false }),
    block('banner', {
      title: '早起 14 天，把早晨还给自己',
      subtitle: '每天 6:30 打卡 · 连续 14 天有奖',
      image: placeholder('dawn'),
      height: 236,
    }),
    block('countdown', {
      target: start.iso,
      label: '挑战开始还有',
      doneText: '挑战已开始',
    }),
    block('text', {
      text: '打卡方式：每天早上拍一张天亮的照片，配一句话。\n连续 14 天完成的同学，发放定制马克杯。',
    }),
    block('button', {
      text: '我要参加',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])

  const checkin = makePage('每日打卡', 'form', [
    block('nav', { title: '今日打卡', right: '第 1 天', showBack: true }),
    block('radio', {
      question: '今天几点起的？',
      options: ['6:00 之前', '6:00 - 6:30', '6:30 - 7:00', '7:00 之后'],
      required: true,
      hint: '诚实一点，我们不查岗',
    }),
    block('form', {
      title: '打卡内容',
      buttonText: '提交打卡',
      successHint: '每天只能打卡一次',
      fields: [
        {
          id: uid('f'),
          label: '昵称',
          type: 'text',
          required: true,
          placeholder: '排行榜上显示的名字',
          options: [],
        },
        {
          id: uid('f'),
          label: '今天的一句话',
          type: 'textarea',
          required: true,
          placeholder: '例如：五点半的天是灰蓝色的',
          options: [],
        },
      ],
    }),
  ])

  const success = makePage('打卡成功', 'success', [
    block('nav', { title: '打卡成功', right: '', showBack: false }),
    block('heading', { text: '第 1 天，完成', sub: '连续 1 / 14 天', size: 'xl' }),
    block('text', { text: '明天早上 6:30 见。\n断签了可以重来，但奖品就没了。' }),
    block('qrcode', { url: '', label: '扫码看排行榜', size: 132 }),
  ])

  const pages = [home, checkin, success]
  linkPages(pages)
  return {
    title: '14 天早起打卡挑战',
    theme: themeOf('forest', { radius: 18 }),
    meta: meta({
      org: '自律互助小组',
      startAt: start.withYear,
      place: '线上打卡',
      deadline: deadline.iso,
      capacity: 0,
      contact: '群公告',
    }),
    pages,
  }
}

function buildClubRecruit(): TemplateBuild {
  const talk = dateOffset(3, 19, 0)
  const deadline = dateOffset(2, 22, 0)

  const home = makePage('首页', 'home', [
    block('nav', { title: '招新啦', right: '2026 秋', showBack: false }),
    block('banner', {
      title: '加入我们，一起搞点事情',
      subtitle: '校学生会 · 2026 秋季招新',
      image: placeholder('dusk'),
      height: 244,
    }),
    block('text', {
      text: '我们不缺打杂的，缺能拿主意的人。\n四个部门同时开招，选一个你喜欢的。',
    }),
    block('infoCard', {
      title: '招新安排',
      items: [
        { id: uid('it'), icon: 'time', label: '宣讲会', value: talk.label },
        { id: uid('it'), icon: 'place', label: '地点', value: '图书馆报告厅' },
        { id: uid('it'), icon: 'contact', label: '咨询', value: '扫码进群' },
      ],
    }),
    block('button', {
      text: '报名加入',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])

  const dept = makePage('部门介绍', 'content', [
    block('nav', { title: '四个部门', right: '', showBack: true }),
    block('heading', { text: '你想做哪一块', size: 'lg' }),
    block('text', {
      text: '活动部：策划落地，最能练手。\n宣传部：写文案、做图、运营公众号。\n技术部：做小程序、搭网站。\n外联部：找赞助，和商家谈。',
    }),
    block('image', {
      url: placeholder('ocean'),
      caption: '去年的迎新之夜',
      ratio: '16:9',
    }),
    block('divider', { label: '' }),
    block('text', { text: '每个部门都接受零基础，我们有老人带。' }),
  ])

  const form = makePage('报名表单', 'form', [
    block('nav', { title: '报名', right: '', showBack: true }),
    block('radio', {
      question: '第一志愿',
      options: ['活动部', '宣传部', '技术部', '外联部'],
      required: true,
      hint: '面试时会聊为什么选它',
    }),
    block('form', {
      title: '基本信息',
      buttonText: '提交报名',
      successHint: '我们会在 3 天内短信通知',
      fields: [
        {
          id: uid('f'),
          label: '姓名',
          type: 'text',
          required: true,
          placeholder: '请输入姓名',
          options: [],
        },
        {
          id: uid('f'),
          label: '班级',
          type: 'text',
          required: true,
          placeholder: '如 软件2504',
          options: [],
        },
        {
          id: uid('f'),
          label: '手机号',
          type: 'tel',
          required: true,
          placeholder: '11 位手机号',
          options: [],
        },
      ],
    }),
  ])

  const success = makePage('报名成功', 'success', [
    block('nav', { title: '报名成功', right: '', showBack: false }),
    block('heading', { text: '报名收到', sub: '面试通知会发到你的手机', size: 'xl' }),
    block('text', { text: '记得在面试前想清楚：你想在这待多久，想带走什么。' }),
    block('qrcode', { url: '', label: '扫码进招新群', size: 132 }),
  ])

  const pages = [home, dept, form, success]
  linkPages(pages)
  return {
    title: '校学生会 2026 秋季招新',
    theme: themeOf('indigo', { radius: 16 }),
    meta: meta({
      org: '校学生会',
      startAt: talk.withYear,
      place: '图书馆报告厅',
      deadline: deadline.iso,
      capacity: 80,
      contact: '招新群',
    }),
    pages,
  }
}

/* ------------------------------------------------------------------------- */
/* 心晴场景模板                                                                */
/* ------------------------------------------------------------------------- */

function buildWellbeingRecruit(): TemplateBuild {
  const talk = dateOffset(4, 19, 0)
  const deadline = dateOffset(3, 22, 0)

  const home = makePage('首页', 'home', [
    block('nav', { title: '心屿心理协会', right: '2026 秋招', showBack: false }),
    block('banner', {
      title: '和愿意倾听的人，一起做点温暖的事',
      subtitle: '心屿心理协会 · 2026 秋季招新',
      image: placeholder('forest'),
      height: 252,
    }),
    block('infoCard', {
      title: '招新安排',
      items: [
        { id: uid('it'), icon: 'time', label: '交流时间', value: talk.label },
        { id: uid('it'), icon: 'place', label: '地点', value: '心理健康中心团体室' },
        { id: uid('it'), icon: 'people', label: '人数', value: '40 人以内' },
      ],
    }),
    block('text', {
      text: '我们组织心理游园会、自我关怀打卡和朋辈分享。\n不需要心理学专业背景，只要愿意认真倾听、尊重边界。',
    }),
    block('button', {
      text: '报名认识我们',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])

  const about = makePage('关于心屿', 'content', [
    block('nav', { title: '关于心屿', right: '', showBack: true }),
    block('heading', {
      text: '让心理关怀发生在日常里',
      sub: '学生心理社团 · 关注参与、表达与连接',
      size: 'lg',
    }),
    block('text', {
      text: '我们不做心理诊断，也不要求分享不想说的故事。\n心屿希望用活动、内容和轻量陪伴，让同学更容易找到适合自己的参与方式。',
    }),
    block('divider', { label: '你可以参与' }),
    block('text', {
      text: '活动策划 · 视觉与内容 · 朋辈陪伴 · 日常运营\n每个方向都有学长学姐带着一起做。',
    }),
  ])

  const form = makePage('报名表单', 'form', [
    block('nav', { title: '报名认识我们', right: '', showBack: true }),
    block('text', {
      text: '只收集活动联系需要的信息。报名信息仅由本次招新负责人查看。',
    }),
    block('radio', {
      question: '你比较想参与哪个方向？',
      options: ['活动策划', '内容创作', '朋辈陪伴', '视觉设计', '还想再了解'],
      required: true,
      hint: '没有经验也完全可以',
    }),
    block('form', {
      title: '基础信息',
      buttonText: '提交报名',
      successHint: '我们会在活动前联系你',
      fields: [
        { id: uid('f'), label: '昵称', type: 'text', required: true, placeholder: '怎么称呼你', options: [] },
        { id: uid('f'), label: '学院/班级', type: 'text', required: false, placeholder: '选填', options: [] },
        { id: uid('f'), label: '联系方式', type: 'text', required: true, placeholder: '手机或微信号', options: [] },
      ],
    }),
    block('checkbox', {
      question: '你更关注哪些活动？',
      options: ['心晴游园会', '自我关怀打卡', '心理知识分享', '匿名表达'],
      required: false,
      max: 2,
    }),
  ])

  const success = makePage('报名成功', 'success', [
    block('nav', { title: '报名收到', right: '', showBack: false }),
    block('heading', { text: '谢谢你愿意靠近一点', sub: '报名信息已经收到', size: 'xl' }),
    block('text', {
      text: `交流时间：${talk.label}\n地点：心理健康中心团体室\n\n如果当天无法参加，也可以回复消息告诉我们。`,
    }),
    block('button', {
      text: '返回首页',
      variant: 'outline',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'home' },
    }),
  ])

  const pages = [home, about, form, success]
  linkPages(pages)
  return {
    title: '心屿心理协会 · 2026 秋季招新',
    theme: themeOf('forest', { primary: '#356b50', accent: '#c79078', bg: '#f6f8f3', radius: 18 }),
    meta: meta({
      org: '心屿心理协会',
      startAt: talk.withYear,
      place: '心理健康中心团体室',
      deadline: deadline.iso,
      capacity: 40,
      contact: '心屿招新群',
    }),
    pages,
  }
}

function buildWellbeingEvent(): TemplateBuild {
  const event = dateOffset(6, 18, 30)
  const deadline = dateOffset(5, 20, 0)
  const home = makePage('首页', 'home', [
    block('nav', { title: '考试周心晴游园会', right: '', showBack: false }),
    block('banner', {
      title: '给忙碌的心，留一小块空地',
      subtitle: '考试周心晴游园会 · 五个轻量互动摊位',
      image: placeholder('dawn'),
      height: 248,
    }),
    block('infoCard', {
      title: '活动信息',
      items: [
        { id: uid('it'), icon: 'time', label: '时间', value: event.labelWithWeek },
        { id: uid('it'), icon: 'place', label: '地点', value: '心理健康中心前广场' },
        { id: uid('it'), icon: 'people', label: '参与', value: '可匿名报名' },
      ],
    }),
    block('text', {
      text: '不需要分享不想说的故事。可以一个人来，也可以和朋友一起。\n完成任意三个摊位，就可以领取一张心晴纪念卡。',
    }),
    block('button', {
      text: '匿名报名',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])
  const detail = makePage('活动说明', 'content', [
    block('nav', { title: '游园会怎么玩', right: '', showBack: true }),
    block('heading', { text: '五个不赶时间的小摊位', size: 'lg' }),
    block('text', {
      text: '呼吸练习 · 情绪颜色 · 温柔便利贴 · 压力球 DIY · 给自己写一句话\n每个摊位都可以只体验，不留下任何文字。',
    }),
    block('divider', { label: '安心说明' }),
    block('text', {
      text: '活动不是心理治疗，也不会进行任何心理测评。\n如果当天状态不适合参与，可以随时离开或联系现场工作人员。',
    }),
  ])
  const form = makePage('匿名报名', 'form', [
    block('nav', { title: '匿名报名', right: '', showBack: true }),
    block('form', {
      title: '参与人数',
      buttonText: '提交报名',
      successHint: '凭提交成功页面入场即可',
      fields: [
        { id: uid('f'), label: '昵称', type: 'text', required: false, placeholder: '可以只写一个称呼', options: [] },
        {
          id: uid('f'),
          label: '预计到场时间',
          type: 'select',
          required: true,
          placeholder: '请选择',
          options: ['18:30 - 19:00', '19:00 - 19:30', '19:30 - 20:00'],
        },
      ],
    }),
    block('checkbox', {
      question: '你希望提前知道什么？',
      options: ['是否有安静休息区', '是否必须参与互动', '是否可以提前离场'],
      required: false,
      max: 3,
    }),
  ])
  const success = makePage('报名成功', 'success', [
    block('nav', { title: '报名成功', right: '', showBack: false }),
    block('heading', { text: '周五，慢一点来', sub: '不需要准备什么，也不需要表现得很积极', size: 'xl' }),
    block('text', { text: `${event.labelWithWeek}\n心理健康中心前广场\n\n现场设有安静休息区，可以随时离场。` }),
  ])
  const pages = [home, detail, form, success]
  linkPages(pages)
  return {
    title: '考试周心晴游园会',
    theme: themeOf('forest', { primary: '#3f7257', accent: '#d29a84', bg: '#f7f8f2', radius: 20 }),
    meta: meta({
      org: '心屿心理协会',
      startAt: event.withYear,
      place: '心理健康中心前广场',
      deadline: deadline.iso,
      capacity: 120,
      contact: '现场工作人员',
    }),
    pages,
  }
}

function buildWellbeingQuiz(): TemplateBuild {
  const home = makePage('首页', 'home', [
    block('nav', { title: '心理健康知识问答', right: '', showBack: false }),
    block('banner', {
      title: '认识情绪，不是给情绪打分',
      subtitle: '五分钟心理知识小问答',
      image: placeholder('ocean'),
      height: 230,
    }),
    block('text', {
      text: '用几道轻松的问题了解常见心理知识。\n这不是心理测评，也不会生成诊断或风险结论。',
    }),
    block('button', {
      text: '开始答题',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])
  const quiz = makePage('知识问答', 'form', [
    block('nav', { title: '知识小问答', right: '', showBack: true }),
    block('radio', {
      question: '感到压力时，下面哪种做法更合适？',
      options: ['先离开高压环境，做几次缓慢呼吸', '强迫自己立刻恢复', '把所有感受都藏起来'],
      required: true,
      hint: '选择你认为最合适的一项',
    }),
    block('radio', {
      question: '朋友向你倾诉时，最重要的是？',
      options: ['认真听，并确认对方需要建议还是陪伴', '马上替他做决定', '告诉他不要想太多'],
      required: true,
    }),
    block('form', {
      title: '完成问答',
      buttonText: '查看结果',
      successHint: '结果仅用于知识科普',
      fields: [{ id: uid('f'), label: '昵称', type: 'text', required: false, placeholder: '选填', options: [] }],
    }),
  ])
  const success = makePage('完成问答', 'success', [
    block('nav', { title: '问答完成', right: '', showBack: false }),
    block('heading', { text: '谢谢你的认真参与', sub: '心理知识帮助我们更好地理解和陪伴', size: 'xl' }),
    block('text', {
      text: '这只是知识问答，不是心理测量。\n如果你最近持续感到困扰，可以联系学校心理健康中心或信任的老师。',
    }),
  ])
  const pages = [home, quiz, success]
  linkPages(pages)
  return {
    title: '心理健康知识小问答',
    theme: themeOf('ocean', { primary: '#527d7a', accent: '#c89b75', bg: '#f4f8f7', radius: 18 }),
    meta: meta({ org: '心晴志愿队', place: '线上参与', contact: '心理健康中心' }),
    pages,
  }
}

function buildWellbeingCheckin(): TemplateBuild {
  const start = dateOffset(2, 8, 0)
  const home = makePage('首页', 'home', [
    block('nav', { title: '21 天自我关怀', right: '', showBack: false }),
    block('banner', {
      title: '每天照顾自己一点点',
      subtitle: '21 天自我关怀练习',
      image: placeholder('dawn'),
      height: 236,
    }),
    block('text', {
      text: '每天收到一个很小的练习：喝水、散步、放空五分钟、给自己一句肯定。\n中断也没有关系，不需要补签。',
    }),
    block('button', {
      text: '开始参与',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])
  const checkin = makePage('今日练习', 'form', [
    block('nav', { title: '今日练习', right: '第 1 天', showBack: true }),
    block('heading', { text: '今天，给自己五分钟', size: 'md' }),
    block('radio', {
      question: '今天完成了哪种照顾？',
      options: ['慢慢喝一杯水', '短暂离开屏幕', '散步几分钟', '今天先休息'],
      required: true,
      hint: '没有哪一种更正确',
    }),
    block('form', {
      title: '留一句话给自己',
      buttonText: '完成今日练习',
      successHint: '内容仅自己可见',
      fields: [
        { id: uid('f'), label: '昵称', type: 'text', required: false, placeholder: '可不填写', options: [] },
        {
          id: uid('f'),
          label: '今天的一句话',
          type: 'textarea',
          required: false,
          placeholder: '不想写也可以留空',
          options: [],
        },
      ],
    }),
  ])
  const success = makePage('练习完成', 'success', [
    block('nav', { title: '今天到这里', right: '', showBack: false }),
    block('heading', { text: '已经做得足够好了', sub: '自我关怀不是打卡任务，也不需要连续完美', size: 'xl' }),
    block('text', { text: '下次想起来的时候再回来。\n断签不会清零，也不会影响任何评价。' }),
  ])
  const pages = [home, checkin, success]
  linkPages(pages)
  return {
    title: '21 天自我关怀练习',
    theme: themeOf('forest', { primary: '#55775b', accent: '#c59677', bg: '#f7f8f2', radius: 20 }),
    meta: meta({ org: '心晴志愿队', startAt: start.withYear, place: '线上参与', contact: '心晴志愿队' }),
    pages,
  }
}

export const TEMPLATES: TemplateDef[] = [
  {
    id: 'recruit',
    name: '心理社团招新',
    scene: '心理社团 / 成员招募',
    tagline: '社团介绍 + 参与方向 + 报名表',
    icon: 'users',
    gradient: ['#4f735f', '#a9c3b2'],
    build: buildWellbeingRecruit,
  },
  {
    id: 'signup',
    name: '心晴活动',
    scene: '游园会 / 团辅 / 分享会',
    tagline: '活动说明 + 安心提示 + 匿名报名',
    icon: 'calendar',
    gradient: ['#9a705a', '#d8b19e'],
    build: buildWellbeingEvent,
  },
  {
    id: 'vote',
    name: '心理知识问答',
    scene: '科普 / 知识互动',
    tagline: '知识问答 + 科普反馈',
    icon: 'check-circle',
    gradient: ['#527d78', '#aec5bf'],
    build: buildWellbeingQuiz,
  },
  {
    id: 'checkin',
    name: '自我关怀打卡',
    scene: '日常练习 / 轻量陪伴',
    tagline: '每日练习 + 无压力记录',
    icon: 'clock',
    gradient: ['#6c8c72', '#c5cdae'],
    build: buildWellbeingCheckin,
  },
]

export function templateById(id: string): TemplateDef | undefined {
  return TEMPLATES.find((item) => item.id === id)
}

/* ------------------------------- Doc 工厂 ------------------------------- */

export function newDocFromBuild(build: TemplateBuild, existing?: Doc): Doc {
  const now = Date.now()
  return {
    id: existing?.id ?? uid('doc'),
    title: build.title,
    slug: existing?.slug ?? slugify(build.title),
    theme: build.theme,
    pages: build.pages,
    meta: build.meta,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
    published: null,
  }
}

/** 首次打开时的演示文档：直接能看到成品的四页活动站 */
export function createDemoDoc(): Doc {
  return newDocFromBuild(buildWellbeingRecruit())
}

export function createBlankDoc(): Doc {
  const pages = [makePage('首页', 'home', [block('nav', { title: '活动标题', right: '', showBack: false })])]
  return newDocFromBuild({
    title: '未命名活动',
    theme: { ...DEFAULT_THEME },
    meta: meta(),
    pages,
  })
}

export function createDocFromTemplate(id: string, existing?: Doc): Doc {
  const template = templateById(id)
  if (!template) return createBlankDoc()
  return newDocFromBuild(template.build(), existing)
}

/** 给「海报解析」演示步骤用：从字段生成一个可用的三页方案 */
export function buildQuickDoc(input: {
  title: string
  org: string
  startAt: string
  place: string
  deadline: string
  capacity: number
  contact: string
}): TemplateBuild {
  const home = makePage('首页', 'home', [
    block('nav', { title: input.title || '活动报名', right: '', showBack: false }),
    block('banner', {
      title: input.title || '活动名称',
      subtitle: input.org ? `${input.org} 主办` : '欢迎参加',
      image: placeholder('dusk'),
      height: 240,
    }),
    block('infoCard', {
      title: '活动信息',
      items: [
        { id: uid('it'), icon: 'time', label: '时间', value: input.startAt || '待补充' },
        { id: uid('it'), icon: 'place', label: '地点', value: input.place || '待补充' },
        { id: uid('it'), icon: 'org', label: '主办', value: input.org || '待补充' },
      ],
    }),
    block('text', {
      text: '这里放活动介绍。先讲清楚：谁办的、干什么的、为什么值得来。',
    }),
    block('button', {
      text: '立即报名',
      variant: 'primary',
      full: true,
      action: { type: 'page', pageId: '', url: '', targetRole: 'form' },
    }),
  ])

  const form = makePage('报名表单', 'form', [
    block('nav', { title: '报名信息', right: '', showBack: true }),
    block('form', {
      title: '填写信息',
      buttonText: '提交报名',
      successHint: '提交后我们会尽快联系你',
      fields: [
        {
          id: uid('f'),
          label: '姓名',
          type: 'text',
          required: true,
          placeholder: '请输入姓名',
          options: [],
        },
        {
          id: uid('f'),
          label: '手机号',
          type: 'tel',
          required: true,
          placeholder: '11 位手机号',
          options: [],
        },
        {
          id: uid('f'),
          label: '备注',
          type: 'textarea',
          required: false,
          placeholder: '有什么想说的',
          options: [],
        },
      ],
    }),
  ])

  const success = makePage('报名成功', 'success', [
    block('nav', { title: '报名成功', right: '', showBack: false }),
    block('heading', { text: '报名成功', sub: '我们会尽快联系你', size: 'xl' }),
    block('text', {
      text: `${input.startAt || '时间待定'} · ${input.place || '地点待定'}`,
    }),
    block('qrcode', { url: '', label: '扫码查看活动详情', size: 132 }),
  ])

  const pages = [home, form, success]
  linkPages(pages)
  return {
    title: input.title || '未命名活动',
    theme: themeOf('indigo', { radius: 16 }),
    meta: meta({
      org: input.org,
      startAt: input.startAt,
      place: input.place,
      deadline: input.deadline,
      capacity: input.capacity,
      contact: input.contact,
    }),
    pages,
  }
}

/** 供「随机配色」用 */
export function randomTheme(current: Theme): Theme {
  const preset = THEME_PRESETS[Math.floor(Math.random() * THEME_PRESETS.length)] as (typeof THEME_PRESETS)[number]
  return { ...current, ...preset.theme }
}
