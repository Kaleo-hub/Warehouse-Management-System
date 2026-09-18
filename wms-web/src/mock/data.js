/**
 * 纯前端演示用的内存数据（npm run dev:mock）。
 * 字段与 sql/V1.0.0__init_schema.sql 保持一致，后端接好后这个文件不会进入生产包。
 */
let autoId = 100

export function nextId() {
  return ++autoId
}

export const users = [
  {
    id: 1,
    username: 'admin',
    password: '123456',
    realName: '系统管理员',
    role: 'ADMIN',
    status: 1,
  },
  {
    id: 2,
    username: 'manager',
    password: '123456',
    realName: '王主管',
    role: 'MANAGER',
    status: 1,
  },
  {
    id: 3,
    username: 'operator',
    password: '123456',
    realName: '李作业',
    role: 'OPERATOR',
    status: 1,
  },
]

const now = () => new Date().toISOString().slice(0, 19).replace('T', ' ')

export const warehouses = [
  {
    id: 1,
    code: 'WH001',
    name: '上海一号仓',
    address: '上海市浦东新区示例路 100 号',
    status: 'ENABLED',
    remark: '示例数据',
    createTime: now(),
    updateTime: now(),
  },
  {
    id: 2,
    code: 'WH002',
    name: '北京中心仓',
    address: '北京市大兴区物流园 8 号',
    status: 'ENABLED',
    remark: '华北主仓',
    createTime: now(),
    updateTime: now(),
  },
  {
    id: 3,
    code: 'WH003',
    name: '广州南沙仓',
    address: '广东省广州市南沙区保税港区',
    status: 'DISABLED',
    remark: '筹备中',
    createTime: now(),
    updateTime: now(),
  },
]

export const zones = [
  { id: 1, warehouseId: 1, code: 'A', name: 'A区存储区', zoneType: 'STORAGE', sortNo: 1, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 2, warehouseId: 1, code: 'B', name: 'B区拣货区', zoneType: 'PICKING', sortNo: 2, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 3, warehouseId: 1, code: 'R', name: '收货暂存区', zoneType: 'RECEIVING', sortNo: 3, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 4, warehouseId: 2, code: 'A', name: 'A区存储区', zoneType: 'STORAGE', sortNo: 1, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 5, warehouseId: 2, code: 'S', name: '发货暂存区', zoneType: 'SHIPPING', sortNo: 2, status: 'ENABLED', createTime: now(), updateTime: now() },
]

export const locations = [
  { id: 1, warehouseId: 1, zoneId: 1, code: 'A-01-01-01', name: 'A区01排01列01层', locationType: 'RACK', capacity: 1000, usedCapacity: 320, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 2, warehouseId: 1, zoneId: 1, code: 'A-01-01-02', name: 'A区01排01列02层', locationType: 'RACK', capacity: 1000, usedCapacity: 0, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 3, warehouseId: 1, zoneId: 1, code: 'A-01-02-01', name: 'A区01排02列01层', locationType: 'RACK', capacity: 1000, usedCapacity: 640, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 4, warehouseId: 1, zoneId: 3, code: 'R-00-00-01', name: '收货暂存位01', locationType: 'TEMP', capacity: 0, usedCapacity: 0, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 5, warehouseId: 2, zoneId: 4, code: 'A-01-01-01', name: 'A区01排01列01层', locationType: 'RACK', capacity: 800, usedCapacity: 100, status: 'ENABLED', createTime: now(), updateTime: now() },
]

export const products = [
  { id: 1, skuCode: 'SKU0001', name: '洗发水 500ml', spec: '500ml/瓶', unit: '瓶', barcode: '6900000000011', weightG: 520, shelfLifeDays: 730, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 2, skuCode: 'SKU0002', name: '沐浴露 750ml', spec: '750ml/瓶', unit: '瓶', barcode: '6900000000028', weightG: 800, shelfLifeDays: 730, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 3, skuCode: 'SKU0003', name: '纸巾 200抽', spec: '200抽/包', unit: '包', barcode: '6900000000035', weightG: 300, shelfLifeDays: 1095, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 4, skuCode: 'SKU0004', name: '洗衣液 3kg', spec: '3kg/桶', unit: '桶', barcode: '6900000000042', weightG: 3200, shelfLifeDays: 1095, status: 'ENABLED', createTime: now(), updateTime: now() },
  { id: 5, skuCode: 'SKU0005', name: '牙膏 120g', spec: '120g/支', unit: '支', barcode: '6900000000059', weightG: 140, shelfLifeDays: 1095, status: 'DISABLED', createTime: now(), updateTime: now() },
]