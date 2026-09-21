/**
 * 本地存储的唯一出口。
 * 组件 / 请求层都不允许直接读写 localStorage，方便以后换成 sessionStorage 或加密存储。
 */
const TOKEN_KEY = 'wms:token'
const USER_KEY = 'wms:user'
const REMEMBER_KEY = 'wms:remember'
const THEME_KEY = 'wms:theme'
const MENU_GROUPS_KEY = 'wms:menuGroups'
const CURRENT_WAREHOUSE_KEY = 'wms:currentWarehouseId'
const PREFERENCES_KEY = 'wms:preferences'

function read(key, fallback = null) {
  try {
    const raw = localStorage.getItem(key)
    return raw === null ? fallback : raw
  } catch {
    return fallback
  }
}

function write(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    /* 隐私模式下 localStorage 可能不可用，静默降级 */
  }
}

function remove(key) {
  try {
    localStorage.removeItem(key)
  } catch {
    /* ignore */
  }
}

/* ---------- token ---------- */
export function getToken() {
  return read(TOKEN_KEY, '')
}

export function setToken(token) {
  if (token) write(TOKEN_KEY, token)
  else remove(TOKEN_KEY)
}

/* ---------- 用户信息 ---------- */
export function getStoredUser() {
  const raw = read(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setStoredUser(user) {
  if (user) write(USER_KEY, JSON.stringify(user))
  else remove(USER_KEY)
}

/* ---------- 记住用户名 ---------- */
export function getRememberedUsername() {
  return read(REMEMBER_KEY, '')
}

export function setRememberedUsername(username) {
  if (username) write(REMEMBER_KEY, username)
  else remove(REMEMBER_KEY)
}

/* ---------- 主题 ---------- */
export function getStoredTheme() {
  return read(THEME_KEY, '')
}

export function setStoredTheme(theme) {
  write(THEME_KEY, theme)
}

/* ---------- 侧边栏展开的模块 ---------- */
/**
 * 存的是分组【名字】而不是下标。
 * 用下标的话，菜单一旦按角色过滤（不同角色看到的分组数不一样），
 * 下标就会错位 —— 管理员存的「3」到了作业员那儿可能指到另一个模块上。
 */
export function getStoredMenuGroups() {
  const raw = read(MENU_GROUPS_KEY)
  if (!raw) return []
  try {
    const list = JSON.parse(raw)
    return Array.isArray(list) ? list.filter((item) => typeof item === 'string') : []
  } catch {
    return []
  }
}

export function setStoredMenuGroups(groups) {
  write(MENU_GROUPS_KEY, JSON.stringify(groups))
}

/* ---------- 当前仓库 ---------- */
export function getStoredCurrentWarehouseId() {
  const value = Number(read(CURRENT_WAREHOUSE_KEY, ''))
  return Number.isInteger(value) && value > 0 ? value : null
}

export function setStoredCurrentWarehouseId(id) {
  if (id) write(CURRENT_WAREHOUSE_KEY, String(id))
  else remove(CURRENT_WAREHOUSE_KEY)
}

/* ---------- 界面偏好 ---------- */
export function getStoredPreferences() {
  const raw = read(PREFERENCES_KEY)
  if (!raw) return {}
  try {
    const value = JSON.parse(raw)
    return value && typeof value === 'object' ? value : {}
  } catch {
    return {}
  }
}

export function setStoredPreferences(preferences) {
  write(PREFERENCES_KEY, JSON.stringify(preferences || {}))
}
/* ---------- 退出登录时统一清理 ---------- */
export function clearAuthStorage() {
  remove(TOKEN_KEY)
  remove(USER_KEY)
}