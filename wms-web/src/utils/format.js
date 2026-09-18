/** 角色编码 → 中文名 + 标签色，后端返回的是 ADMIN / MANAGER / OPERATOR */
export const ROLE_MAP = {
  ADMIN: { label: '系统管理员', type: 'danger', desc: '全部权限，含基础数据维护' },
  MANAGER: { label: '仓库主管', type: 'warning', desc: '单据审核、库存调整、盘点' },
  OPERATOR: { label: '作业员', type: 'primary', desc: '收货、上架、拣货、复核' },
}

export function roleLabel(role) {
  return ROLE_MAP[role]?.label || role || '-'
}

export function roleTagType(role) {
  return ROLE_MAP[role]?.type || 'info'
}

const pad = (n) => String(n).padStart(2, '0')

/**
 * 后端给的是 LocalDateTime，序列化后可能有多种形态：
 *   '2026-09-16 11:03:24'              MySQL 直接查出来的样式
 *   '2026-09-16T11:03:24'              LocalDateTime 默认 JSON
 *   '2026-09-16T11:03:24.123'          带毫秒
 *   '2026-09-16T11:03:24.123456789'    带纳秒（部分浏览器会解析失败）
 *   1789556454657                      时间戳
 *   [2026, 9, 16, 11, 3, 24]           开了 WRITE_DATES_AS_TIMESTAMPS
 * 统一输出 yyyy-MM-dd HH:mm:ss，表格里不会因为时间串过长被撑爆。
 */
export function formatDateTime(value) {
  if (value === null || value === undefined || value === '') return '-'

  if (Array.isArray(value)) {
    const [y, mo = 1, d = 1, h = 0, mi = 0, s = 0] = value
    if (!y) return String(value)
    return `${y}-${pad(mo)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`
  }

  if (typeof value === 'number') return toText(new Date(value))

  let text = String(value).trim()
  text = text.replace(' ', 'T')
  // 只保留 3 位小数，多的纳秒位会让 Date 解析失败
  text = text.replace(/(\.\d{3})\d+/, '$1')

  return toText(new Date(text), value)
}

function toText(date, fallback) {
  if (Number.isNaN(date.getTime())) return fallback === undefined ? '-' : String(fallback)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
/** 生成前端 traceId，方便和后端日志对齐 */
export function createTraceId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID().replace(/-/g, '')
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}