import type { RouteRecordRaw } from 'vue-router'
const operationMeta = { surface: 'operations' as const }

export const operationsRoutes: RouteRecordRaw[] = [
  {
    path: '/projects',
    name: 'projects',
    component: () => import('@/views/ProjectsView.vue'),
    meta: operationMeta,
  },
  {
    path: '/activities',
    redirect: '/projects',
  },
  {
    path: '/published',
    name: 'published-data',
    component: () => import('@/views/PublishedDataView.vue'),
    meta: operationMeta,
  },
  {
    path: '/referrals',
    name: 'referral-queue',
    component: () => import('@/views/ReferralsView.vue'),
    meta: operationMeta,
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: operationMeta,
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: operationMeta,
  },
  {
    path: '/help',
    name: 'help',
    component: () => import('@/views/HelpView.vue'),
    meta: operationMeta,
  },
  {
    path: '/projects/:projectId/analytics',
    name: 'project-analytics',
    component: () => import('@/views/ProjectDataView.vue'),
    meta: operationMeta,
  },
  {
    path: '/projects/:projectId/submissions',
    name: 'project-submissions',
    component: () => import('@/views/ProjectDataView.vue'),
    meta: operationMeta,
  },
  {
    path: '/projects/:projectId/exports',
    name: 'project-exports',
    component: () => import('@/views/ProjectDataView.vue'),
    meta: operationMeta,
  },
]
