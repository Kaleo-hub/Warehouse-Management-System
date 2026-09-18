import request from './request'

/** 作业任务（当前只用到上架，拣货任务复用同一组接口） */
const base = '/v1/task/stock-tasks'

export const stockTaskApi = {
  /** 分页查询：page / size / taskType / status / warehouseId / assigneeId / sourceNo */
  page: (params) => request.get(base, { params }),

  detail: (id) => request.get(`${base}/${id}`),

  /** 领取任务：CREATED → ASSIGNED，并发下用 CAS，被抢走返回 70003 */
  assign: (id) => request.post(`${base}/${id}/assign`),

  /** 开始作业：ASSIGNED → PROCESSING */
  start: (id) => request.post(`${base}/${id}/start`),

  /** 完成任务：PROCESSING → DONE，body 带实际数量，服务端在这里写库存 */
  complete: (id, payload) => request.post(`${base}/${id}/complete`, payload),
}

export default stockTaskApi