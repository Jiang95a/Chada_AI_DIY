import type {
  ActivityDomain,
  ParticipantTrace,
  TraceParseResult,
  TraceParser,
  TraceTag,
} from '@/types/trace'

/**
 * 前端规则引擎：把一段自由输入解析成标签 + 会话上下文。
 *
 * 设计约束：
 * - 只做「抽取 + 重算」，不下结论、不打人格标签；
 * - 关键词表按业务域分开，职规与心理共用同一个返回结构；
 * - 三期换成 agent 时，只替换 drop-in 的 TraceParser，不动调用方。
 */

interface TagRule {
  tag: string
  words: string[]
  paths: string[]
}

const MENTAL_RULES: TagRule[] = [
  {
    tag: '压力',
    words: ['压力', '撑不住', '崩溃', '喘不过气', '忙不过来', '期末', '考试', '作业', '论文', '汇报', '答辩'],
    paths: ['breathe', 'tiny-step', 'note'],
  },
  {
    tag: '睡眠',
    words: ['失眠', '睡不着', '睡不好', '熬夜', '早醒', '噩梦', '睡不够'],
    paths: ['sleep', 'breathe', 'note'],
  },
  {
    tag: '人际',
    words: ['室友', '朋友', '同学', '社恐', '尴尬', '被排挤', '被讨厌', '吵架', '冷战', '关系', '不合群'],
    paths: ['company', 'note', 'quiet'],
  },
  {
    tag: '孤独',
    words: ['孤独', '一个人', '没人', '没人听', '没人懂', '空落落'],
    paths: ['company', 'ask-help', 'note'],
  },
  {
    tag: '家庭',
    words: ['家里', '爸妈', '父母', '家人', '被说', '期待', '失望', '催'],
    paths: ['family', 'note', 'tiny-step'],
  },
  {
    tag: '自我评价',
    words: ['没用', '不够好', '自卑', '失败', '丢人', '配不上', '讨厌自己', '很差'],
    paths: ['evidence', 'note', 'ask-help'],
  },
  {
    tag: '情绪',
    words: ['焦虑', '紧张', '害怕', '难过', '低落', '委屈', '生气', '烦躁', '想哭', '麻木', '提不起劲'],
    paths: ['breathe', 'note', 'company'],
  },
  {
    tag: '学业',
    words: ['绩点', '挂科', '保研', '转专业', '考研', '实习', '就业', '求职', '未来', '毕业'],
    paths: ['tiny-step', 'note', 'ask-help'],
  },
  {
    tag: '身体',
    words: ['心慌', '头疼', '胃疼', '手抖', '吃不下', '暴食', '胸闷'],
    paths: ['breathe', 'sleep', 'ask-help'],
  },
  {
    tag: '想休息',
    words: ['想休息', '想安静', '安静待', '想一个人待着', '放松', '喘口气', '躺一会', '好累', '有点累'],
    paths: ['quiet', 'unwind', 'sleep'],
  },
  {
    tag: '想表达',
    words: ['想说说', '想说', '想写', '想被听见', '想倾诉', '记录'],
    paths: ['note', 'company', 'ask-help'],
  },
  {
    tag: '想认识人',
    words: ['想认识', '交朋友', '认识人', '认识几个', '一起', '结伴'],
    paths: ['company', 'note', 'quiet'],
  },
]

const CAREER_RULES: TagRule[] = [
  {
    tag: '迷茫',
    words: ['迷茫', '不知道', '没方向', '不知所措', '看不清', '困惑'],
    paths: ['map', 'story', 'tiny-test'],
  },
  {
    tag: '犹豫',
    words: ['犹豫', '纠结', '两个方向', '选哪个', '改主意', '拿不定', '摇摆'],
    paths: ['map', 'story', 'tiny-test'],
  },
  {
    tag: '方向',
    words: ['产品', '运营', '设计', '编程', '开发', '公务员', '教师', '考研', '保研', '出国', '就业', '实习'],
    paths: ['map', 'story', 'tiny-test'],
  },
  {
    tag: '能力',
    words: ['能力', '不会', '学不会', '没经验', '简历', '面试', '实习经历'],
    paths: ['tiny-test', 'story', 'map'],
  },
  {
    tag: '家庭期待',
    words: ['家里', '爸妈', '父母', '期待', '稳定', '反对'],
    paths: ['family', 'map', 'tiny-test'],
  },
  {
    tag: '怕选错',
    words: ['怕选错', '选错', '后悔', '来不及', '失败'],
    paths: ['story', 'map', 'tiny-test'],
  },
  {
    tag: '兴趣',
    words: ['兴趣', '喜欢', '有意思', '热爱'],
    paths: ['map', 'tiny-test', 'story'],
  },
  {
    tag: '想行动',
    words: ['想试', '试试', '这周', '行动', '开始'],
    paths: ['tiny-test', 'story', 'map'],
  },
]

const TAG_RULES: Record<ActivityDomain, TagRule[]> = {
  mental: MENTAL_RULES,
  career: CAREER_RULES,
}

const DEFAULT_PATHS: Record<ActivityDomain, string[]> = {
  mental: ['breathe', 'note', 'quiet'],
  career: ['map', 'story', 'tiny-test'],
}

const HINTS: Record<ActivityDomain, Record<string, string>> = {
  mental: {
    压力: '先不用解决全部，挑一件最小的事，做完就算数。',
    睡眠: '今晚先只改一件事：把手机放到够不着的地方。',
    人际: '不用把关系想清楚，先看看「半步」能做什么。',
    孤独: '你不需要马上变得热闹，先找一个愿意听你说两句的人。',
    家庭: '家里的期待很重，但你不必现在就说服谁。',
    自我评价: '你刚才那句话可能太绝对了，我们找一件反例。',
    情绪: '先让情绪有个名字，再决定要不要处理它。',
    学业: '信息不足的时候，先弄清「要决定什么」，别急着选。',
    身体: '身体比脑子先知道答案，先看看它哪里最紧。',
    想休息: '可以什么都不解决，先给自己十分钟。',
    想表达: '想说的话不用完整，写下来一句也算。',
    想认识人: '不用一次认识很多人，先迈半步就够了。',
  },
  career: {
    迷茫: '不用现在就想出答案，先把「不知道什么」写清楚。',
    犹豫: '两个方向都想要，说明它们各自满足了不同的东西。',
    方向: '先别问「最适合」，先问「哪个我现在能试」。',
    能力: '能力不是想出来的，是用一件小事验证出来的。',
    家庭期待: '他们的期待是一个声音，不是你的答案。',
    怕选错: '怕选错，通常怕的是后悔，而不是选错本身。',
    兴趣: '兴趣要靠做过才知道，不是靠回想。',
    想行动: '把这周能做的最小一步写下来，比计划更有用。',
  },
}

const QUESTIONS: Record<ActivityDomain, Record<string, string>> = {
  mental: {
    压力: '这周有没有哪个时刻，压力比平时轻一点？',
    睡眠: '昨晚是几点睡着的？',
    人际: '最近一次你觉得被理解，是什么时候？',
    孤独: '这周有没有一个人，是你愿意多说两句的？',
    家庭: '如果家里那句话可以换一种说法，你希望它是什么？',
    自我评价: '有没有一件小事，证明你刚才说的不完全对？',
    情绪: '这个情绪如果有一个名字，它会叫什么？',
    学业: '你现在最想弄清的是信息，还是决定？',
    身体: '身体最紧的地方在哪里？',
    想休息: '如果给自己十分钟什么都不做，你会做什么？',
    想表达: '你最想被谁听见？',
    想认识人: '如果只迈半步，你会先做什么？',
  },
  career: {
    迷茫: '你说不清的那部分，是信息不够，还是不敢选？',
    犹豫: '如果两边只能留一个，你最舍不得的是什么？',
    方向: '这个方向里，有一件你这周就能试的小事吗？',
    能力: '你缺的是能力，还是一次被看见的机会？',
    家庭期待: '如果不用考虑家里，你会怎么选？',
    怕选错: '你更怕选错，还是更怕一直不选？',
    兴趣: '最近一次做事情忘了时间，是在做什么？',
    想行动: '这周你能做的最小一步是什么？',
  },
}

/** 只认明确的自伤表达，避免「累死了」这类误伤。与 EchoNote 共用。 */
export const CARE_WORDS = ['不想活', '活不下去', '想死', '自杀', '自残', '割腕', '结束自己', '跳楼', '没有活下去']

export function hasCareWords(text: string): boolean {
  return CARE_WORDS.some((word) => text.includes(word))
}

function uniqueTags(tags: TraceTag[]): TraceTag[] {
  const seen = new Set<string>()
  return tags.filter((tag) => {
    if (seen.has(tag.content)) return false
    seen.add(tag.content)
    return true
  })
}

function matchTags(text: string, domain: ActivityDomain): TagRule[] {
  return TAG_RULES[domain].filter((rule) => rule.words.some((word) => text.includes(word)))
}

function pickPromise(text: string): string | undefined {
  const sentence = text
    .split(/[。！？!?\n]/)
    .map((part) => part.trim())
    .find((part) => {
      if (part.length < 6) return false
      if (/(我要|我会|我打算).{0,18}(试试|试一下|做|问|写|聊|去|开始|完成|约|报名|联系)/.test(part)) return true
      // 「这周考试好多」不是承诺；要有具体动作才算。
      return /(这周|下周|明天|接下来).{0,18}(试试|试一下|去做|去问|去写|去聊|去约|开始|完成|报名|联系|投简历)/.test(
        part
      )
    })
  if (!sentence) return undefined
  return sentence.length > 36 ? `${sentence.slice(0, 36)}…` : sentence
}

function buildSummary(domain: ActivityDomain, primaryTags: string[], care: boolean): string {
  if (care) return '你写下了一些很重的话，先照顾好自己。'
  if (!primaryTags.length) return ''
  const list = primaryTags
    .slice(0, 3)
    .map((tag) => `「${tag}」`)
    .join('、')
  return domain === 'career' ? `这次你提到 ${list}，还在找下一步。` : `这次你提到 ${list}，想先缓一缓。`
}

export async function parseTrace(input: {
  text: string
  trace: ParticipantTrace
}): Promise<TraceParseResult> {
  const text = input.text.trim()
  const domain = input.trace.domain
  const care = hasCareWords(text)
  const matched = care ? [] : matchTags(text, domain)
  const now = Date.now()

  const freshTags: TraceTag[] = matched.map((rule) => ({
    content: rule.tag,
    source: 'auto',
    createdAt: now,
  }))
  const mergedTags = uniqueTags([...input.trace.tags, ...freshTags]).slice(-12)

  const currentTagNames = matched.map((rule) => rule.tag)
  const primary = currentTagNames[0] ?? mergedTags[mergedTags.length - 1]?.content ?? ''
  const summaryTagNames = [...currentTagNames, ...input.trace.tags.map((tag) => tag.content)].filter(
    (name, index, all) => all.indexOf(name) === index
  )
  const changedMind = /(其实|不是|但是|可是|换个|改主意|想了想|又觉得)/.test(text)

  const paths = care
    ? ['ask-help', 'note', 'quiet']
    : (() => {
        // 本次输入优先，之前留下的需求继续保留，避免「改主意」把旧路径全冲掉。
        const previousRules = TAG_RULES[domain].filter((rule) =>
          input.trace.tags.some((tag) => tag.content === rule.tag)
        )
        const picked = [...matched, ...previousRules].flatMap((rule) => rule.paths)
        const fallback = DEFAULT_PATHS[domain]
        const list = [...picked, ...fallback].filter((path, index, all) => all.indexOf(path) === index)
        return list.slice(0, 4)
      })()

  const previousPrimary = input.trace.tags.filter((tag) => tag.source === 'auto').slice(-1)[0]?.content
  const change =
    changedMind || (previousPrimary && currentTagNames.length && previousPrimary !== primary)
      ? previousPrimary && previousPrimary !== primary
        ? `从「${previousPrimary}」到「${primary}」`
        : '你自己的想法有了一点调整'
      : input.trace.change

  const baseHint = care
    ? '你写下的这些，值得有人认真听。下面这些号码和入口，现在就能用。'
    : (primary && HINTS[domain][primary]) ||
      (matched.length
        ? '你已经说出了几件具体的事，我们可以从最小的一件开始。'
        : '没抓到关键词也没关系，可以再多说一句：最近最占你心思的是什么？')

  // 一句提示只回答一件事，但可以补一句更具体的下一步。
  const extras: string[] = []
  if (!care && domain === 'mental') {
    if (currentTagNames.includes('睡眠') && primary !== '睡眠') {
      extras.push('如果今晚睡不着，先只改一件事：把手机放到够不着的地方。')
    }
    if (currentTagNames.includes('家庭') && primary !== '家庭') {
      extras.push('家里的那部分，可以先不急着回应。')
    }
  }
  const hint = [baseHint, ...extras].join(' ')

  return {
    tags: mergedTags,
    matchedTags: currentTagNames,
    sessionContext: { currentHint: hint, availablePaths: paths },
    summary: buildSummary(domain, summaryTagNames, care) || input.trace.summary,
    openQuestion: (primary && QUESTIONS[domain][primary]) || input.trace.openQuestion,
    change: care ? input.trace.change : change,
    promise: pickPromise(text) ?? input.trace.promise,
    updatedAt: now,
  }
}

/** 预留 agent 替换点：调用方只依赖 TraceParser 接口。 */
export const ruleParser: TraceParser = {
  parse: parseTrace,
}
