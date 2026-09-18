/** 与 sql/V1.0.0__init_schema.sql 里注释的枚举值严格对应，不要在前端凭空造状态 */

export const STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
]

export const STATUS_TAG = {
  ENABLED: { text: '启用', type: 'success' },
  DISABLED: { text: '停用', type: 'info' },
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