/** 与 sql/V1.0.0__init_schema.sql 里注释的枚举值严格对应，不要在前端凭空造状态 */

export const STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
]

export const STATUS_TAG = {
  ENABLED: { text: '启用', type: 'success' },
  DISABLED: { text: '停用', type: 'info' },
}

/* ---------- 用户角色（对应后端 system/enums/UserRole） ---------- */
export const ROLE_OPTIONS = [
  { label: '系统管理员', value: 'ADMIN' },
  { label: '仓库主管', value: 'MANAGER' },
  { label: '作业员', value: 'OPERATOR' },
]

export const ROLE_TAG = {
  ADMIN: { text: '系统管理员', type: 'danger' },
  MANAGER: { text: '仓库主管', type: 'warning' },
  OPERATOR: { text: '作业员', type: 'primary' },
}

/* ---------- 用户状态：sys_user.status 是 tinyint（1/0），不是 ENABLED/DISABLED ---------- */
export const USER_STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

export const USER_STATUS_TAG = {
  1: { text: '启用', type: 'success' },
  0: { text: '停用', type: 'info' },
}

export const ZONE_TYPE_OPTIONS = [
  { label: '存储区', value: 'STORAGE' },
  { label: '拣货区', value: 'PICKING' },
  { label: '收货区', value: 'RECEIVING' },
  { label: '发货区', value: 'SHIPPING' },
  { label: '分拣区', value: 'SORTING' },
]

export const ZONE_TYPE_TAG = {
  STORAGE: { text: '存储区', type: 'primary' },
  PICKING: { text: '拣货区', type: 'warning' },
  RECEIVING: { text: '收货区', type: 'success' },
  SHIPPING: { text: '发货区', type: 'info' },
  SORTING: { text: '分拣区', type: 'danger' },
}

export const LOCATION_TYPE_OPTIONS = [
  { label: '货架位', value: 'RACK' },
  { label: '地堆位', value: 'FLOOR' },
  { label: '临时位', value: 'TEMP' },
]

export const LOCATION_TYPE_TAG = {
  RACK: { text: '货架位', type: 'primary' },
  FLOOR: { text: '地堆位', type: 'warning' },
  TEMP: { text: '临时位', type: 'info' },
}