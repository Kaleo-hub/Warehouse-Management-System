<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Bell, Close, Expand, Fold, FullScreen, Moon, SwitchButton, Sunny } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { roleLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const auth = useAuthStore()

const isFullscreen = ref(Boolean(document.fullscreenElement))

const breadcrumb = computed(() => route.meta.breadcrumb || [route.meta.title].filter(Boolean))
const collapseIcon = computed(() => {
  // 窄屏是抽屉（开/关），宽屏是折叠（展开/收起）
  if (app.isMobile) return app.mobileSidebarOpen ? Close : Fold
  return app.sidebarCollapsed ? Expand : Fold
})
const toggleTitle = computed(() => {
  if (app.isMobile) return app.mobileSidebarOpen ? '关闭菜单' : '打开菜单'
  return app.sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'
})

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
      isFullscreen.value = false
    } else {
      await document.documentElement.requestFullscreen()
      isFullscreen.value = true
    }
  } catch {
    ElMessage.warning('当前浏览器不允许进入全屏')
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  auth.logout()
  ElMessage.success('已安全退出')
  await router.replace('/login')
}
</script>

<template>
  <header class="header">
    <div class="header__left">
      <button
        class="icon-btn"
        type="button"
        :title="toggleTitle"
        @click="app.toggleSidebar()"
      >
        <el-icon><component :is="collapseIcon" /></el-icon>
      </button>

      <nav class="breadcrumb" aria-label="面包屑">
        <template v-for="(item, index) in breadcrumb" :key="item">
          <span v-if="index > 0" class="breadcrumb__sep">/</span>
          <span
            class="breadcrumb__item"
            :class="{ 'is-current': index === breadcrumb.length - 1 }"
          >
            {{ item }}
          </span>
        </template>
      </nav>
    </div>

    <div class="header__right">
      <button class="icon-btn" type="button" title="切换主题" @click="app.toggleTheme()">
        <el-icon><component :is="app.isDark ? Sunny : Moon" /></el-icon>
      </button>

      <el-badge :value="3" class="header__badge">
        <button class="icon-btn" type="button" title="通知">
          <el-icon><Bell /></el-icon>
        </button>
      </el-badge>

      <button class="icon-btn" type="button" title="全屏" @click="toggleFullscreen">
        <el-icon><component :is="isFullscreen ? Expand : FullScreen" /></el-icon>
      </button>

      <span class="header__divider"></span>

      <el-dropdown trigger="click" placement="bottom-end">
        <button class="profile" type="button">
          <span class="profile__avatar">{{ auth.initial }}</span>
          <span class="profile__meta">
            <strong>{{ auth.displayName }}</strong>
            <em>{{ roleLabel(auth.user?.role) }}</em>
          </span>
          <el-icon class="profile__caret"><ArrowDown /></el-icon>
        </button>

        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              账号 {{ auth.user?.username }}
            </el-dropdown-item>
            <el-dropdown-item divided :icon="SwitchButton" @click="handleLogout">
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: var(--header-h);
  padding: 0 var(--page-pad);
  background: color-mix(in srgb, var(--bg-surface) 88%, transparent);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-color);
}

.header__left,
.header__right {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  font-size: 16px;
  color: var(--text-secondary);
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.icon-btn:hover {
  color: var(--brand-500);
  border-color: var(--border-color);
  background: var(--bg-surface-2);
}

.header__badge {
  display: inline-flex;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  font-size: 13px;
  white-space: nowrap;
}

.breadcrumb__sep {
  color: var(--text-placeholder);
}

.breadcrumb__item {
  color: var(--text-secondary);
}

.breadcrumb__item.is-current {
  font-weight: 600;
  color: var(--text-primary);
}

.header__divider {
  width: 1px;
  height: 22px;
  margin: 0 4px;
  background: var(--border-color);
}

.profile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 10px 5px 5px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.profile:hover {
  border-color: var(--border-color);
  background: var(--bg-surface-2);
}

.profile__avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 30px;
  height: 30px;
  font-size: 13px;
  font-weight: 600;
  color: #ffffff;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-500), var(--accent-500));
}

.profile__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.3;
}

.profile__meta strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.profile__meta em {
  font-size: 11px;
  font-style: normal;
  color: var(--text-secondary);
}

.profile__caret {
  font-size: 12px;
  color: var(--text-placeholder);
}

@media (max-width: 720px) {
  .breadcrumb,
  .profile__meta,
  .header__badge {
    display: none;
  }
}
</style>