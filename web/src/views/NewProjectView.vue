<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import WorkspaceShell from '@/components/studio/WorkspaceShell.vue'
import Icon from '@/components/ui/Icon.vue'
import { useToast } from '@/composables/useToast'
import { useProjectStore } from '@/stores/projects'
import { loadTemplateCatalog, type TemplateCatalogItem } from '@/services/templates'

const router = useRouter()
const projectStore = useProjectStore()
const toast = useToast()
const selectedTemplate = ref('recruit')
const templates = ref<TemplateCatalogItem[]>([])
const title = ref('考试周心晴游园会')
const org = ref('心屿心理协会')
const activityType = ref('心晴活动')
const prompt = ref(
  '做一个考试周心晴游园会页面，需要活动介绍、互动摊位、匿名报名和保密说明。整体风格温柔、安静，不要做心理诊断。'
)

let pendingProjectId = ''

onMounted(async () => {
  templates.value = await loadTemplateCatalog()
  if (!templates.value.some((item) => item.id === selectedTemplate.value)) {
    selectedTemplate.value = templates.value[0]?.id ?? ''
  }
})

async function ensureProject() {
  if (pendingProjectId) {
    const existing = projectStore.getById(pendingProjectId)
    if (existing) return existing
  }
  const template = templates.value.find((item) => item.id === selectedTemplate.value)
  const project = await projectStore.createProjectRemote({
    title: title.value,
    org: org.value,
    type: activityType.value.trim() || template?.name || '心理活动',
    templateId: selectedTemplate.value,
  })
  pendingProjectId = project.id
  return project
}

async function startWithTemplate(): Promise<void> {
  try {
    const project = await ensureProject()
    await router.push(`/editor/${project.id}`)
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '创建项目失败，请稍后重试', 'error')
  }
}

async function openCreate(tab: 'poster'): Promise<void> {
  try {
    const project = await ensureProject()
    await router.push({ path: `/editor/${project.id}`, query: { create: tab } })
  } catch (error) {
    toast.pushToast(error instanceof Error ? error.message : '创建项目失败，请稍后重试', 'error')
  }
}

function openAiGenerator(): void {
  const value = prompt.value.trim()
  void router.push({
    path: '/ai',
    query: value ? { prompt: value } : undefined,
  })
}

function templateTone(id: string): string {
  const category = templates.value.find((item) => item.id === id)?.category
  return (
    { recruit: 'photo', recruitment: 'photo', signup: 'event', registration: 'event', vote: 'vote', checkin: 'habit' }[
      category || id
    ] ?? 'event'
  )
}
</script>

<template>
  <WorkspaceShell>
    <main class="create-page studio-create">
      <div class="create-heading">
        <div>
          <RouterLink class="create-back" to="/projects"><span>←</span>返回心晴运营台</RouterLink>
          <p class="eyebrow">Page workshop</p>
          <h1>制作一个心晴活动页面</h1>
          <p>从心理活动模板开始，也可以上传海报，让 AI 帮你整理活动信息。</p>
        </div>
        <div class="create-steps" aria-label="创建流程">
          <span class="is-active"><b>1</b> 描述活动</span><i /> <span><b>2</b> 生成页面</span><i />
          <span><b>3</b> 进入编辑</span>
        </div>
      </div>

      <div class="create-layout">
        <section class="create-form-card">
          <div class="create-section-heading">
            <span>01</span>
            <div>
              <h2>基础信息</h2>
              <p>先给活动起一个清晰的名字。</p>
            </div>
          </div>
          <label class="field">
            <span>活动名称</span>
            <input v-model="title" />
            <small>页面顶部和分享卡片都会使用这个名称。</small>
          </label>
          <div class="field-row">
            <label class="field"><span>组织者</span><input v-model="org" /></label>
            <label class="field">
              <span>活动类型</span>
              <select v-model="activityType">
                <option value="心理社团">心理社团</option>
                <option value="心晴活动">心晴活动</option>
                <option value="心理科普">心理科普</option>
                <option value="自我关怀">自我关怀</option>
                <option value="安心表达">安心表达</option>
              </select>
            </label>
          </div>

          <div class="create-divider" />
          <div class="create-section-heading">
            <span>02</span>
            <div>
              <h2>从心晴模板开始</h2>
              <p>模板已经包含心理活动常用页面和安心说明。</p>
            </div>
          </div>
          <div id="templates" class="template-grid">
            <label
              v-for="template in templates"
              :key="template.id"
              class="template-option"
              :class="`template-option--${templateTone(template.id)}`"
            >
              <input v-model="selectedTemplate" name="template" type="radio" :value="template.id" />
              <span class="template-check"><Icon name="check" :size="12" /></span>
              <span class="template-code">{{ template.scene }}</span>
              <span class="template-art" aria-hidden="true"><i /><i /><i /></span>
              <strong>{{ template.name }}</strong>
              <small>{{ template.tagline }}</small>
            </label>
          </div>

          <div class="create-divider" />
          <div class="create-section-heading">
            <span>03</span>
            <div>
              <h2>让 AI 帮你构思页面</h2>
              <p>描述活动对象和参与方式，或者上传一张活动海报。</p>
            </div>
          </div>
          <label class="field">
            <span>活动需求</span>
            <textarea v-model="prompt" rows="5" />
            <small>描述得越具体，生成的内容越接近你的预期。</small>
          </label>
          <button class="poster-upload" type="button" @click="openCreate('poster')">
            <span class="poster-upload-icon"><Icon name="upload" :size="19" /></span>
            <span><strong>制作活动海报</strong><small>上传已有图片，或让 AI 生成新的视觉底图</small></span>
            <span class="poster-upload-action">开始制作</span>
          </button>

          <div class="create-form-actions">
            <RouterLink class="app-button app-button--secondary" to="/projects">取消</RouterLink>
            <button class="app-button app-button--secondary" type="button" @click="openAiGenerator">
              <Icon name="spark" :size="16" /> AI 辅助生成
            </button>
            <button class="app-button app-button--primary" type="button" @click="startWithTemplate">
              <Icon name="wand" :size="16" /> 使用模板进入页面工坊
            </button>
          </div>
        </section>

        <aside class="create-preview">
          <div class="create-preview-header">
            <span>页面工坊将为你准备</span>
            <span class="create-preview-badge"><Icon name="spark" :size="12" />智能生成</span>
          </div>
          <div class="create-phone">
            <div class="create-phone-nav"><span>心屿心理协会</span><span>•••</span></div>
            <div class="create-phone-hero">
              <span>PAUSE · BREATHE · CONNECT</span><strong>给忙碌的心<br />留一小块空地</strong><i />
            </div>
            <div class="create-phone-content">
              <span class="create-phone-kicker">A GENTLE EVENT</span>
              <strong>考试周，也可以有一点轻松</strong>
              <p>一起做几个轻量互动练习，不需要分享不想说的故事。</p>
              <div class="create-phone-info"><span>本周五 18:30</span><span>心理健康中心活动室</span></div>
              <div class="create-phone-button">匿名报名</div>
            </div>
          </div>
          <div class="create-preview-list">
            <div>
              <span class="create-preview-check"><Icon name="check" :size="11" /></span
              ><span><strong>多页面活动页</strong><small>介绍、参与说明、表单、确认页</small></span>
            </div>
            <div>
              <span class="create-preview-check"><Icon name="check" :size="11" /></span
              ><span><strong>安心参与组件</strong><small>信息卡、保密提示、表单、二维码</small></span>
            </div>
            <div>
              <span class="create-preview-check"><Icon name="check" :size="11" /></span
              ><span><strong>发布与参与数据</strong><small>实时预览、分享二维码和参与统计</small></span>
            </div>
          </div>
          <p class="create-preview-tip">生成后仍可以在页面工坊里自由修改文案、颜色和页面结构。</p>
        </aside>
      </div>
    </main>
  </WorkspaceShell>
</template>
