import request, { idempotent } from './request'

/**
 * 库存接口。
 *
 * 查询部分是只读的；写入部分没有「直接改库存」的方法，全是业务动作：
 * 冻结 / 解冻 / 移库 / 盘点 / 对账。
 * 每个动作背后都有明确业务语义，服务端会写对应的库存流水 ——
 * 前端不可能绕过这些动作去改数，这是 BR-008「流水只增不改」的前提。
 */
export const inventoryApi = {
  /** 库存分页查询：page / size / warehouseId / skuId / locationId / batchNo / keyword */
  page: (params) => request.get('/v1/inventory/inventories', { params }),

  /**
   * 导出库存 Excel。筛选条件和列表页完全一致，单次最多 5000 行。
   * responseType: 'blob' —— 拿到二进制而不是 JSON，交给 triggerDownload 落盘。
   */
  exportInventory: (params) =>
    request.get('/v1/inventory/inventories/export', { params, responseType: 'blob' }),

  /** 导出库存流水 Excel */
  exportTransactions: (params) =>
    request.get('/v1/inventory/transactions/export', { params, responseType: 'blob' }),

  /** 库存详情（移库 / 盘点页面深链进来时拿初值） */
  detail: (id) => request.get(`/v1/inventory/inventories/${id}`),

  /** 库存流水查询：page / size / skuId / locationId / txType / sourceNo / beginTime / endTime */
  transactions: (params) => request.get('/v1/inventory/transactions', { params }),

  /** 条库存流水明细（对账用） */
  transactionDetail: (id) => request.get(`/v1/inventory/transactions/${id}`),

  /** 冻结：frozen_qty +qty。货还在库，可用数量变少，不可分配也不可移库 */
  freeze: (id, qty, idempotentKey) =>
    request.post(`/v1/inventory/inventories/${id}/freeze`, { qty }, idempotent(idempotentKey)),

  /** 解冻：frozen_qty -qty，可用数量恢复 */
  unfreeze: (id, qty, idempotentKey) =>
    request.post(`/v1/inventory/inventories/${id}/unfreeze`, { qty }, idempotent(idempotentKey)),

  /**
   * 移库：把某库位某批次挪到同仓库的另一个库位。
   * 一个事务里转出 -n、转入 +n，服务端失败会整体回滚。
   */
  transfer: (payload, idempotentKey) =>
    request.post('/v1/inventory/transfers', payload, idempotent(idempotentKey)),

  /**
   * 盘点：传实盘数量（不是差异），后端自己算差异并写盘盈 / 盘亏流水。
   * 返回 { systemQty, actualQty, diffQty, txType, changed }。
   */
  stocktake: (payload, idempotentKey) =>
    request.post('/v1/inventory/stocktakings', payload, idempotent(idempotentKey)),

  /**
   * 对账：按流水重算库存余额（BR-009）。
   * autoFix=false 只检查不改数；true 会把余额改成按流水重算出来的值。
   */
  reconcile: (autoFix = false, idempotentKey) =>
    request.post('/v1/inventory/reconcile', null, {
      params: { autoFix },
      ...(idempotent(idempotentKey) || {}),
    }),

  /**
   * 库存总览（工作台看板）。
   * 一次返回：在库/锁定/冻结/可用、SKU 数、库位占用率、待处理数、按仓库分布。
   */
  summary: (params) => request.get('/v1/inventory/summary', { params }),

  /**
   * 分组合并汇总。
   * @param {{dimension?: 'WAREHOUSE'|'ZONE'|'SKU', warehouseId?: number}} params
   */
  groupedSummary: (params) => request.get('/v1/inventory/summary/grouped', { params }),

  /**
   * 库存预警：临期 / 冻结 / 库位超容。
   * @param {{expireDays?: number, warehouseId?: number}} params
   */
  alerts: (params) => request.get('/v1/inventory/alerts', { params }),
}

export default inventoryApi

/** 盘点单：建单时快照一个库位的库存行，录入后批量过账。 */
export const stocktakingOrderApi = {
  page: (params) => request.get('/v1/inventory/stocktaking-orders', { params }),
  detail: (id) => request.get(`/v1/inventory/stocktaking-orders/${id}`),
  create: (payload, idempotentKey) =>
    request.post('/v1/inventory/stocktaking-orders', payload, idempotent(idempotentKey)),
  saveCounts: (id, payload, idempotentKey) =>
    request.put(`/v1/inventory/stocktaking-orders/${id}/counts`, payload, idempotent(idempotentKey)),
  post: (id, idempotentKey) =>
    request.post(`/v1/inventory/stocktaking-orders/${id}/post`, null, idempotent(idempotentKey)),
  cancel: (id) => request.post(`/v1/inventory/stocktaking-orders/${id}/cancel`),
}

/** 移库单：一个源库位到同仓目标库位，可包含多条 SKU/批次。 */
export const transferOrderApi = {
  page: (params) => request.get('/v1/inventory/transfer-orders', { params }),
  detail: (id) => request.get(`/v1/inventory/transfer-orders/${id}`),
  create: (payload, idempotentKey) =>
    request.post('/v1/inventory/transfer-orders', payload, idempotent(idempotentKey)),
  complete: (id, idempotentKey) =>
    request.post(`/v1/inventory/transfer-orders/${id}/complete`, null, idempotent(idempotentKey)),
  cancel: (id) => request.post(`/v1/inventory/transfer-orders/${id}/cancel`),
}