import type { RouteRecordRaw } from 'vue-router'
import PublicLayout from '@/layouts/PublicLayout.vue'
export const publicRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: PublicLayout,
    meta: { public: true, surface: 'public' },
    children: [
      {
        path: '',
        name: 'landing',
        component: () => import('@/views/LandingView.vue'),
      },
      {
        path: 'campus',
        name: 'campus-activities',
        component: () => import('@/views/CampusActivitiesView.vue'),
      },
      {
        path: 'resources',
        name: 'wellbeing-resources',
        component: () => import('@/views/WellbeingResourcesView.vue'),
      },
      {
        path: 'tools',
        name: 'wellbeing-tools',
        component: () => import('@/views/WellbeingToolsView.vue'),
      },
      {
        path: 'trace',
        name: 'my-trace',
        component: () => import('@/views/TraceTimelineView.vue'),
      },
      {
        path: 'about',
        name: 'wellbeing-about',
        component: () => import('@/views/WellbeingAboutView.vue'),
      },
    ],
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, surface: 'public' },
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/ForgotPasswordView.vue'),
    meta: { public: true, surface: 'public' },
  },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('@/views/ResetPasswordView.vue'),
    meta: { public: true, surface: 'public' },
  },
  {
    path: '/p/:slug',
    name: 'public-activity',
    component: () => import('@/views/PublicActivityView.vue'),
    meta: { public: true, surface: 'public' },
  },
]
