import type { RouteRecordRaw } from 'vue-router'
const studioMeta = { surface: 'studio' as const }

export const studioRoutes: RouteRecordRaw[] = [
  {
    path: '/projects/new',
    name: 'new-project',
    component: () => import('@/views/NewProjectView.vue'),
    meta: studioMeta,
  },
  {
    path: '/templates',
    name: 'templates',
    component: () => import('@/views/TemplatesView.vue'),
    meta: studioMeta,
  },
  {
    path: '/ai',
    name: 'ai-generator',
    component: () => import('@/views/AiGeneratorView.vue'),
    meta: studioMeta,
  },
  {
    path: '/editor/:projectId',
    name: 'editor',
    component: () => import('@/views/EditorView.vue'),
    meta: studioMeta,
  },
]
