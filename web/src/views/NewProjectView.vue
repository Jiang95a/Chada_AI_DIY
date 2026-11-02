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
const title = ref('摄影社 2026 秋季招新')
const org = ref('光影摄影社')
const activityType = ref('招新')
const prompt = ref(
  '做一个摄影社秋季招新页面，需要社团介绍、三个部门、活动照片、报名表和负责人联系方式。整体风格温暖、有胶片感。'
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
    type: activityType.value.trim() || template?.name || '校园活动',
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
          <RouterLink class="create-back" to="/projects"><span>←</span>返回项目工作台</RouterLink>
          <p class="eyebrow">New activity</p>
          <h1>把一个想法变成活动页面</h1>
          <p>选择模板快速开始，也可以上传海报让 AI 自动整理内容。</p>
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
                <option value="招新">招新</option>
                <option value="比赛">比赛</option>
                <option value="讲座">讲座</option>
                <option value="志愿服务">志愿服务</option>
              </select>
            </label>
          </div>

          <div class="create-divider" />
          <div class="create-section-heading">
            <span>02</span>
            <div>
              <h2>从模板开始</h2>
              <p>模板已经包含常用页面和组件。</p>
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
              <h2>让 AI 帮你生成</h2>
              <p>描述活动需求，或者上传一张活动海报。</p>
            </div>
          </div>
          <label class="field">
            <span>活动需求</span>
            <textarea v-model="prompt" rows="5" />
            <small>描述得越具体，生成的内容越接近你的预期。</small>
          </label>
          <button class="poster-upload" type="button" @click="openCreate('poster')">
            <span class="poster-upload-icon"><Icon name="upload" :size="19" /></span>
            <span><strong>上传活动海报</strong><small>AI 会提取活动名称、时间、地点和报名信息</small></span>
            <span class="poster-upload-action">选择图片</span>
          </button>

          <div class="create-form-actions">
            <RouterLink class="app-button app-button--secondary" to="/projects">取消</RouterLink>
            <button class="app-button app-button--secondary" type="button" @click="openAiGenerator">
              <Icon name="spark" :size="16" /> AI 生成
            </button>
            <button class="app-button app-button--primary" type="button" @click="startWithTemplate">
              <Icon name="wand" :size="16" /> 使用模板进入编辑器
            </button>
          </div>
        </section>

        <aside class="create-preview">
          <div class="create-preview-header">
            <span>AI 将为你准备</span>
            <span class="create-preview-badge"><Icon name="spark" :size="12" />智能生成</span>
          </div>
          <div class="create-phone">
            <div class="create-phone-nav"><span>摄影社</span><span>•••</span></div>
            <div class="create-phone-hero">
              <span>JOIN US · 2026</span><strong>寻找校园里的<br />下一帧光</strong><i />
            </div>
            <div class="create-phone-content">
              <span class="create-phone-kicker">ABOUT US</span>
              <strong>用镜头记录校园的每一种可能</strong>
              <p>从日常街拍到大型活动，摄影社期待和你一起发现更多故事。</p>
              <div class="create-phone-info"><span>10 月 28 日 18:30</span><span>大学生活动中心 201</span></div>
              <div class="create-phone-button">立即报名</div>
            </div>
          </div>
          <div class="create-preview-list">
            <div>
              <span class="create-preview-check"><Icon name="check" :size="11" /></span
              ><span><strong>多页面 PageDocument</strong><small>首页、介绍、表单、成功页</small></span>
            </div>
            <div>
              <span class="create-preview-check"><Icon name="check" :size="11" /></span
              ><span><strong>13 个智能组件</strong><small>文字、图片、表单、二维码</small></span>
            </div>
            <div>
              <span class="create-preview-check"><Icon name="check" :size="11" /></span
              ><span><strong>发布与海报</strong><small>实时预览、二维码和分享海报</small></span>
            </div>
          </div>
          <p class="create-preview-tip">生成后仍可以在编辑器里自由修改文案、图片、颜色和页面结构。</p>
        </aside>
      </div>
    </main>
  </WorkspaceShell>
</template>
