import request, { idempotent } from './request'

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
  /**
   * 创建入库单。
   * @param idempotentKey 幂等键：新建页打开时生成一次，提交成功后才换新的。
   *        双击提交、失败重试必须用同一个 —— 否则后端认不出这是同一次动作。
   */
  create: (payload, idempotentKey) => request.post(base, payload, idempotent(idempotentKey)),

  /** 取消：只有 CREATED 允许 */
  cancel: (id) => request.post(`${base}/${id}/cancel`),

  /** 收货登记：整单一次提交，items 里带本次实收数量和批次 */
  /** 收货登记。分批收货很常见，所以幂等键由调用方按「这一次收货」生成 */
  receive: (id, payload, idempotentKey) =>
    request.post(`${base}/${id}/receive`, payload, idempotent(idempotentKey)),

  /** 按已收货明细生成上架任务 */
  generatePutawayTasks: (id) => request.post(`${base}/${id}/putaway-tasks`),
}

export default inboundApi