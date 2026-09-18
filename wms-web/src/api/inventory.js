import request from './request'

/**
 * 库存与流水。都是只读查询，写操作一律由业务动作（收货、上架、出库…）间接完成，
 * 前端不允许直接改库存 —— 这是 BR-008「流水只增不改」的前提。
 */
export const inventoryApi = {
  /** 库存分页查询：page / size / warehouseId / skuId / locationId / batchNo / keyword */
  page: (params) => request.get('/v1/inventory/inventories', { params }),

  /** 库存流水查询：page / size / skuId / locationId / txType / sourceNo / beginTime / endTime */
  transactions: (params) => request.get('/v1/inventory/transactions', { params }),

  /** 条库存流水明细（对账用） */
  transactionDetail: (id) => request.get(`/v1/inventory/transactions/${id}`),
}

export default inventoryApi