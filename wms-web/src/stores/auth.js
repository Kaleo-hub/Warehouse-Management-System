import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginApi } from '@/api/auth'
import {
  clearAuthStorage,
  getStoredUser,
  getToken,
  setStoredUser,
  setToken,
} from '@/utils/storage'
import { roleLabel, roleTagType } from '@/utils/format'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken() || '')
  const user = ref(getStoredUser())

  const isLoggedIn = computed(() => Boolean(token.value))
  const displayName = computed(() => user.value?.realName || user.value?.username || '未登录')
  const roleName = computed(() => roleLabel(user.value?.role))
  const roleTag = computed(() => roleTagType(user.value?.role))
  const initial = computed(() => (user.value?.realName || user.value?.username || '?').slice(0, 1))

  /**
   * 登录：调用真实接口 POST /api/v1/auth/login
   * 成功后把 token 和用户信息同时写进 store 与 localStorage。
   */
  async function login(payload) {
    const data = await loginApi(payload)
    token.value = data.token
    user.value = data.userInfo
    setToken(data.token)
    setStoredUser(data.userInfo)
    return data
  }

  function updateUser(nextUser) {
    user.value = nextUser
    setStoredUser(nextUser)
  }

  function logout() {
    token.value = ''
    user.value = null
    clearAuthStorage()
  }

  return {
    token,
    user,
    isLoggedIn,
    displayName,
    roleName,
    roleTag,
    initial,
    login,
    updateUser,
    logout,
  }
})