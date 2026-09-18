import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getStoredTheme, setStoredTheme } from '@/utils/storage'

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

  return {
    sidebarCollapsed,
    mobileSidebarOpen,
    isMobile,
    theme,
    isDark,
    applyTheme,
    toggleTheme,
    toggleSidebar,
    closeMobileSidebar,
  }
})