import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { askAgent, extractPoster } from '@/services/agent'
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
})
