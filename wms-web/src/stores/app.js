import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getStoredCurrentWarehouseId,
  getStoredPreferences,
  getStoredTheme,
  setStoredCurrentWarehouseId,
  setStoredPreferences,
  setStoredTheme,
} from '@/utils/storage'

const MOBILE_QUERY = '(max-width: 900px)'

function resolveInitialTheme() {
  const stored = getStoredTheme()
  if (stored === 'dark' || stored === 'light') return stored
  return window.matchMedia?.('(prefers-color-scheme: dark)')?.matches ? 'dark' : 'light'
}

function resolveIsMobile() {
  return Boolean(window.matchMedia?.(MOBILE_QUERY)?.matches)
}

export const useAppStore = defineStore('app', () => {
  /** 桌面端：折叠成图标条 */
  const sidebarCollapsed = ref(false)
  /** 移动端：抽屉式侧边栏是否展开 */
  const mobileSidebarOpen = ref(false)
  const isMobile = ref(resolveIsMobile())
  const theme = ref(resolveInitialTheme())
  /** 当前仓库：作为业务页面的默认过滤范围，可随时在顶栏切换 */
  const currentWarehouseId = ref(getStoredCurrentWarehouseId())
  /** 用户界面偏好。只影响本浏览器，不伪装成服务端全局配置 */
  const storedPreferences = getStoredPreferences()
  const alertDays = ref([7, 15, 30, 60, 90].includes(Number(storedPreferences.alertDays)) ? Number(storedPreferences.alertDays) : 30)
  const pageSize = ref([10, 20, 50, 100].includes(Number(storedPreferences.pageSize)) ? Number(storedPreferences.pageSize) : 10)

  const isDark = computed(() => theme.value === 'dark')

  function applyTheme() {
    document.documentElement.classList.toggle('dark', isDark.value)
    setStoredTheme(theme.value)
  }

  function toggleTheme() {
    theme.value = isDark.value ? 'light' : 'dark'
    applyTheme()
  }

  /** 同一个按钮，窄屏控制抽屉、宽屏控制折叠 */
  function toggleSidebar() {
    if (isMobile.value) mobileSidebarOpen.value = !mobileSidebarOpen.value
    else sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function closeMobileSidebar() {
    mobileSidebarOpen.value = false
  }

  const mql = window.matchMedia?.(MOBILE_QUERY)
  mql?.addEventListener?.('change', (event) => {
    isMobile.value = event.matches
    // 切回桌面端时，把抽屉状态清掉，避免影响折叠逻辑
    if (!event.matches) mobileSidebarOpen.value = false
  })

  function setCurrentWarehouse(id) {
    currentWarehouseId.value = id ? Number(id) : null
    setStoredCurrentWarehouseId(currentWarehouseId.value)
  }

  function updatePreferences(next = {}) {
    if (next.alertDays !== undefined) alertDays.value = Number(next.alertDays)
    if (next.pageSize !== undefined) pageSize.value = Number(next.pageSize)
    setStoredPreferences({ alertDays: alertDays.value, pageSize: pageSize.value })
  }

  function resetPreferences() {
    alertDays.value = 30
    pageSize.value = 10
    setStoredPreferences({ alertDays: 30, pageSize: 10 })
  }
  return {
    sidebarCollapsed,
    mobileSidebarOpen,
    isMobile,
    theme,
    isDark,
    currentWarehouseId,
    alertDays,
    pageSize,
    applyTheme,
    toggleTheme,
    toggleSidebar,
    closeMobileSidebar,
    setCurrentWarehouse,
    updatePreferences,
    resetPreferences,
  }
})
