<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Bell,
  Close,
  Expand,
  Fold,
  FullScreen,
  Key,
  Moon,
  SwitchButton,
  Sunny,
} from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useInventoryAlertCount } from '@/composables/useInventoryAlertCount'
import { userApi } from '@/api/system'
import { roleLabel } from '@/utils/format'
import { toastError } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const auth = useAuthStore()

const isFullscreen = ref(Boolean(document.fullscreenElement))

/*
 * 铃铛上的数字必须是真数据。
 * 之前这里写的是 :value="3" —— 一个永远不动的假红点，
 * 和菜单里「不做假页面」的原则自相矛盾，而且会让人以为有 3 条待办。
 */
const { alertTotal, load: loadAlertCount } = useInventoryAlertCount()
onMounted(loadAlertCount)

function goAlerts() {
  router.push('/inventory/alerts')
}

/* ---------------- 修改密码 ---------------- */
const passwordVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref(null)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6 到 32 之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再输入一次新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}

async function submitPassword() {
  if (passwordSubmitting.value) return
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  passwordSubmitting.value = true
  try {
    await userApi.changeOwnPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordVisible.value = false
    /*
     * 这里必须说清楚：改完密码，当前这个 token 在过期前依然有效。
     * 因为用的是无状态 JWT，服务端不存 session，没有「踢下线」的抓手 ——
     * 想立刻失效就得引入 token 黑名单（Redis）或改短有效期，那是第 8 周的事。
     * 不写清楚的话，用户会以为改密码等于注销了其他设备。
     */
    ElMessage.success('密码已修改。当前登录状态会保持到本次 token 过期')
  } catch (error) {
    toastError(error, '修改密码失败')
  } finally {
    passwordSubmitting.value = false
  }
}

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

      <el-badge v-if="alertTotal > 0" :value="alertTotal" class="header__badge">
        <button class="icon-btn" type="button" title="库存预警" @click="goAlerts">
          <el-icon><Bell /></el-icon>
        </button>
      </el-badge>
      <button v-else class="icon-btn" type="button" title="库存预警（当前正常）" @click="goAlerts">
        <el-icon><Bell /></el-icon>
      </button>

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
              账号 {{ auth.user?.username }} · {{ roleLabel(auth.user?.role) }}
            </el-dropdown-item>
            <el-dropdown-item divided :icon="Key" @click="openPasswordDialog">
              修改密码
            </el-dropdown-item>
            <el-dropdown-item :icon="SwitchButton" @click="handleLogout">
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>

  <!-- 修改密码 -->
  <el-dialog v-model="passwordVisible" title="修改密码" width="420px">
    <el-form
      ref="passwordFormRef"
      :model="passwordForm"
      :rules="passwordRules"
      label-position="top"
    >
      <el-form-item label="当前密码" prop="oldPassword">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="passwordForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="passwordVisible = false">取消</el-button>
      <el-button type="primary" :loading="passwordSubmitting" @click="submitPassword">
        确认修改
      </el-button>
    </template>
  </el-dialog>
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