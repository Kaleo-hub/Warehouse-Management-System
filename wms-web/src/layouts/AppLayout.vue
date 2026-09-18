<script setup>
import HeaderBar from './components/HeaderBar.vue'
import SidebarNav from './components/SidebarNav.vue'
import { useAppStore } from '@/stores/app'

const app = useAppStore()
</script>

<template>
  <div class="layout" :class="{ 'is-collapsed': app.sidebarCollapsed && !app.isMobile }">
    <SidebarNav />

    <transition name="backdrop">
      <div
        v-if="app.isMobile && app.mobileSidebarOpen"
        class="layout__backdrop"
        @click="app.closeMobileSidebar()"
      ></div>
    </transition>

    <div class="layout__main">
      <HeaderBar />

      <main class="layout__content">
        <router-view v-slot="{ Component }">
          <transition name="route-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--bg-app);
}

.layout__main {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  margin-left: var(--sidebar-w);
  transition: margin-left var(--duration-base) var(--ease-in-out);
}

.layout.is-collapsed .layout__main {
  margin-left: var(--sidebar-w-collapsed);
}

.layout__content {
  flex: 1 1 auto;
  min-width: 0;
  padding: var(--page-pad);
}

.layout__backdrop {
  position: fixed;
  inset: 0;
  z-index: 110;
  background: rgba(8, 13, 24, 0.5);
  backdrop-filter: blur(2px);
}

.backdrop-enter-active,
.backdrop-leave-active {
  transition: opacity var(--duration-base) var(--ease-in-out);
}

.backdrop-enter-from,
.backdrop-leave-to {
  opacity: 0;
}

@media (max-width: 900px) {
  .layout__main,
  .layout.is-collapsed .layout__main {
    margin-left: 0;
  }

  .layout__content {
    padding: 14px;
  }
}
</style>