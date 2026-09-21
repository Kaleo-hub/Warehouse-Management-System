<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { menuGroups } from '../menu'
import { useAppStore } from '@/stores/app'
import { getStoredMenuGroups, setStoredMenuGroups } from '@/utils/storage'

const route = useRoute()
const app = useAppStore()
const appVersion = import.meta.env.VITE_APP_VERSION || '0.1.0'

/** 移动端不做图标折叠，一律展开文字 */
const collapsed = computed(() => !app.isMobile && app.sidebarCollapsed)

/**
 * 侧边栏分组折叠。
 *
 * 【为什么要折叠】库存模块做完之后菜单有 6 个模块、17 个入口，
 * 全部铺开要滚一屏，「找入口」比「点进去」还费劲。
 * 折叠之后默认只展开「当前页所在的那个模块」，一屏能看全所有模块名。
 *
 * 状态存在 localStorage 里：手点开的分组刷新后还是开的，不用每次重新点。
 * 路由变化时会自动把当前页所在的分组补进展开列表（但不会收起别人 ——
 * 如果切个页面就把用户刚展开的分组关掉，那是帮倒忙）。
 */
const openGroups = ref(getStoredMenuGroups())

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function hasActiveItem(index) {
  return menuGroups[index].items.some((item) => isActive(item.path))
}

/** 只有一个入口的分组（概览 / 系统）不折叠：折它没有意义，还多一次点击 */
function isCollapsible(index) {
  return menuGroups[index].items.length > 1
}

function isGroupOpen(index) {
  // 图标条模式下没有分组标题可点，所有入口都要露出来
  if (collapsed.value) return true
  if (!isCollapsible(index)) return true
  return openGroups.value.includes(index)
}

function persist() {
  setStoredMenuGroups(openGroups.value)
}

function toggleGroup(index) {
  if (!isCollapsible(index)) return
  openGroups.value = openGroups.value.includes(index)
    ? openGroups.value.filter((it) => it !== index)
    : [...openGroups.value, index]
  persist()
}

function syncOpenGroups() {
  const next = new Set(openGroups.value)
  menuGroups.forEach((group, index) => {
    if (group.items.some((item) => isActive(item.path))) next.add(index)
  })
  openGroups.value = [...next]
  persist()
}

watch(
  () => route.path,
  () => {
    app.closeMobileSidebar()
    syncOpenGroups()
  },
  { immediate: true },
)
</script>

<template>
  <aside
    class="sidebar"
    :class="{ 'is-collapsed': collapsed, 'is-mobile-open': app.mobileSidebarOpen }"
  >
    <div class="sidebar__brand">
      <span class="sidebar__mark" aria-hidden="true">
        <svg viewBox="0 0 32 32" width="22" height="22">
          <defs>
            <linearGradient id="sideLogo" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stop-color="#8aa4ff" />
              <stop offset="1" stop-color="#38bdf8" />
            </linearGradient>
          </defs>
          <path
            d="M16 4.5 27 10.5v11L16 27.5 5 21.5v-11z"
            fill="none"
            stroke="url(#sideLogo)"
            stroke-width="1.8"
            stroke-linejoin="round"
          />
          <path
            d="M5 10.5 16 16.5l11-6M16 16.5v11"
            fill="none"
            stroke="url(#sideLogo)"
            stroke-width="1.8"
            stroke-linejoin="round"
            opacity=".85"
          />
        </svg>
      </span>
      <transition name="brand-text">
        <span v-if="!collapsed" class="sidebar__title">
          <strong>WMS</strong>
          <em>智能仓储管理</em>
        </span>
      </transition>
    </div>

    <nav class="sidebar__nav">
      <div v-for="(group, index) in menuGroups" :key="group.title" class="nav-group">
        <!-- 图标条模式没有文字，用一条分隔线代替分组标题 -->
        <div v-if="collapsed" class="nav-group__divider"></div>

        <!-- 可折叠：整行就是按钮，点标题展开 / 收起 -->
        <button
          v-else-if="isCollapsible(index)"
          type="button"
          class="nav-group__toggle"
          :class="{ 'is-open': isGroupOpen(index) }"
          :aria-expanded="isGroupOpen(index)"
          @click="toggleGroup(index)"
        >
          <span class="nav-group__title">{{ group.title }}</span>
          <span
            v-if="hasActiveItem(index) && !isGroupOpen(index)"
            class="nav-group__dot"
            aria-hidden="true"
          ></span>
          <el-icon class="nav-group__arrow"><ArrowDown /></el-icon>
        </button>

        <!-- 只有一个入口的分组：标题就是普通文字 -->
        <p v-else class="nav-group__title nav-group__title--static">{{ group.title }}</p>

        <el-collapse-transition>
          <div v-show="isGroupOpen(index)" class="nav-group__items">
            <template v-for="item in group.items" :key="item.path">
              <router-link
                v-if="!item.soon"
                class="nav-item"
                :class="{ 'is-active': isActive(item.path) }"
                :to="item.path"
                :title="collapsed ? item.title : undefined"
              >
                <el-icon class="nav-item__icon"><component :is="item.icon" /></el-icon>
                <span v-if="!collapsed" class="nav-item__text">{{ item.title }}</span>
              </router-link>

              <span
                v-else
                class="nav-item is-soon"
                :title="collapsed ? `${item.title}（规划中）` : undefined"
              >
                <el-icon class="nav-item__icon"><component :is="item.icon" /></el-icon>
                <span v-if="!collapsed" class="nav-item__text">{{ item.title }}</span>
                <span v-if="!collapsed" class="nav-item__badge">规划中</span>
              </span>
            </template>
          </div>
        </el-collapse-transition>
      </div>
    </nav>

    <div class="sidebar__foot">
      <div class="sidebar__status" :class="{ 'is-compact': collapsed }">
        <span class="sidebar__status-dot"></span>
        <span v-if="!collapsed" class="sidebar__status-text">
          <strong>服务已连接</strong>
          <em>v{{ appVersion }}</em>
        </span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 120;
  display: flex;
  flex-direction: column;
  width: var(--sidebar-w);
  background: linear-gradient(180deg, var(--sidebar-bg) 0%, var(--sidebar-bg-2) 100%);
  border-right: 1px solid var(--sidebar-border);
  transition: width var(--duration-base) var(--ease-in-out);
}

.sidebar.is-collapsed {
  width: var(--sidebar-w-collapsed);
}

/* ---------- 品牌 ---------- */
.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--header-h);
  padding: 0 16px;
  border-bottom: 1px solid var(--sidebar-border);
}

.sidebar.is-collapsed .sidebar__brand {
  justify-content: center;
  padding: 0;
}

.sidebar__mark {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(138, 164, 255, 0.24);
  border-radius: 10px;
  background: rgba(138, 164, 255, 0.09);
}

.sidebar__title {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  line-height: 1.3;
  white-space: nowrap;
}

.sidebar__title strong {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: #ffffff;
}

.sidebar__title em {
  font-size: 11px;
  font-style: normal;
  color: var(--sidebar-text);
}

.brand-text-enter-active,
.brand-text-leave-active {
  transition: opacity var(--duration-fast) var(--ease-in-out);
}

.brand-text-enter-from,
.brand-text-leave-to {
  opacity: 0;
}

/* ---------- 导航 ---------- */
.sidebar__nav {
  flex: 1 1 auto;
  padding: 12px 10px;
  overflow-x: hidden;
  overflow-y: auto;
}

.nav-group + .nav-group {
  margin-top: 2px;
}

/* 分组标题既是标题也是按钮：整行可点，带一个箭头表示能不能展开 */
.nav-group__toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 10px 7px;
  color: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.nav-group__toggle:hover .nav-group__title {
  color: rgba(214, 226, 250, 0.92);
}

.nav-group__toggle:hover .nav-group__arrow {
  opacity: 1;
}

.nav-group__title {
  flex: 1 1 auto;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-align: left;
  text-transform: uppercase;
  color: rgba(151, 163, 189, 0.62);
  transition: color var(--duration-fast) var(--ease-in-out);
}

/* 单入口分组的标题：只是文字，不能点 */
.nav-group__title--static {
  display: block;
  padding: 9px 10px 7px;
}

.nav-group__toggle.is-open .nav-group__title {
  color: rgba(151, 163, 189, 0.86);
}

/* 收起状态但里面装着当前页时，给个圆点，免得「当前页去哪了」 */
.nav-group__dot {
  flex: 0 0 auto;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.18);
}

.nav-group__arrow {
  flex: 0 0 auto;
  font-size: 11px;
  color: rgba(151, 163, 189, 0.7);
  opacity: 0.55;
  transition: transform var(--duration-base) var(--ease-in-out),
    opacity var(--duration-fast) var(--ease-in-out);
  transform: rotate(-90deg);
}

.nav-group__toggle.is-open .nav-group__arrow {
  transform: rotate(0deg);
}

.nav-group__items {
  overflow: hidden;
}

.nav-group__divider {
  height: 1px;
  margin: 8px 6px 10px;
  background: var(--sidebar-border);
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  height: 40px;
  margin-bottom: 3px;
  padding: 0 11px;
  font-size: 13.5px;
  color: var(--sidebar-text);
  border-radius: 9px;
  text-decoration: none;
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-in-out),
    background-color var(--duration-fast) var(--ease-in-out);
}

.sidebar.is-collapsed .nav-item {
  justify-content: center;
  padding: 0;
}

.nav-item:hover {
  color: #e8efff;
  background: rgba(255, 255, 255, 0.06);
}

.nav-item.is-active {
  color: var(--sidebar-text-active);
  background: linear-gradient(100deg, rgba(59, 100, 244, 0.9), rgba(59, 100, 244, 0.55));
  box-shadow: 0 10px 20px -14px rgba(59, 100, 244, 1);
}

.nav-item.is-active::before {
  position: absolute;
  top: 50%;
  left: -10px;
  width: 3px;
  height: 20px;
  border-radius: 0 3px 3px 0;
  background: #38bdf8;
  content: '';
  transform: translateY(-50%);
}

.nav-item.is-soon {
  color: rgba(151, 163, 189, 0.44);
  cursor: not-allowed;
}

.nav-item.is-soon:hover {
  color: rgba(151, 163, 189, 0.44);
  background: transparent;
}

.nav-item__icon {
  flex: 0 0 auto;
  font-size: 16px;
}

.nav-item__text {
  flex: 1 1 auto;
  overflow: hidden;
  white-space: nowrap;
}

.nav-item__badge {
  flex: 0 0 auto;
  padding: 1px 6px;
  font-size: 10px;
  color: rgba(186, 202, 232, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
}

/* ---------- 底部 ---------- */
.sidebar__foot {
  padding: 12px 14px 16px;
  border-top: 1px solid var(--sidebar-border);
}

.sidebar__status {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.04);
}

.sidebar__status.is-compact {
  justify-content: center;
  padding: 9px 0;
}

.sidebar__status-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.16);
}

.sidebar__status-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  white-space: nowrap;
}

.sidebar__status-text strong {
  font-size: 11.5px;
  font-weight: 600;
  color: #d7e2f7;
}

.sidebar__status-text em {
  font-size: 10.5px;
  font-style: normal;
  color: rgba(151, 163, 189, 0.7);
}

/* ---------- 窄屏：抽屉式侧边栏 ---------- */
@media (max-width: 900px) {
  .sidebar {
    width: var(--sidebar-w);
    transform: translateX(-100%);
    transition: transform var(--duration-base) var(--ease-out);
  }

  .sidebar.is-mobile-open {
    transform: translateX(0);
    box-shadow: var(--shadow-xl);
  }
}
</style>