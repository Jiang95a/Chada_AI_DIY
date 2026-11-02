import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import ProjectsView from '@/views/ProjectsView.vue'
import NewProjectView from '@/views/NewProjectView.vue'
import TemplatesView from '@/views/TemplatesView.vue'
import EditorView from '@/views/EditorView.vue'
import PublicActivityView from '@/views/PublicActivityView.vue'
import LoginView from '@/views/LoginView.vue'
import ProjectDataView from '@/views/ProjectDataView.vue'
import CampusActivitiesView from '@/views/CampusActivitiesView.vue'
import ProfileView from '@/views/ProfileView.vue'
import SettingsView from '@/views/SettingsView.vue'
import HelpView from '@/views/HelpView.vue'
import LandingView from '@/views/LandingView.vue'
import AiGeneratorView from '@/views/AiGeneratorView.vue'
import { getAccessToken } from '@/services/http'

export const appRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'landing',
    component: LandingView,
    meta: { public: true },
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { public: true },
  },
  {
    path: '/projects',
    name: 'projects',
    component: ProjectsView,
  },
  {
    path: '/projects/new',
    name: 'new-project',
    component: NewProjectView,
  },
  {
    path: '/templates',
    name: 'templates',
    component: TemplatesView,
  },
  {
    path: '/ai',
    name: 'ai-generator',
    component: AiGeneratorView,
  },
  {
    path: '/campus',
    name: 'campus-activities',
    component: CampusActivitiesView,
  },
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView,
  },
  {
    path: '/settings',
    name: 'settings',
    component: SettingsView,
  },
  {
    path: '/help',
    name: 'help',
    component: HelpView,
  },
  {
    path: '/projects/:projectId/analytics',
    name: 'project-analytics',
    component: ProjectDataView,
  },
  {
    path: '/projects/:projectId/submissions',
    name: 'project-submissions',
    component: ProjectDataView,
  },
  {
    path: '/projects/:projectId/exports',
    name: 'project-exports',
    component: ProjectDataView,
  },
  {
    path: '/editor/:projectId',
    name: 'editor',
    component: EditorView,
  },
  {
    path: '/p/:slug',
    name: 'public-activity',
    component: PublicActivityView,
    meta: { public: true },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/projects',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: appRoutes,
})

router.beforeEach((to) => {
  if (to.meta.public || to.path === '/login') return true
  if (getAccessToken()) return true
  return {
    path: '/login',
    query: { redirect: to.fullPath },
  }
})

window.addEventListener('auth-expired', () => {
  const current = router.currentRoute.value
  if (current.path === '/login' || current.meta.public) return
  void router.replace({ path: '/login', query: { redirect: current.fullPath } })
})

export default router
