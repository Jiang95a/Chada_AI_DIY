import type { RouteRecordRaw } from 'vue-router'
import NewProjectView from '@/views/NewProjectView.vue'
import TemplatesView from '@/views/TemplatesView.vue'
import EditorView from '@/views/EditorView.vue'
import AiGeneratorView from '@/views/AiGeneratorView.vue'

const studioMeta = { surface: 'studio' as const }

export const studioRoutes: RouteRecordRaw[] = [
  {
    path: '/projects/new',
    name: 'new-project',
    component: NewProjectView,
    meta: studioMeta,
  },
  {
    path: '/templates',
    name: 'templates',
    component: TemplatesView,
    meta: studioMeta,
  },
  {
    path: '/ai',
    name: 'ai-generator',
    component: AiGeneratorView,
    meta: studioMeta,
  },
  {
    path: '/editor/:projectId',
    name: 'editor',
    component: EditorView,
    meta: studioMeta,
  },
]
