import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/index.css'

import App from './App.vue'
import router from './router'
import pinia from './stores'
import { useAppStore } from './stores/app'
import { useAuthStore } from './stores/auth'
import { onSessionExpired } from './utils/session'

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 首屏就把主题类挂到 <html>，避免亮/暗切换闪烁
useAppStore(pinia).applyTheme()

/*
 * 会话失效处理。
 *
 * 【必须同时清掉内存和本地存储】
 * 只清 localStorage 是不够的：Pinia 里的 token 是启动时读进内存的，
 * 内存里还在的话路由守卫会认为「已登录」，把 /login 又弹回首页，
 * 用户就会永远卡在过期状态里出不去（只能手动刷新浏览器）。
 *
 * 所以这里用 auth.logout()，它会把 store 和 localStorage 一起清掉。
 */
let sessionRedirecting = false

onSessionExpired(async () => {
  const auth = useAuthStore(pinia)
  if (!auth.isLoggedIn || sessionRedirecting) return

  sessionRedirecting = true
  try {
    const current = router.currentRoute.value
    auth.logout()
    ElMessage.error('登录状态已失效，请重新登录')
    if (current.path !== '/login') {
      await router.replace({ path: '/login', query: { redirect: current.fullPath } })
    }
  } finally {
    sessionRedirecting = false
  }
})

app.mount('#app')