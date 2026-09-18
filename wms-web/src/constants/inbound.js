/**
 * 入库 / 任务 / 库存流水的枚举。
 * 取值严格对应 sql/V1.0.0__init_schema.sql 里字段注释，以及 docs/01 的状态机。
 */

/* ---------------- 入库单状态机 ----------------
 * CREATED ──收货──> RECEIVING ──收完──> RECEIVED ──上架完成──> PUTAWAY_DONE ──> CLOSED
 *    └──取消──> CANCELLED
 * 只能沿箭头走，禁止跳跃和回退（BR-007）
 */
export const INBOUND_STATUS = {
  CREATED: { text: '待收货', type: 'info' },
  RECEIVING: { text: '收货中', type: 'warning' },
  RECEIVED: { text: '已收货', type: 'primary' },
  PUTAWAY_DONE: { text: '已上架', type: 'success' },
  CLOSED: { text: '已关闭', type: 'info' },
  CANCELLED: { text: '已取消', type: 'danger' },
}

export const INBOUND_STATUS_OPTIONS = Object.entries(INBOUND_STATUS).map(([value, meta]) => ({
  label: meta.text,
  value,
}))

/** 只有这两个状态可以继续收货 */
export const RECEIVABLE_STATUS = ['CREATED', 'RECEIVING']

/** 只有 CREATED 可以取消 */
export const CANCELLABLE_STATUS = ['CREATED']

/** 已收货但还没生成上架任务 */
export const PUTAWAY_GENERATABLE_STATUS = ['RECEIVED']

/* ---------------- 作业任务 ----------------
 * CREATED ──领取──> ASSIGNED ──开始──> PROCESSING ──> DONE
 *    ├──取消──> CANCELLED
 *    └──超时──> TIMEOUT
 */
export const TASK_STATUS = {
  CREATED: { text: '待领取', type: 'info' },
  ASSIGNED: { text: '已领取', type: 'warning' },
  PROCESSING: { text: '作业中', type: 'primary' },
  DONE: { text: '已完成', type: 'success' },
  CANCELLED: { text: '已取消', type: 'info' },
  TIMEOUT: { text: '已超时', type: 'danger' },
  EXCEPTION: { text: '异常', type: 'danger' },
}

export const TASK_STATUS_OPTIONS = Object.entries(TASK_STATUS).map(([value, meta]) => ({
  label: meta.text,
  value,
}))

export const TASK_TYPE = {
  PUTAWAY: { text: '上架', type: 'primary' },
  PICK: { text: '拣货', type: 'warning' },
  TRANSFER: { text: '移库', type: 'info' },
  COUNT: { text: '盘点', type: 'info' },
}

export const TASK_TYPE_OPTIONS = Object.entries(TASK_TYPE).map(([value, meta]) => ({
  label: meta.text,
  value,
}))

/* ---------------- 库存流水类型 ----------------
 * 对应 docs/01 第 7 节「库存变动规则」表格
 */
export const TX_TYPE = {
  PUTAWAY_IN: { text: '上架入库', type: 'success' },
  ALLOCATE_LOCK: { text: '出库分配', type: 'warning' },
  ALLOCATE_RELEASE: { text: '释放分配', type: 'info' },
  OUTBOUND_OUT: { text: '出库发货', type: 'danger' },
  FREEZE: { text: '冻结', type: 'warning' },
  UNFREEZE: { text: '解冻', type: 'info' },
  TRANSFER_OUT: { text: '移库转出', type: 'info' },
  TRANSFER_IN: { text: '移库转入', type: 'success' },
  STOCKTAKING_GAIN: { text: '盘盈', type: 'success' },
  STOCKTAKING_LOSS: { text: '盘亏', type: 'danger' },
}

export const TX_TYPE_OPTIONS = Object.entries(TX_TYPE).map(([value, meta]) => ({
  label: meta.text,
  value,
}))