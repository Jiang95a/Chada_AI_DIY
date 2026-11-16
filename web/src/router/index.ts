import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@/services/http'
import { useEditorStore } from '@/stores/editor'
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
  // 被踢回登录页之前，先把编辑器里还没落盘的改动同步存一次
  try {
    const editor = useEditorStore()
    if (current.path.startsWith('/editor/')) editor.saveNow()
  } catch {
    // pinia 还没就绪时忽略，不能因为存草稿挡住跳转
  }
  void router.replace({ path: '/login', query: { redirect: current.fullPath } })
})

export default router
