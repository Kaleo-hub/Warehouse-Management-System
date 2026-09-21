/**
 * 出库相关枚举。取值严格对应 sql/V1.0.0__init_schema.sql 与 docs/01 第 6.2 节状态机。
 */

/* ---------------- 出库单状态机 ----------------
 * CREATED ──分配──> ALLOCATED ──开始拣货──> PICKING ──拣完──> PICKED ──发货──> SHIPPED ──> CLOSED
 *    │                  │
 *    └──取消──> CANCELLED └──释放/超时──> CREATED
 *
 * 注意 PICKING 不能回退到 ALLOCATED —— 货已经从货架上拿下来了，
 * 再释放分配会造成账实不符。
 */
export const OUTBOUND_STATUS = {
  CREATED: { text: '待分配', type: 'info' },
  ALLOCATED: { text: '已分配', type: 'warning' },
  PICKING: { text: '拣货中', type: 'primary' },
  PICKED: { text: '已拣货', type: 'primary' },
  SHIPPED: { text: '已发货', type: 'success' },
  REVERSED: { text: '已红冲', type: 'danger' },
  CLOSED: { text: '已关闭', type: 'info' },
  CANCELLED: { text: '已取消', type: 'danger' },
}

export const OUTBOUND_STATUS_OPTIONS = Object.entries(OUTBOUND_STATUS).map(([value, meta]) => ({
  label: meta.text,
  value,
}))

/** 只有还没分配的能分配 / 取消 */
export const ALLOCATABLE_STATUS = ['CREATED']
/** 只有已分配、还没开始拣货的能释放 */
export const RELEASABLE_STATUS = ['ALLOCATED']
/** 已分配才能生成拣货任务 */
export const PICKABLE_STATUS = ['ALLOCATED']
/** 全部拣完才能发货 */
export const SHIPPABLE_STATUS = ['PICKED']

/** 只有已发货的单能红冲 */
export const REVERSIBLE_STATUS = ['SHIPPED']

/* ---------------- 分配明细状态 ---------------- */
export const ALLOCATE_STATUS = {
  LOCKED: { text: '已锁定', type: 'warning' },
  PICKED: { text: '已拣货', type: 'primary' },
  RELEASED: { text: '已释放', type: 'info' },
}
