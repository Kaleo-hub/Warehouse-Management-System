import request from './request'

/**
 * 登录
 * POST /api/v1/auth/login
 * @param {{ username: string, password: string }} payload
 * @returns {Promise<{ token:string, tokenType:string, userInfo:object }>}
 */
export function loginApi(payload) {
  return request.post('/v1/auth/login', payload)
}

/**
 * 当前登录用户信息。
 * 后端接口尚未提供，先留好调用点，等后端补 GET /api/v1/auth/me 后直接生效。
 */
export function getCurrentUserApi() {
  return request.get('/v1/auth/me')
}