import request, { idempotent } from './request'

/**
 * 出库单。动作型接口遵循 docs/02 第 4.1 节的 /{id}/{action} 命名。
 *
 * 完整链路：
 *   建单 → 分配库存(FIFO) → 生成拣货任务 → 领取/拣货 → 发货
 *   CREATED → ALLOCATED   → PICKING        → PICKED   → SHIPPED
 */
const base = '/v1/outbound/outbound-orders'

export const outboundApi = {
  /** 分页查询：page / size / keyword / warehouseId / status / beginTime / endTime */
  page: (params) => request.get(base, { params }),

  /** 详情，含 items 出库明细和 allocates 分配明细 */
  detail: (id) => request.get(`${base}/${id}`),

  /** 创建：单头 + items 明细数组 */
  /** 创建出库单。理由同入库单创建 */
  create: (payload, idempotentKey) => request.post(base, payload, idempotent(idempotentKey)),

  /** 分配库存（FIFO）。任一明细可用量不够就整单回滚 */
  allocate: (id) => request.post(`${base}/${id}/allocate`),

  /** 释放分配，把 locked_qty 还回去，单据回到 CREATED */
  release: (id) => request.post(`${base}/${id}/release`),

  /** 按分配明细生成拣货任务（实现在 task 模块） */
  generatePickTasks: (id) => request.post(`${base}/${id}/pick-tasks`),

  /** 发货：qty 和 locked_qty 一起减（账实核销） */
  ship: (id) => request.post(`${base}/${id}/ship`),

  /** 红冲：已发货单据开反向单并恢复库存，返回红冲单 id */
  reverse: (id) => request.post(`${base}/${id}/reverse`),

  /** 取消：只有 CREATED 允许 */
  cancel: (id) => request.post(`${base}/${id}/cancel`),
}

export default outboundApi