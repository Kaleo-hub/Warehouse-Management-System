import request from './request'

/**
 * 入库单。动作型接口遵循 docs/02 第 4.1 节的 /{id}/{action} 命名。
 * 后端还没实现，契约见 docs/入库模块-后端接口约定.md
 */
const base = '/v1/inbound/inbound-orders'

export const inboundApi = {
  /** 分页查询：page / size / keyword / warehouseId / status / beginTime / endTime */
  page: (params) => request.get(base, { params }),

  /** 详情，必须带 items 明细 */
  detail: (id) => request.get(`${base}/${id}`),

  /** 创建：单头 + items 明细数组 */
  create: (payload) => request.post(base, payload),

  /** 取消：只有 CREATED 允许 */
  cancel: (id) => request.post(`${base}/${id}/cancel`),

  /** 收货登记：整单一次提交，items 里带本次实收数量和批次 */
  receive: (id, payload) => request.post(`${base}/${id}/receive`, payload),

  /** 按已收货明细生成上架任务 */
  generatePutawayTasks: (id) => request.post(`${base}/${id}/putaway-tasks`),
}

export default inboundApi