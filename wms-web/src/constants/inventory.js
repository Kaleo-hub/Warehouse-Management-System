/** 盘点单状态。 */
export const STOCKTAKING_ORDER_STATUS = {
  COUNTING: { text: '盘点中', type: 'warning' },
  COMPLETED: { text: '已完成', type: 'success' },
  CANCELLED: { text: '已取消', type: 'info' },
}

export const STOCKTAKING_ORDER_STATUS_OPTIONS = Object.entries(STOCKTAKING_ORDER_STATUS).map(
  ([value, meta]) => ({ label: meta.text, value }),
)

/** 移库单状态。 */
export const TRANSFER_ORDER_STATUS = {
  CREATED: { text: '待执行', type: 'warning' },
  COMPLETED: { text: '已完成', type: 'success' },
  CANCELLED: { text: '已取消', type: 'info' },
}

export const TRANSFER_ORDER_STATUS_OPTIONS = Object.entries(TRANSFER_ORDER_STATUS).map(
  ([value, meta]) => ({ label: meta.text, value }),
)