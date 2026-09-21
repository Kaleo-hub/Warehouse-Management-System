import axios from 'axios'
import { getToken } from '@/utils/storage'
import { emitSessionExpired } from '@/utils/session'
import { createTraceId } from '@/utils/format'
import { isMockEnabled, mockAdapter } from '@/mock'

/** 业务异常：带上后端错误码和 traceId，页面可以精确定位问题 */
export class ApiError extends Error {
  constructor(message, code, traceId) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}

/** 这两个码代表身份失效，需要踢回登录页 */
export const SESSION_EXPIRED_CODES = [20001, 20002]

let lastTraceId = ''

/** 最近一次请求的 traceId，登录页会用它在报错时给出可排查的编号 */
export function getLastTraceId() {
  return lastTraceId
}

/**
 * 构造带幂等键的请求配置。
 *
 * 幂等键走请求头 X-Idempotent-Key，和后端 common/idempotent 的约定一致。
 * key 为空就退化成普通请求（后端还有「业务键 + 请求体哈希」兜底，不至于完全没保护）。
 */
export function idempotent(key) {
  return key ? { headers: { 'X-Idempotent-Key': key } } : undefined
}

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

/* ------------------------------------------------------------------ 请求拦截 */
service.interceptors.request.use((config) => {
  if (isMockEnabled()) config.adapter = mockAdapter

  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`

  // 客户端也生成一个 traceId，后端 TraceIdFilter 会优先使用它，方便前后端日志对齐
  const traceId = createTraceId()
  config.headers['X-Trace-Id'] = traceId
  lastTraceId = traceId

  return config
})

/* ------------------------------------------------------------------ 响应拦截 */
function unwrap(response) {
  const body = response.data

  // 非 Result 结构（文件流、第三方接口）原样返回
  if (body === null || typeof body !== 'object' || !('code' in body)) return body

  if (body.traceId) lastTraceId = body.traceId
  if (body.code === 0) return body.data

  throw new ApiError(body.message || '请求失败', body.code, body.traceId)
}

function toApiError(error) {
  const body = error?.response?.data
  if (body && typeof body === 'object' && typeof body.code === 'number') {
    if (body.traceId) lastTraceId = body.traceId
    return new ApiError(body.message || '请求失败', body.code, body.traceId)
  }

  const status = error?.response?.status
  if (status === 401) return new ApiError('登录已过期，请重新登录', 20002, lastTraceId)
  if (status === 403) return new ApiError('无权限访问该资源', 20003, lastTraceId)
  if (status === 404) return new ApiError('请求的接口不存在', 10004, lastTraceId)
  if (status >= 500) return new ApiError('服务暂时不可用，请稍后重试', 10001, lastTraceId)
  if (error?.code === 'ECONNABORTED') return new ApiError('请求超时，请检查网络后重试', -2, lastTraceId)
  if (!error?.response) return new ApiError('无法连接后端服务，请确认服务已启动', -1, lastTraceId)
  return new ApiError(error.message || '请求失败', -3, lastTraceId)
}

/**
 * 会话失效的兜底广播。
 *
 * 请求层只做「通知」，具体怎么清登录态、怎么跳登录页由 main.js 里的订阅者决定 ——
 * 这样 request.js 不用 import store / router，避免循环依赖。
 * 1 秒内重复触发只广播一次：批量并发请求同时 401 时不至于弹一堆提示。
 */
let lastEmitAt = 0

function notifySessionExpired() {
  const now = Date.now()
  if (now - lastEmitAt < 1000) return
  lastEmitAt = now
  emitSessionExpired()
}
service.interceptors.response.use(
  (response) => {
    try {
      return unwrap(response)
    } catch (error) {
      if (SESSION_EXPIRED_CODES.includes(error.code)) {
        error.silent = true   // 提示已经由会话失效处理器弹过了，页面层别再弹一次
        notifySessionExpired()
      }
      return Promise.reject(error)
    }
  },
  (error) => {
    const apiError = toApiError(error)
    if (SESSION_EXPIRED_CODES.includes(apiError.code)) {
      apiError.silent = true
      notifySessionExpired()
    }
    return Promise.reject(apiError)
  },
)

export default service
