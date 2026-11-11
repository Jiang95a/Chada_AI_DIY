import type { RouteRecordRaw } from 'vue-router'
import PublicLayout from '@/layouts/PublicLayout.vue'
import LandingView from '@/views/LandingView.vue'
import CampusActivitiesView from '@/views/CampusActivitiesView.vue'
import WellbeingResourcesView from '@/views/WellbeingResourcesView.vue'
import WellbeingToolsView from '@/views/WellbeingToolsView.vue'
import WellbeingAboutView from '@/views/WellbeingAboutView.vue'
import PublicActivityView from '@/views/PublicActivityView.vue'
import LoginView from '@/views/LoginView.vue'

export const publicRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: PublicLayout,
    meta: { public: true, surface: 'public' },
    children: [
      {
        path: '',
        name: 'landing',
        component: LandingView,
      },
      {
        path: 'campus',
        name: 'campus-activities',
        component: CampusActivitiesView,
      },
      {
        path: 'resources',
        name: 'wellbeing-resources',
        component: WellbeingResourcesView,
      },
      {
        path: 'tools',
        name: 'wellbeing-tools',
        component: WellbeingToolsView,
      },
      {
        path: 'about',
        name: 'wellbeing-about',
        component: WellbeingAboutView,
      },
    ],
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { public: true, surface: 'public' },
  },
  {
    path: '/p/:slug',
    name: 'public-activity',
    component: PublicActivityView,
    meta: { public: true, surface: 'public' },
  },
]
