import request from './request'

/**
 * 系统管理接口。
 *
 * 用户管理整块都要求 ADMIN（后端 SysUserController 类上的 @PreAuthorize），
 * 前端拿到 403 / code 20003 时会走统一的错误提示。
 */
export const userApi = {
  /** 用户分页：page / size / keyword（登录名或姓名）/ role / status(1 启用 0 禁用) */
  page: (params) => request.get('/v1/system/users', { params }),

  create: (payload) => request.post('/v1/system/users', payload),

  update: (id, payload) => request.put(`/v1/system/users/${id}`, payload),

  remove: (id) => request.delete(`/v1/system/users/${id}`),

  /** 管理员重置密码：不需要旧密码 */
  resetPassword: (id, newPassword) =>
    request.put(`/v1/system/users/${id}/password`, { newPassword }),

  /** 本人修改密码：必须带旧密码 */
  changeOwnPassword: (payload) => request.put('/v1/auth/password', payload),
}

/**
 * 审计日志查询（仅 ADMIN）。
 *
 * 只读：审计的写入口只有一个 —— 后端的 AuditLogAspect 自动记，
 * 所以这里刻意没有 create / update / remove。
 */
export const auditApi = {
  /** 分页：page / size / keyword（操作人·动作·对象id）/ module / result / traceId / beginTime / endTime */
  page: (params) => request.get('/v1/system/audit-logs', { params }),
}

export default userApi