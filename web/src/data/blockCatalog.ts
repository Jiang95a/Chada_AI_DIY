import type { Block, BlockType, FieldDef, Theme } from '@/types/editor'
import { uid } from '@/utils/misc'

/* ------------------------------- 组件元信息 ------------------------------- */

export interface BlockMeta {
  type: BlockType
  label: string
  icon: string
  group: BlockGroup
  desc: string
}

export type BlockGroup = '布局' | '内容' | '表单' | '互动'

export const BLOCK_GROUPS: BlockGroup[] = ['布局', '内容', '表单', '互动']

export const BLOCK_META: BlockMeta[] = [
  { type: 'nav', label: '导航栏', icon: 'nav', group: '布局', desc: '顶部标题栏' },
  { type: 'banner', label: 'Banner', icon: 'banner', group: '布局', desc: '主视觉大图' },
  { type: 'divider', label: '分割线', icon: 'divider', group: '布局', desc: '分隔内容' },
  { type: 'heading', label: '标题', icon: 'heading', group: '内容', desc: '章节标题' },
  { type: 'text', label: '文本', icon: 'text', group: '内容', desc: '段落说明' },
  { type: 'image', label: '图片', icon: 'image', group: '内容', desc: '单张配图' },
  {
    type: 'infoCard',
    label: '信息卡片',
    icon: 'card',
    group: '内容',
    desc: '时间地点等',
  },
  { type: 'button', label: '按钮', icon: 'button', group: '互动', desc: '跳转 / 提交' },
  { type: 'qrcode', label: '二维码', icon: 'qr', group: '互动', desc: '扫码查看' },
  {
    type: 'countdown',
    label: '倒计时',
    icon: 'clock',
    group: '互动',
    desc: '截止时间提醒',
  },
  { type: 'form', label: '表单', icon: 'form', group: '表单', desc: '报名信息收集' },
  { type: 'radio', label: '单选框', icon: 'radio', group: '表单', desc: '单选问题' },
  {
    type: 'checkbox',
    label: '多选框',
    icon: 'checkbox',
    group: '表单',
    desc: '多选问题',
  },
]

export function metaOf(type: BlockType): BlockMeta {
  const found = BLOCK_META.find((item) => item.type === type)
  return found ?? (BLOCK_META[0] as BlockMeta)
}

/* ------------------------------- 主题 ------------------------------- */

export const DEFAULT_THEME: Theme = {
  primary: '#2d5046',
  accent: '#b88c72',
  bg: '#faf8f5',
  text: '#1f3129',
  radius: 16,
  fontScale: 1,
  spacing: 1,
  buttonStyle: 'solid',
  font: 'sans',
}

export interface ThemePreset {
  id: string
  name: string
  theme: Pick<Theme, 'primary' | 'accent' | 'bg' | 'text'>
}

export const THEME_PRESETS: ThemePreset[] = [
  {
    id: 'indigo',
    name: '墨绿',
    theme: { primary: '#2d5046', accent: '#b88c72', bg: '#faf8f5', text: '#1f3129' },
  },
  {
    id: 'sunset',
    name: '暖棕',
    theme: { primary: '#8d654f', accent: '#c69a80', bg: '#fbf5ef', text: '#2d261f' },
  },
  {
    id: 'forest',
    name: '森林',
    theme: { primary: '#355c4e', accent: '#b88c72', bg: '#f5f3ec', text: '#183028' },
  },
  {
    id: 'ocean',
    name: '雾青',
    theme: { primary: '#527d78', accent: '#b88c72', bg: '#f2f6f3', text: '#18332f' },
  },
  {
    id: 'rose',
    name: '陶土',
    theme: { primary: '#95604f', accent: '#c99278', bg: '#fbf3ef', text: '#33201a' },
  },
  {
    id: 'midnight',
    name: '夜墨',
    theme: { primary: '#c69a80', accent: '#6d937f', bg: '#122019', text: '#f3f0e8' },
  },
  {
    id: 'graphite',
    name: '深灰绿',
    theme: { primary: '#3f554a', accent: '#b88c72', bg: '#f4f0e9', text: '#1f3129' },
  },
]

/* ------------------------------- 默认属性 ------------------------------- */

function defaultProps(type: BlockType): Record<string, any> {
  switch (type) {
    case 'nav':
      return { title: '活动标题', right: '', showBack: true }
    case 'banner':
      return {
        title: '在这里写主标题',
        subtitle: '用简短文案说清这是什么活动',
        image: '',
        height: 220,
      }
    case 'heading':
      return { text: '章节标题', sub: '', size: 'lg' }
    case 'text':
      return {
        text: '在这里写活动介绍。短句、具体、说人话——别写「诚挚邀请」这种公文腔。',
        size: 'md',
      }
    case 'image':
      return { url: '', caption: '', ratio: '4:3' }
    case 'infoCard':
      return {
        title: '活动信息',
        items: [
          { id: uid('it'), icon: 'time', label: '时间', value: '10月26日 19:00' },
          { id: uid('it'), icon: 'place', label: '地点', value: '三教 402' },
          { id: uid('it'), icon: 'org', label: '主办', value: '学生会' },
        ],
      }
    case 'button':
      return {
        text: '立即报名',
        variant: 'primary',
        full: true,
        action: { type: 'page', pageId: '', url: '' },
      }
    case 'form':
      return {
        title: '报名信息',
        buttonText: '提交报名',
        successHint: '提交后我们会短信通知你',
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
            placeholder: '用于接收活动通知',
            options: [],
          },
          {
            id: uid('f'),
            label: '备注',
            type: 'textarea',
            required: false,
            placeholder: '有什么想告诉我们的？',
            options: [],
          },
        ],
      }
    case 'radio':
      return {
        question: '你想加入哪个部门？',
        options: ['活动部', '宣传部', '技术部'],
        required: true,
        hint: '',
      }
    case 'checkbox':
      return {
        question: '感兴趣的活动类型',
        options: ['外拍', '讲座', '比赛'],
        required: false,
        max: 2,
        hint: '',
      }
    case 'qrcode':
      return { url: '', label: '扫码查看活动详情', size: 132 }
    case 'divider':
      return { label: '' }
    case 'countdown':
      return { target: '', label: '距离报名截止', doneText: '报名已截止' }
    default:
      return {}
  }
}

export const DEFAULT_BLOCK_STYLE = {
  bg: '',
  paddingX: 20,
  paddingY: 10,
  radius: 0,
  align: 'left' as const,
}

export function createBlock(type: BlockType, props: Record<string, any> = {}): Block {
  return {
    id: uid('blk'),
    type,
    props: { ...defaultProps(type), ...props },
    style: { ...DEFAULT_BLOCK_STYLE },
    hidden: false,
  }
}

export function cloneBlock(block: Block): Block {
  const copy = JSON.parse(JSON.stringify(block)) as Block
  copy.id = uid('blk')
  if (Array.isArray(copy.props.fields)) {
    copy.props.fields = copy.props.fields.map((f: any) => ({ ...f, id: uid('f') }))
  }
  if (Array.isArray(copy.props.items)) {
    copy.props.items = copy.props.items.map((item: any) => ({ ...item, id: uid('it') }))
  }
  return copy
}

/* ------------------------------- 属性面板字段 ------------------------------- */

const FIELD_TYPE_OPTIONS = [
  { label: '单行文本', value: 'text' },
  { label: '电话', value: 'tel' },
  { label: '邮箱', value: 'email' },
  { label: '多行文本', value: 'textarea' },
  { label: '下拉选择', value: 'select' },
  { label: '单选', value: 'radio' },
  { label: '多选', value: 'checkbox' },
]

export const FIELD_TYPE_LABELS = FIELD_TYPE_OPTIONS

export function fieldsFor(type: BlockType): FieldDef[] {
  switch (type) {
    case 'nav':
      return [
        { kind: 'text', key: 'title', label: '标题' },
        { kind: 'text', key: 'right', label: '右侧文字', placeholder: '可留空' },
        { kind: 'switch', key: 'showBack', label: '显示返回箭头' },
      ]
    case 'banner':
      return [
        { kind: 'text', key: 'title', label: '主标题' },
        { kind: 'text', key: 'subtitle', label: '副标题' },
        {
          kind: 'text',
          key: 'image',
          label: '背景图地址',
          placeholder: '留空则使用主题渐变',
        },
        {
          kind: 'number',
          key: 'height',
          label: '高度',
          min: 140,
          max: 320,
          step: 10,
          unit: 'px',
        },
      ]
    case 'heading':
      return [
        { kind: 'text', key: 'text', label: '标题文字' },
        { kind: 'text', key: 'sub', label: '副标题', placeholder: '可留空' },
        {
          kind: 'select',
          key: 'size',
          label: '字号',
          options: [
            { label: '超大', value: 'xl' },
            { label: '大', value: 'lg' },
            { label: '中', value: 'md' },
          ],
        },
      ]
    case 'text':
      return [
        { kind: 'textarea', key: 'text', label: '正文', rows: 6 },
        {
          kind: 'select',
          key: 'size',
          label: '字号',
          options: [
            { label: '正文', value: 'md' },
            { label: '小字', value: 'sm' },
          ],
        },
      ]
    case 'image':
      return [
        {
          kind: 'text',
          key: 'url',
          label: '图片地址',
          placeholder: 'https://… 或留空用占位图',
        },
        { kind: 'text', key: 'caption', label: '图注', placeholder: '可留空' },
        {
          kind: 'select',
          key: 'ratio',
          label: '比例',
          options: [
            { label: '4:3', value: '4:3' },
            { label: '1:1', value: '1:1' },
            { label: '16:9', value: '16:9' },
            { label: '3:4', value: '3:4' },
          ],
        },
      ]
    case 'infoCard':
      return [
        { kind: 'text', key: 'title', label: '卡片标题' },
        { kind: 'items', key: 'items', label: '信息行' },
      ]
    case 'button':
      return [
        { kind: 'text', key: 'text', label: '按钮文案' },
        {
          kind: 'select',
          key: 'variant',
          label: '样式',
          options: [
            { label: '主按钮', value: 'primary' },
            { label: '描边', value: 'outline' },
            { label: '浅色', value: 'soft' },
          ],
        },
        { kind: 'switch', key: 'full', label: '占满整行' },
        { kind: 'action', key: 'action', label: '点击后' },
      ]
    case 'form':
      return [
        { kind: 'text', key: 'title', label: '表单标题' },
        { kind: 'fields', key: 'fields', label: '字段' },
        { kind: 'text', key: 'buttonText', label: '提交按钮文案' },
        { kind: 'text', key: 'successHint', label: '提交后提示', placeholder: '可留空' },
      ]
    case 'radio':
      return [
        { kind: 'text', key: 'question', label: '问题' },
        { kind: 'lines', key: 'options', label: '选项（每行一个）' },
        { kind: 'switch', key: 'required', label: '必填' },
        { kind: 'text', key: 'hint', label: '补充说明', placeholder: '可留空' },
      ]
    case 'checkbox':
      return [
        { kind: 'text', key: 'question', label: '问题' },
        { kind: 'lines', key: 'options', label: '选项（每行一个）' },
        { kind: 'switch', key: 'required', label: '必填' },
        {
          kind: 'number',
          key: 'max',
          label: '最多可选',
          min: 0,
          max: 9,
          step: 1,
          unit: '项（0 为不限）',
        },
        { kind: 'text', key: 'hint', label: '补充说明', placeholder: '可留空' },
      ]
    case 'qrcode':
      return [
        {
          kind: 'text',
          key: 'url',
          label: '二维码内容',
          placeholder: '留空则使用发布链接',
        },
        { kind: 'text', key: 'label', label: '下方说明' },
        {
          kind: 'number',
          key: 'size',
          label: '尺寸',
          min: 90,
          max: 180,
          step: 6,
          unit: 'px',
        },
      ]
    case 'divider':
      return [{ kind: 'text', key: 'label', label: '中间文字', placeholder: '可留空' }]
    case 'countdown':
      return [
        {
          kind: 'text',
          key: 'target',
          label: '截止时间',
          placeholder: '2026-10-25 22:00',
        },
        { kind: 'text', key: 'label', label: '说明文字' },
        { kind: 'text', key: 'doneText', label: '截止后文案' },
      ]
    default:
      return []
  }
}

export const FIELD_TYPE_OPTIONS_ALL = FIELD_TYPE_OPTIONS

export const INFO_ICON_OPTIONS = [
  { label: '时间', value: 'time' },
  { label: '地点', value: 'place' },
  { label: '主办', value: 'org' },
  { label: '人数', value: 'people' },
  { label: '费用', value: 'ticket' },
  { label: '联系', value: 'contact' },
]

export const INFO_ICON_MAP: Record<string, string> = {
  time: 'clock',
  place: 'pin',
  org: 'users',
  people: 'users',
  ticket: 'tag',
  contact: 'phone',
}
