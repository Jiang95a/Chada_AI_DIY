import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@/services/http'
import { publicRoutes } from './public.routes'
import { operationsRoutes } from './operations.routes'
import { studioRoutes } from './studio.routes'

export const appRoutes: RouteRecordRaw[] = [
  ...publicRoutes,
  ...operationsRoutes,
  ...studioRoutes,
  {
    path: '/:pathMatch(.*)*',
    redirect: '/projects',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: appRoutes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) {
      return {
        el: to.hash,
        top: 84,
        behavior: 'smooth',
      }
    }
    return { top: 0 }
  },
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
