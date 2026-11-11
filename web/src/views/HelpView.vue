<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import { useWorkspace } from '@/composables/useWorkspace'
import { submitFeedback, type FeedbackInput } from '@/services/feedback'
import { API_BASE } from '@/services/http'

interface LocalFeedback {
  id: string
  category: string
  content: string
  createdAt: string
}

const HISTORY_KEY = 'diy-campus:feedback-history:v1'
const toast = useToast()
const route = useRoute()
const auth = useAuthStore()
const { selected } = useWorkspace()
const category = ref<FeedbackInput['category']>('suggestion')
const content = ref('')
const contact = ref('')
const submitting = ref(false)
const submitted = ref(false)
const error = ref('')
const history = ref<LocalFeedback[]>(restoreHistory())

const categories = [
  { value: 'suggestion' as const, label: '功能建议', icon: 'spark' },
  { value: 'bug' as const, label: '问题反馈', icon: 'alert' },
  { value: 'content' as const, label: '内容问题', icon: 'file' },
  { value: 'other' as const, label: '其他', icon: 'help' },
]

const diagnostics = computed(() =>
  [
    `页面：${route.fullPath}`,
    `空间：${selected.value.name}`,
    `账号：${auth.user?.email || '未登录'}`,
    `API：${API_BASE}`,
    `浏览器：${navigator.userAgent}`,
    `时间：${new Date().toLocaleString('zh-CN')}`,
  ].join('\n')
)

function restoreHistory(): LocalFeedback[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]') as unknown
    return Array.isArray(parsed) ? (parsed.slice(0, 5) as LocalFeedback[]) : []
  } catch {
    return []
  }
}

function remember(item: LocalFeedback): void {
  history.value = [item, ...history.value].slice(0, 5)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
}

async function sendFeedback(): Promise<void> {
  if (content.value.trim().length < 5) {
    error.value = '请至少写 5 个字，方便我们理解问题。'
    return
  }
  error.value = ''
  submitting.value = true
  try {
    const result = await submitFeedback({
      category: category.value,
      content: content.value.trim(),
      contact: contact.value.trim() || undefined,
    })
    remember({
      id: result.id,
      category: categories.find((item) => item.value === category.value)?.label ?? '其他',
      content: content.value.trim(),
      createdAt: result.createdAt,
    })
    content.value = ''
    contact.value = ''
    submitted.value = true
    toast.pushToast('反馈已提交，感谢你的建议', 'success')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '反馈提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function copyDiagnostics(): Promise<void> {
  try {
    await navigator.clipboard.writeText(diagnostics.value)
    toast.pushToast('诊断信息已复制', 'success')
  } catch {
    toast.pushToast('复制失败，请手动选择文本', 'warn')
  }
}
</script>

<template>
  <WorkspaceShell>
    <main class="help-page">
      <header class="help-heading">
        <div>
          <p class="eyebrow">Help center</p>
          <h1>帮助与反馈</h1>
          <p>从创建活动到发布排错，先在这里找答案；没解决就直接告诉我们。</p>
        </div>
        <RouterLink class="app-button app-button--secondary" to="/profile">
          <Icon name="users" :size="15" />返回个人资料
        </RouterLink>
      </header>

      <section class="help-hero">
        <div>
          <span class="help-hero-icon"><Icon name="help" :size="24" /></span>
          <h2>先做这三步，大多数问题都能解决</h2>
        </div>
        <div class="help-steps">
          <article>
            <b>01</b><strong>从模板创建</strong>
            <p>模板会自动生成完整页面、表单和成功页。</p>
          </article>
          <article>
            <b>02</b><strong>编辑后先预览</strong>
            <p>检查表单必填、按钮跳转和手机端显示。</p>
          </article>
          <article>
            <b>03</b><strong>发布并分享</strong>
            <p>发布后复制链接，返回数据页查看报名。</p>
          </article>
        </div>
      </section>

      <div class="help-layout">
        <section class="help-main">
          <div class="help-section-title">
            <div>
              <span>FAQ</span>
              <h2>常见问题</h2>
            </div>
            <small>点击问题展开答案</small>
          </div>
          <div class="help-faq">
            <details open>
              <summary><span>如何创建第一个活动页面？</span><Icon name="chevron" :size="15" /></summary>
              <p>
                打开「心晴模板」，选择适合的心理活动类型并点击“用这个模板创建”。进入页面工坊修改内容后发布。
              </p>
            </details>
            <details>
              <summary><span>页面发布后，为什么打不开？</span><Icon name="chevron" :size="15" /></summary>
              <p>先确认活动状态为“已发布”，并且后端 8080 正在运行。如果页面提示“尚未发布”，请在编辑器中重新发布。</p>
            </details>
            <details>
              <summary><span>报名数据和统计为什么是空的？</span><Icon name="chevron" :size="15" /></summary>
              <p>
                统计数据来自真实公开页访问和表单操作。把已发布链接分享给同学，打开并填写后，数据会出现在项目的数据中心。
              </p>
            </details>
            <details>
              <summary><span>AI 生成或海报识别不可用怎么办？</span><Icon name="chevron" :size="15" /></summary>
              <p>
                Agent 是独立服务。不可用时会自动降级到本地规则，不影响你继续手动编辑。需要真实 AI 能力时，再启动 Agent
                并配置模型 Key。
              </p>
            </details>
            <details>
              <summary><span>如何切换工作空间？</span><Icon name="chevron" :size="15" /></summary>
              <p>点击左侧空间卡片，在“心屿心理协会”“个人空间”和“心晴志愿队”之间切换。偏好设置里也可以切换当前空间。</p>
            </details>
          </div>

          <div class="help-section-title help-section-title--feedback">
            <div>
              <span>Feedback</span>
              <h2>直接反馈</h2>
            </div>
            <small>反馈会记录到后端</small>
          </div>
          <form class="feedback-form" @submit.prevent="sendFeedback">
            <div class="feedback-categories">
              <button
                v-for="item in categories"
                :key="item.value"
                type="button"
                :class="{ 'is-active': category === item.value }"
                @click="category = item.value"
              >
                <Icon :name="item.icon" :size="15" />{{ item.label }}
              </button>
            </div>
            <label>
              <span>问题或建议</span>
              <textarea
                v-model="content"
                rows="6"
                maxlength="3000"
                placeholder="请描述发生了什么、你期待的结果，以及复现步骤…"
              />
              <small>{{ content.length }} / 3000</small>
            </label>
            <label>
              <span>联系方式 <em>选填</em></span>
              <input v-model="contact" maxlength="120" placeholder="邮箱 / 微信 / QQ，方便我们回复你" />
            </label>
            <p v-if="error" class="feedback-error"><Icon name="alert" :size="13" />{{ error }}</p>
            <p v-if="submitted" class="feedback-success">
              <Icon name="check-circle" :size="13" />已收到反馈，我们会尽快查看。
            </p>
            <div class="feedback-actions">
              <button class="app-button app-button--primary" type="submit" :disabled="submitting">
                <Icon name="send" :size="15" />{{ submitting ? '提交中…' : '提交反馈' }}
              </button>
              <button class="app-button app-button--secondary" type="button" @click="copyDiagnostics">
                <Icon name="copy" :size="15" />复制诊断信息
              </button>
            </div>
          </form>
        </section>

        <aside class="help-side">
          <section class="help-side-card">
            <h2>快速入口</h2>
            <RouterLink to="/projects"
              ><Icon name="home" :size="15" /><span
                ><strong>运营概览</strong><small>管理全部心晴活动</small></span
              ></RouterLink
            >
            <RouterLink to="/templates"
              ><Icon name="grid" :size="15" /><span
                ><strong>心晴模板</strong><small>从心理活动结构开始</small></span
              ></RouterLink
            >
            <RouterLink to="/campus"
              ><Icon name="pin" :size="15" /><span
                ><strong>心理活动</strong><small>查看已发布的心晴活动</small></span
              ></RouterLink
            >
          </section>
          <section class="help-side-card">
            <h2>系统状态</h2>
            <div class="health-row"><i class="ok" /><span>业务后端</span><strong>8080</strong></div>
            <div class="health-row"><i /><span>智能体</span><strong>自动降级</strong></div>
            <p class="help-api">{{ API_BASE }}</p>
          </section>
          <section v-if="history.length" class="help-side-card">
            <h2>最近反馈</h2>
            <article v-for="item in history" :key="item.id" class="feedback-history">
              <span>{{ item.category }}</span>
              <p>{{ item.content }}</p>
            </article>
          </section>
        </aside>
      </div>
    </main>
  </WorkspaceShell>
</template>

<style scoped>
.help-page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 34px 34px 60px;
}
.help-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}
.help-heading h1 {
  margin: 0;
  font-size: clamp(30px, 4vw, 42px);
  letter-spacing: -0.05em;
}
.help-heading > div > p:last-child {
  margin: 9px 0 0;
  color: var(--muted);
  font-size: 11px;
}
.help-heading .app-button {
  min-height: 39px;
  font-size: 10px;
}
.help-hero {
  margin-top: 26px;
  padding: 24px;
  border: 1px solid var(--brand-200);
  border-radius: 20px;
  background:
    radial-gradient(circle at 90% 5%, rgb(45 80 70 / 12%), transparent 30%),
    linear-gradient(135deg, var(--brand-50), var(--surface));
}
.help-hero > div:first-child {
  display: flex;
  align-items: center;
  gap: 11px;
}
.help-hero-icon {
  display: grid;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  color: var(--primary);
  background: #fff;
  box-shadow: var(--shadow-sm);
  place-items: center;
}
.help-hero h2 {
  margin: 0;
  font-size: 17px;
}
.help-steps {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.help-steps article {
  padding: 14px;
  border: 1px solid rgb(45 80 70 / 10%);
  border-radius: 12px;
  background: rgb(255 255 255 / 72%);
}
.help-steps b {
  color: var(--primary);
  font-size: 9px;
}
.help-steps strong {
  display: block;
  margin-top: 7px;
  font-size: 10px;
}
.help-steps p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 8px;
  line-height: 1.6;
}
.help-layout {
  display: grid;
  align-items: start;
  gap: 16px;
  margin-top: 20px;
  grid-template-columns: minmax(0, 1.45fr) minmax(280px, 0.65fr);
}
.help-main,
.help-side {
  display: grid;
  gap: 14px;
}
.help-section-title {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}
.help-section-title > div {
  display: flex;
  align-items: center;
  gap: 8px;
}
.help-section-title span {
  padding: 4px 6px;
  border-radius: 6px;
  color: var(--primary);
  background: var(--primary-soft);
  font-size: 8px;
  font-weight: 800;
}
.help-section-title h2 {
  margin: 0;
  font-size: 16px;
}
.help-section-title small {
  color: var(--muted);
  font-size: 8px;
}
.help-section-title--feedback {
  margin-top: 10px;
}
.help-faq {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
}
.help-faq details {
  border-bottom: 1px solid var(--line);
}
.help-faq details:last-child {
  border-bottom: 0;
}
.help-faq summary {
  display: flex;
  min-height: 50px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  font-size: 10px;
  font-weight: 750;
  cursor: pointer;
  list-style: none;
}
.help-faq summary::-webkit-details-marker {
  display: none;
}
.help-faq details[open] summary svg {
  transform: rotate(180deg);
}
.help-faq p {
  margin: -3px 16px 15px;
  padding: 11px 12px;
  border-radius: 10px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  font-size: 9px;
  line-height: 1.8;
}
.feedback-form {
  display: grid;
  gap: 14px;
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
}
.feedback-categories {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.feedback-categories button {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 5px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 9px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  font-size: 9px;
  cursor: pointer;
}
.feedback-categories button.is-active {
  border-color: #aabff3;
  color: var(--primary);
  background: var(--primary-soft);
}
.feedback-form label {
  display: grid;
  gap: 7px;
}
.feedback-form label > span {
  color: var(--ink-soft);
  font-size: 9px;
  font-weight: 750;
}
.feedback-form label > span em {
  color: var(--muted);
  font-size: 8px;
  font-style: normal;
}
.feedback-form input,
.feedback-form textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  outline: none;
  background: var(--surface-soft);
  font-size: 10px;
  line-height: 1.65;
  resize: vertical;
}
.feedback-form input {
  height: 40px;
}
.feedback-form input:focus,
.feedback-form textarea:focus {
  border-color: var(--brand-400);
  background: var(--surface-raised);
  box-shadow: 0 0 0 3px rgb(45 80 70 / 9%);
}
.feedback-form label > small {
  justify-self: end;
  color: var(--muted);
  font-size: 8px;
}
.feedback-error,
.feedback-success {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  font-size: 9px;
}
.feedback-error {
  color: var(--danger);
}
.feedback-success {
  color: var(--mint);
}
.feedback-actions {
  display: flex;
  gap: 8px;
}
.feedback-actions .app-button {
  min-height: 38px;
  font-size: 10px;
}
.help-side-card {
  padding: 17px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
}
.help-side-card h2 {
  margin: 0 0 12px;
  font-size: 12px;
}
.help-side-card > a {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 0;
  border-top: 1px solid var(--line);
}
.help-side-card > a > span {
  display: flex;
  flex-direction: column;
}
.help-side-card > a strong {
  font-size: 9px;
}
.help-side-card > a small {
  margin-top: 2px;
  color: var(--muted);
  font-size: 8px;
}
.health-row {
  display: grid;
  align-items: center;
  gap: 7px;
  padding: 8px 0;
  grid-template-columns: auto 1fr auto;
  font-size: 9px;
}
.health-row i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--amber);
}
.health-row i.ok {
  background: var(--mint);
}
.health-row strong {
  color: var(--muted);
  font-size: 8px;
}
.help-api {
  margin: 9px 0 0;
  overflow-wrap: anywhere;
  color: var(--muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 7px;
}
.feedback-history {
  padding: 9px 0;
  border-top: 1px solid var(--line);
}
.feedback-history span {
  color: var(--primary);
  font-size: 8px;
  font-weight: 750;
}
.feedback-history p {
  display: -webkit-box;
  overflow: hidden;
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 8px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
@media (max-width: 900px) {
  .help-layout {
    grid-template-columns: 1fr;
  }
  .help-side {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 650px) {
  .help-page {
    padding: 22px 18px 44px;
  }
  .help-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .help-steps,
  .help-side {
    grid-template-columns: 1fr;
  }
  .feedback-actions {
    flex-direction: column;
  }
}
</style>
