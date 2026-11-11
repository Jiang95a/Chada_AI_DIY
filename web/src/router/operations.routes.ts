import type { RouteRecordRaw } from 'vue-router'
import ProjectsView from '@/views/ProjectsView.vue'
import ProjectDataView from '@/views/ProjectDataView.vue'
import PublishedDataView from '@/views/PublishedDataView.vue'
import ReferralsView from '@/views/ReferralsView.vue'
import ProfileView from '@/views/ProfileView.vue'
import SettingsView from '@/views/SettingsView.vue'
import HelpView from '@/views/HelpView.vue'

const operationMeta = { surface: 'operations' as const }

export const operationsRoutes: RouteRecordRaw[] = [
  {
    path: '/projects',
    name: 'projects',
    component: ProjectsView,
    meta: operationMeta,
  },
  {
    path: '/activities',
    redirect: '/projects',
  },
  {
    path: '/published',
    name: 'published-data',
    component: PublishedDataView,
    meta: operationMeta,
  },
  {
    path: '/referrals',
    name: 'referral-queue',
    component: ReferralsView,
    meta: operationMeta,
  },
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView,
    meta: operationMeta,
  },
  {
    path: '/settings',
    name: 'settings',
    component: SettingsView,
    meta: operationMeta,
  },
  {
    path: '/help',
    name: 'help',
    component: HelpView,
    meta: operationMeta,
  },
  {
    path: '/projects/:projectId/analytics',
    name: 'project-analytics',
    component: ProjectDataView,
    meta: operationMeta,
  },
  {
    path: '/projects/:projectId/submissions',
    name: 'project-submissions',
    component: ProjectDataView,
    meta: operationMeta,
  },
  {
    path: '/projects/:projectId/exports',
    name: 'project-exports',
    component: ProjectDataView,
    meta: operationMeta,
  },
]
