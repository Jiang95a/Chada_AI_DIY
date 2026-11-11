import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  applyProjectAiProposal,
  askAgent,
  extractPoster,
  generatePosterArtwork,
  generateProjectAiProposal,
} from '@/services/agent'
import { requestPasswordReset, resetPassword } from '@/services/auth'
import { loadTemplateCatalog } from '@/services/templates'

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('前端后端服务契约', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('diy-campus:access-token', 'test-token')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('模板目录读取 8080 /templates', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: [
          {
            id: 'template-1',
            code: 'recruitment-club',
            name: '心理社团招新',
            category: 'recruitment',
            description: '四页心理社团招新',
            document: {
              title: '心理社团招新',
              theme: { primary: '#2563eb', accent: '#7c3aed', bg: '#fff', text: '#111827' },
              pages: [{ name: '首页', role: 'home', blocks: [{ type: 'banner', props: { title: '招新' } }] }],
            },
          },
        ],
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const catalog = await loadTemplateCatalog()
    const remote = catalog.find((item) => item.source === 'remote')
    expect(remote?.id).toBe('template-1')
    expect(catalog).toHaveLength(1)
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/v1/templates')
  })

  it('AI 对话只请求业务后端 /ai/chat', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          reply: '页面方案已生成',
          thread_id: 'thread-1',
          latency_ms: 12,
          tool_calls: [],
          rag_hits: 2,
          flags: [],
        },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const reply = await askAgent('做一个招新页')
    expect(reply.reply).toBe('页面方案已生成')
    expect(reply.ragHits).toBe(2)
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/v1/ai/chat')
    expect(String(fetchMock.mock.calls[0]?.[0])).not.toContain(':8000')
  })

  it('海报解析只请求业务后端 /ai/poster/extract', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          info: {
            title: '校园活动',
            org: '',
            startAt: '',
            place: '',
            deadline: '',
            capacity: null,
            fee: '',
            contact: '',
            intro: '',
            signupFields: [],
            evidence: {},
            unclear: [],
            confidence: 0.9,
          },
          warnings: [],
          model: 'vision-test',
          latencyMs: 20,
        },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await extractPoster('data:image/png;base64,AAAA')
    expect(result.info.title).toBe('校园活动')
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/v1/ai/poster/extract')
    expect(String(fetchMock.mock.calls[0]?.[0])).not.toContain(':8000')
  })

  it('AI 海报生成只请求业务后端 /ai/poster/generate', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          url: 'https://assets.example/poster.webp',
          prompt: '考试周游园会',
          model: 'image-test',
        },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await generatePosterArtwork('考试周游园会海报')
    expect(result.url).toContain('poster.webp')
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/v1/ai/poster/generate')
    expect(String(fetchMock.mock.calls[0]?.[0])).not.toContain(':8000')
  })

  it('申请密码重置只提交邮箱并返回统一提示', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          message: '如果该邮箱已注册，我们会发送密码重置方式。',
        },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await requestPasswordReset('demo@example.com')
    expect(result.message).toContain('如果该邮箱已注册')
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/v1/auth/forgot-password')
    expect(String(fetchMock.mock.calls[0]?.[1]?.body)).toContain('demo@example.com')
  })

  it('提交密码重置使用邮件中的一次性令牌', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: { reset: true },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    await resetPassword('one-time-token', 'NewPassword123')
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/v1/auth/reset-password')
    const body = String(fetchMock.mock.calls[0]?.[1]?.body)
    expect(body).toContain('one-time-token')
    expect(body).toContain('NewPassword123')
  })

  it('正式 AI 提案通过项目接口生成', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          id: 'proposal-1',
          jobId: 'job-1',
          projectId: 'project-1',
          baseRevision: 3,
          target: { kind: 'document' },
          summary: '增加活动说明和报名表单',
          candidateDocument: null,
          operations: [{ tool: 'insert_component', arguments: {} }],
          rationale: ['补充报名入口'],
          risk: 'low',
          status: 'proposed',
        },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const proposal = await generateProjectAiProposal('project-1', '增加报名表单', 3)
    expect(proposal.summary).toContain('报名表单')
    expect(proposal.operations).toHaveLength(1)
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/v1/ai/projects/project-1/generate')
  })

  it('正式 AI 提案采用后返回新草稿版本', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          revision: 4,
          updatedAt: new Date().toISOString(),
          document: {
            id: 'doc-1',
            title: '考试周心晴游园会',
            slug: 'heart-garden',
            theme: {
              primary: '#2d5046',
              accent: '#b88c72',
              bg: '#faf8f5',
              text: '#1f3129',
              radius: 16,
              fontScale: 1,
              spacing: 1,
              buttonStyle: 'solid',
              font: 'sans',
            },
            pages: [{ id: 'page-home', name: '首页', role: 'home', blocks: [], bg: '' }],
            meta: { org: '心屿心理协会', startAt: '', place: '', deadline: '', capacity: 0, contact: '' },
            createdAt: Date.now(),
            updatedAt: Date.now(),
            published: null,
          },
        },
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await applyProjectAiProposal('proposal-1', 3)
    expect(result.revision).toBe(4)
    expect(result.document.pages).toHaveLength(1)
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/v1/ai/proposals/proposal-1/apply')
  })
})
