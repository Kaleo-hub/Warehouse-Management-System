import { ElMessage } from 'element-plus'
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

  /*
   * 页面级权限：路由 meta.roles 声明了角色就校验。
   *
   * 【为什么前端也要挡一道】后端已经用 @PreAuthorize 挡住了接口，
   * 但作业员手敲 /system/users 还是能看到一个空壳页面、点啥都报错，
   * 体验上像是「页面坏了」。这里直接送回工作台并说明原因。
   * 记住这【不是】安全边界 —— 真正的边界在后端。
   */
  if (to.meta.roles?.length && !auth.hasRole(...to.meta.roles)) {
    ElMessage.warning('当前角色没有权限访问该页面')
    return { path: '/dashboard' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
})

export default router