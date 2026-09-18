import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

const APP_TITLE = import.meta.env.VITE_APP_TITLE || 'WMS 智能仓储管理系统'

/** 全局守卫：未登录一律拦到登录页，并带上 redirect 以便登录后原路返回 */
router.beforeEach((to) => {
  const auth = useAuthStore(pinia)

  if (to.meta.public) {
    // 已登录还去登录页 → 直接送回首页
    if (to.name === 'Login' && auth.isLoggedIn) return { path: '/' }
    return true
  }

  if (!auth.isLoggedIn) {
    return {
      path: '/login',
      query: to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : undefined,
    }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
})

export default router