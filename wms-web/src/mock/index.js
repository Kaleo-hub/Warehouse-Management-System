/**
 * Mock 层：只在 `npm run dev:mock` 时启用（VITE_USE_MOCK=true）。
 *
 * 它是一个 axios adapter，所以业务代码、拦截器、错误处理全部走真实链路，
 * 接好后端只要不设置 VITE_USE_MOCK，这一层自动失效，零改动。
 */
import { users, warehouses, zones, locations, products, nextId } from './data'
import {
  inboundOrders,
  inventories,
  stockTasks,
  transactions,
  nextOrderNo,
  nextTaskNo,
  nextTxNo,
  timestamp,
} from './inboundData'

export function isMockEnabled() {
  return String(import.meta.env.VITE_USE_MOCK) === 'true'
}

const now = () => new Date().toISOString().slice(0, 19).replace('T', ' ')

const RESOURCES = {
  warehouses: {
    table: warehouses,
    keywordFields: ['code', 'name', 'address'],
    uniqueField: 'code',
    uniqueMessage: '仓库编码已存在',
  },
  zones: {
    table: zones,
    keywordFields: ['code', 'name'],
    uniqueField: 'code',
    uniqueMessage: '同一仓库下库区编码已存在',
  },
  locations: {
    table: locations,
    keywordFields: ['code', 'name'],
    uniqueField: 'code',
    uniqueMessage: '同一仓库下库位编码已存在',
  },
  products: {
    table: products,
    keywordFields: ['skuCode', 'name', 'barcode'],
    uniqueField: 'skuCode',
    uniqueMessage: 'SKU 编码已存在',
  },
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function traceIdOf(config) {
  return config?.headers?.['X-Trace-Id'] || ''
}

function ok(data, config) {
  return { code: 0, message: 'success', data, traceId: traceIdOf(config), timestamp: Date.now() }
}

function fail(code, message, config) {
  return { code, message, data: null, traceId: traceIdOf(config), timestamp: Date.now() }
}

/* ------------------------------ 业务处理 ------------------------------ */

function handleLogin(payload, config) {
  const { username, password } = payload
  const user = users.find((it) => it.username === username)
  if (!user || user.password !== password) {
    return { httpStatus: 200, body: fail(20004, '用户名或密码错误', config) }
  }
  if (user.status !== 1) {
    return { httpStatus: 200, body: fail(20005, '账号已被禁用', config) }
  }
  return {
    httpStatus: 200,
    body: ok(
      {
        token: `mock-token-${user.id}-${Date.now()}`,
        tokenType: 'Bearer',
        userInfo: {
          id: user.id,
          username: user.username,
          realName: user.realName,
          role: user.role,
        },
      },
      config,
    ),
  }
}
function pageOf(resource, config, params) {
  const keyword = (params.keyword || '').trim().toLowerCase()
  const page = Number(params.page || 1)
  const size = Number(params.size || 10)

  let rows = resource.table.slice()

  if (keyword) {
    rows = rows.filter((row) =>
      resource.keywordFields.some((field) =>
        String(row[field] ?? '')
          .toLowerCase()
          .includes(keyword),
      ),
    )
  }
  if (params.status) rows = rows.filter((row) => row.status === params.status)
  if (params.warehouseId) rows = rows.filter((row) => row.warehouseId === Number(params.warehouseId))
  if (params.zoneId) rows = rows.filter((row) => row.zoneId === Number(params.zoneId))

  rows.sort((a, b) => b.id - a.id)

  const total = rows.length
  const start = (page - 1) * size
  return {
    httpStatus: 200,
    body: ok({ records: rows.slice(start, start + size), total, page, size }, config),
  }
}

function createOf(resource, config, payload) {
  const key = resource.uniqueField
  if (key && resource.table.some((row) => row[key] === payload[key])) {
    return { httpStatus: 200, body: fail(10005, resource.uniqueMessage, config) }
  }
  const row = { ...payload, id: nextId(), createTime: now(), updateTime: now() }
  resource.table.push(row)
  return { httpStatus: 200, body: ok(row, config) }
}

function updateOf(resource, config, id, payload) {
  const index = resource.table.findIndex((row) => row.id === Number(id))
  if (index < 0) return { httpStatus: 200, body: fail(10004, '资源不存在', config) }

  const key = resource.uniqueField
  if (key && resource.table.some((row) => row.id !== Number(id) && row[key] === payload[key])) {
    return { httpStatus: 200, body: fail(10005, resource.uniqueMessage, config) }
  }

  resource.table[index] = { ...resource.table[index], ...payload, id: Number(id), updateTime: now() }
  return { httpStatus: 200, body: ok(resource.table[index], config) }
}

function removeOf(resource, config, id) {
  const index = resource.table.findIndex((row) => row.id === Number(id))
  if (index < 0) return { httpStatus: 200, body: fail(10004, '资源不存在', config) }
  resource.table.splice(index, 1)
  return { httpStatus: 200, body: ok(null, config) }
}

/* ============================ 入库 / 库存 / 任务 ============================ */

/**
 * 深拷贝再返回。
 * 真实 HTTP 接口经过 JSON 序列化，每次都会给出全新对象；
 * 如果直接把内存里的对象引用返回，Vue 拿到的前后两次是同一个引用，
 * 就不会触发重新渲染 —— 表现成「接口成功了但页面没变」。
 */
const clone = (value) => JSON.parse(JSON.stringify(value))

function paginate(rows, params, config, transform) {
  const page = Number(params.page || 1)
  const size = Number(params.size || 10)
  const sorted = rows.slice().sort((a, b) => b.id - a.id)
  const total = sorted.length
  const start = (page - 1) * size
  const slice = sorted.slice(start, start + size)
  return {
    httpStatus: 200,
    body: ok(
      { records: clone(transform ? slice.map(transform) : slice), total, page, size },
      config,
    ),
  }
}

function inRange(time, begin, end) {
  if (!time) return true
  const day = String(time).slice(0, 10)
  if (begin && day < begin) return false
  if (end && day > end) return false
  return true
}

function warehouseNameOf(id) {
  return warehouses.find((it) => it.id === Number(id))?.name || `#${id}`
}

function locationCodeOf(id) {
  return locations.find((it) => it.id === Number(id))?.code || null
}

/* ---------------- 入库单 ---------------- */

function inboundPage(config, params) {
  const keyword = (params.keyword || '').trim().toLowerCase()
  let rows = inboundOrders.slice()

  if (keyword) {
    rows = rows.filter(
      (row) =>
        String(row.orderNo).toLowerCase().includes(keyword) ||
        String(row.supplierName || '')
          .toLowerCase()
          .includes(keyword),
    )
  }
  if (params.warehouseId) rows = rows.filter((row) => row.warehouseId === Number(params.warehouseId))
  if (params.status) rows = rows.filter((row) => row.status === params.status)
  if (params.beginTime || params.endTime) {
    rows = rows.filter((row) => inRange(row.createTime, params.beginTime, params.endTime))
  }

  // 列表不返回 items，和真实后端的分页 VO 保持一致
  return paginate(rows, params, config, (row) => {
    const { items, ...rest } = row
    return rest
  })
}

function inboundGet(config, id) {
  const order = inboundOrders.find((it) => it.id === Number(id))
  if (!order) return { httpStatus: 200, body: fail(10004, '入库单不存在', config) }
  return { httpStatus: 200, body: ok(clone(order), config) }
}

function inboundCreate(config, payload) {
  const items = payload.items || []
  if (!payload.warehouseId) return { httpStatus: 200, body: fail(10002, '入库仓库不能为空', config) }
  if (!items.length) return { httpStatus: 200, body: fail(10002, '入库明细不能为空', config) }

  const order = {
    id: nextId(),
    orderNo: nextOrderNo(),
    warehouseId: Number(payload.warehouseId),
    warehouseName: warehouseNameOf(payload.warehouseId),
    supplierName: payload.supplierName || '',
    expectArriveTime: payload.expectArriveTime || null,
    status: 'CREATED',
    totalExpectQty: items.reduce((sum, it) => sum + (Number(it.expectQty) || 0), 0),
    totalReceivedQty: 0,
    remark: payload.remark || '',
    createTime: timestamp(),
    updateTime: timestamp(),
    items: items.map((it) => {
      const sku = products.find((p) => p.id === Number(it.skuId))
      return {
        id: nextId(),
        skuId: Number(it.skuId),
        skuCode: sku?.skuCode || '',
        skuName: sku?.name || '',
        expectQty: Number(it.expectQty) || 0,
        receivedQty: 0,
        putawayQty: 0,
        batchNo: it.batchNo || '',
        productionDate: it.productionDate || null,
        expiryDate: it.expiryDate || null,
        remark: it.remark || '',
      }
    }),
  }

  inboundOrders.push(order)
  return { httpStatus: 200, body: ok(order.id, config) }
}

function inboundReceive(config, order, payload) {
  const items = payload.items || []
  if (!items.length) return { httpStatus: 200, body: fail(10002, '收货明细不能为空', config) }

  // 先整体校验再落库 —— 对应后端「整单原子」，任一行不合法就整单失败
  const staged = []
  for (const input of items) {
    const item = order.items.find((it) => it.id === Number(input.itemId))
    if (!item) return { httpStatus: 200, body: fail(10004, '明细不存在', config) }

    const qty = Number(input.receivedQty) || 0
    if (qty <= 0) return { httpStatus: 200, body: fail(10002, '实收数量必须大于 0', config) }
    if (item.receivedQty + qty > item.expectQty) {
      return {
        httpStatus: 200,
        body: fail(40004, `${item.skuCode} 实收数量超过预计数量 ${item.expectQty}`, config),
      }
    }
    staged.push({ item, input, qty })
  }

  staged.forEach(({ item, input, qty }) => {
    item.receivedQty += qty
    if (input.batchNo !== undefined) item.batchNo = input.batchNo || ''
    if (input.productionDate !== undefined) item.productionDate = input.productionDate || null
    if (input.expiryDate !== undefined) item.expiryDate = input.expiryDate || null
  })

  order.totalReceivedQty = order.items.reduce((sum, it) => sum + it.receivedQty, 0)
  const allReceived = order.items.every((it) => it.receivedQty >= it.expectQty)
  order.status = allReceived ? 'RECEIVED' : 'RECEIVING'
  order.updateTime = timestamp()

  return { httpStatus: 200, body: ok(null, config) }
}

function inboundCancel(config, order) {
  if (order.status !== 'CREATED') {
    return { httpStatus: 200, body: fail(40002, '只有「待收货」的入库单可以取消', config) }
  }
  order.status = 'CANCELLED'
  order.updateTime = timestamp()
  return { httpStatus: 200, body: ok(null, config) }
}

/** 简单版库位推荐：同仓库下优先已有该 SKU 的 RACK 库位，否则用第一个 RACK 库位 */
function recommendLocation(warehouseId, skuId) {
  const racks = locations.filter(
    (it) => it.warehouseId === Number(warehouseId) && it.status === 'ENABLED' && it.locationType === 'RACK',
  )
  if (!racks.length) return null

  const hit = racks.find((loc) =>
    inventories.some((inv) => inv.locationId === loc.id && inv.skuId === Number(skuId)),
  )
  return hit || racks[0]
}

function inboundGeneratePutaway(config, order) {
  if (order.status !== 'RECEIVED') {
    return { httpStatus: 200, body: fail(40002, '只有「已收货」的入库单可以生成上架任务', config) }
  }

  const pending = order.items.filter((it) => it.receivedQty - it.putawayQty > 0)
  if (!pending.length) {
    return { httpStatus: 200, body: fail(10008, '没有待上架的明细', config) }
  }

  // 幂等：同一明细已经有未完成的任务就不再生成
  const existing = stockTasks.filter(
    (t) => t.sourceType === 'INBOUND_ORDER' && t.sourceId === order.id && t.status !== 'DONE' && t.status !== 'CANCELLED',
  )

  let created = 0
  pending.forEach((item) => {
    if (existing.some((t) => t.sourceItemId === item.id)) return
    const target = recommendLocation(order.warehouseId, item.skuId)
    stockTasks.push({
      id: nextId(),
      taskNo: nextTaskNo(),
      taskType: 'PUTAWAY',
      warehouseId: order.warehouseId,
      warehouseName: warehouseNameOf(order.warehouseId),
      sourceType: 'INBOUND_ORDER',
      sourceId: order.id,
      sourceItemId: item.id,
      sourceNo: order.orderNo,
      status: 'CREATED',
      priority: 0,
      skuId: item.skuId,
      skuCode: item.skuCode,
      skuName: item.skuName,
      batchNo: item.batchNo,
      fromLocationId: locations.find((l) => l.warehouseId === order.warehouseId && l.locationType === 'TEMP')?.id || null,
      fromLocationCode: locations.find((l) => l.warehouseId === order.warehouseId && l.locationType === 'TEMP')?.code || null,
      toLocationId: target?.id || null,
      toLocationCode: target?.code || null,
      planQty: item.receivedQty - item.putawayQty,
      actualQty: 0,
      assigneeId: null,
      assigneeName: null,
      createTime: timestamp(),
      startTime: null,
      finishTime: null,
    })
    created++
  })

  if (!created) return { httpStatus: 200, body: fail(10007, '上架任务已生成，请勿重复提交', config) }
  return { httpStatus: 200, body: ok({ created }, config) }
}

/* ---------------- 库存 ---------------- */

function inventoryPage(config, params) {
  const keyword = (params.keyword || '').trim().toLowerCase()
  let rows = inventories.slice()

  if (keyword) {
    rows = rows.filter(
      (row) =>
        String(row.skuCode).toLowerCase().includes(keyword) ||
        String(row.skuName).toLowerCase().includes(keyword) ||
        String(row.locationCode || '').toLowerCase().includes(keyword),
    )
  }
  if (params.warehouseId) rows = rows.filter((row) => row.warehouseId === Number(params.warehouseId))
  if (params.skuId) rows = rows.filter((row) => row.skuId === Number(params.skuId))
  if (params.locationId) rows = rows.filter((row) => row.locationId === Number(params.locationId))
  if (params.batchNo) rows = rows.filter((row) => row.batchNo === params.batchNo)

  return paginate(rows, params, config)
}

function transactionPage(config, params) {
  const keyword = (params.keyword || '').trim().toLowerCase()
  let rows = transactions.slice()

  if (keyword) {
    rows = rows.filter(
      (row) =>
        String(row.txNo).toLowerCase().includes(keyword) ||
        String(row.skuCode).toLowerCase().includes(keyword) ||
        String(row.skuName).toLowerCase().includes(keyword),
    )
  }
  if (params.txType) rows = rows.filter((row) => row.txType === params.txType)
  if (params.skuId) rows = rows.filter((row) => row.skuId === Number(params.skuId))
  if (params.sourceNo) rows = rows.filter((row) => row.sourceNo === params.sourceNo)
  if (params.beginTime || params.endTime) {
    rows = rows.filter((row) => inRange(row.createTime, params.beginTime, params.endTime))
  }

  return paginate(rows, params, config)
}

/**
 * 库存唯一的写入口。真实后端必须把「改库存 + 写流水」放在同一个事务里（BR-008），
 * Mock 这里等价地在一个函数里完成，避免出现「改了库存没流水」的情况。
 */
function applyInventoryChange({
  warehouseId,
  locationId,
  skuId,
  batchNo,
  qtyChange,
  txType,
  sourceType,
  sourceId,
  sourceNo,
  operatorName,
  remark,
}) {
  const sku = products.find((p) => p.id === Number(skuId))
  let inv = inventories.find(
    (it) => it.locationId === Number(locationId) && it.skuId === Number(skuId) && it.batchNo === (batchNo || ''),
  )

  const before = inv ? inv.qty : 0
  if (!inv) {
    inv = {
      id: nextId(),
      warehouseId: Number(warehouseId),
      warehouseName: warehouseNameOf(warehouseId),
      locationId: Number(locationId),
      locationCode: locationCodeOf(locationId),
      skuId: Number(skuId),
      skuCode: sku?.skuCode || '',
      skuName: sku?.name || '',
      batchNo: batchNo || '',
      qty: 0,
      lockedQty: 0,
      frozenQty: 0,
      availableQty: 0,
      inboundTime: timestamp(),
    }
    inventories.push(inv)
  }

  inv.qty += qtyChange
  inv.availableQty = inv.qty - inv.lockedQty - inv.frozenQty

  transactions.push({
    id: nextId(),
    txNo: nextTxNo(),
    warehouseId: inv.warehouseId,
    warehouseName: inv.warehouseName,
    locationId: inv.locationId,
    locationCode: inv.locationCode,
    skuId: inv.skuId,
    skuCode: inv.skuCode,
    skuName: inv.skuName,
    batchNo: inv.batchNo,
    txType,
    qtyChange,
    lockedChange: 0,
    frozenChange: 0,
    qtyBefore: before,
    qtyAfter: inv.qty,
    sourceType,
    sourceId,
    sourceNo,
    operatorId: null,
    operatorName: operatorName || '当前用户',
    remark: remark || '',
    createTime: timestamp(),
  })
}

/* ---------------- 作业任务 ---------------- */

function taskPage(config, params) {
  const keyword = (params.keyword || '').trim().toLowerCase()
  let rows = stockTasks.slice()

  if (keyword) {
    rows = rows.filter(
      (row) =>
        String(row.taskNo).toLowerCase().includes(keyword) ||
        String(row.skuCode).toLowerCase().includes(keyword),
    )
  }
  if (params.taskType) rows = rows.filter((row) => row.taskType === params.taskType)
  if (params.status) rows = rows.filter((row) => row.status === params.status)
  if (params.warehouseId) rows = rows.filter((row) => row.warehouseId === Number(params.warehouseId))

  return paginate(rows, params, config)
}

function taskAction(config, task, action, payload) {
  if (action === 'assign') {
    if (task.status !== 'CREATED') {
      return { httpStatus: 200, body: fail(70003, '任务已被领取', config) }
    }
    task.status = 'ASSIGNED'
    task.assigneeId = users[2].id
    task.assigneeName = users[2].realName
    return { httpStatus: 200, body: ok(null, config) }
  }

  if (action === 'start') {
    if (task.status !== 'ASSIGNED') {
      return { httpStatus: 200, body: fail(10008, '当前状态不允许开始', config) }
    }
    task.status = 'PROCESSING'
    task.startTime = timestamp()
    return { httpStatus: 200, body: ok(null, config) }
  }

  if (action === 'complete') {
    if (task.status !== 'PROCESSING') {
      return { httpStatus: 200, body: fail(10008, '当前状态不允许完成', config) }
    }

    const qty = Number(payload.actualQty ?? task.planQty)
    if (qty < 0) return { httpStatus: 200, body: fail(10002, '实际上架数量不能为负', config) }
    if (!task.toLocationId) return { httpStatus: 200, body: fail(10008, '任务没有目标库位', config) }

    // 上架完成的瞬间才写库存 —— 这是整个入库链路唯一改库存的地方
    if (qty > 0) {
      applyInventoryChange({
        warehouseId: task.warehouseId,
        locationId: task.toLocationId,
        skuId: task.skuId,
        batchNo: task.batchNo,
        qtyChange: qty,
        txType: 'PUTAWAY_IN',
        sourceType: 'TASK',
        sourceId: task.id,
        sourceNo: task.taskNo,
        operatorName: task.assigneeName,
        remark: payload.remark || '上架入库',
      })
    }

    task.status = 'DONE'
    task.actualQty = qty
    task.finishTime = timestamp()

    // 回写单据明细的已上架数量；整单都上架完就把单据推到 PUTAWAY_DONE
    const order =
      task.sourceType === 'INBOUND_ORDER'
        ? inboundOrders.find((it) => it.id === task.sourceId)
        : null
    if (order && task.sourceItemId) {
      const item = order.items.find((it) => it.id === task.sourceItemId)
      if (item) item.putawayQty = Math.min(item.expectQty, item.putawayQty + qty)
      const allDone = order.items.every((it) => it.putawayQty >= it.receivedQty)
      if (allDone) order.status = 'PUTAWAY_DONE'
      order.updateTime = timestamp()
    }

    return { httpStatus: 200, body: ok(null, config) }
  }

  return { httpStatus: 404, body: fail(10004, `未支持的任务动作 ${action}`, config) }
}
/* ------------------------------ 路由分发 ------------------------------ */

function dispatch(config) {
  const method = (config.method || 'get').toUpperCase()
  const url = (config.url || '').split('?')[0]
  const params = config.params || {}
  const payload = config.data ? JSON.parse(config.data) : {}

  if (method === 'POST' && url === '/v1/auth/login') return handleLogin(payload, config)
  if (method === 'GET' && url === '/v1/auth/me') {
    const user = users[0]
    return {
      httpStatus: 200,
      body: ok(
        { id: user.id, username: user.username, realName: user.realName, role: user.role },
        config,
      ),
    }
  }

  /* ---------------- 入库 ---------------- */
  const inboundAction = url.match(/^\/v1\/inbound\/inbound-orders\/(\d+)\/([a-z-]+)$/)
  const inboundDetail = url.match(/^\/v1\/inbound\/inbound-orders\/(\d+)$/)

  if (method === 'GET' && url === '/v1/inbound/inbound-orders') return inboundPage(config, params)
  if (method === 'POST' && url === '/v1/inbound/inbound-orders') return inboundCreate(config, payload)

  if (inboundDetail && method === 'GET') return inboundGet(config, inboundDetail[1])

  if (inboundAction && method === 'POST') {
    const order = inboundOrders.find((it) => it.id === Number(inboundAction[1]))
    if (!order) return { httpStatus: 200, body: fail(10004, '入库单不存在', config) }
    const action = inboundAction[2]
    if (action === 'receive') return inboundReceive(config, order, payload)
    if (action === 'cancel') return inboundCancel(config, order)
    if (action === 'putaway-tasks') return inboundGeneratePutaway(config, order)
    return { httpStatus: 404, body: fail(10004, `未支持的入库动作 ${action}`, config) }
  }

  /* ---------------- 库存 ---------------- */
  if (method === 'GET' && url === '/v1/inventory/inventories') return inventoryPage(config, params)
  if (method === 'GET' && url === '/v1/inventory/transactions') return transactionPage(config, params)

  /* ---------------- 作业任务 ---------------- */
  const taskActionMatch = url.match(/^\/v1\/task\/stock-tasks\/(\d+)\/([a-z-]+)$/)
  if (method === 'GET' && url === '/v1/task/stock-tasks') return taskPage(config, params)
  if (taskActionMatch && method === 'POST') {
    const task = stockTasks.find((it) => it.id === Number(taskActionMatch[1]))
    if (!task) return { httpStatus: 200, body: fail(10004, '任务不存在', config) }
    return taskAction(config, task, taskActionMatch[2], payload)
  }

  const byId = url.match(/^\/v1\/basedata\/([a-zA-Z-]+)\/(\d+)$/)
  const byCollection = url.match(/^\/v1\/basedata\/([a-zA-Z-]+)$/)

  if (byId) {
    const resource = RESOURCES[byId[1]]
    if (!resource) return { httpStatus: 404, body: fail(10004, '资源不存在', config) }
    if (method === 'PUT') return updateOf(resource, config, byId[2], payload)
    if (method === 'DELETE') return removeOf(resource, config, byId[2])
    if (method === 'GET') {
      const row = resource.table.find((it) => it.id === Number(byId[2]))
      return row
        ? { httpStatus: 200, body: ok(row, config) }
        : { httpStatus: 200, body: fail(10004, '资源不存在', config) }
    }
  }

  if (byCollection) {
    const resource = RESOURCES[byCollection[1]]
    if (!resource) return { httpStatus: 404, body: fail(10004, '资源不存在', config) }
    if (method === 'GET') return pageOf(resource, config, params)
    if (method === 'POST') return createOf(resource, config, payload)
  }

  return { httpStatus: 404, body: fail(10004, `Mock 未实现的接口：${method} ${url}`, config) }
}

/* ------------------------------ axios adapter ------------------------------ */

export async function mockAdapter(config) {
  await sleep(220 + Math.random() * 280)

  const { httpStatus, body } = dispatch(config)

  if (httpStatus >= 400) {
    const error = new Error(`Request failed with status code ${httpStatus}`)
    error.isAxiosError = true
    error.config = config
    error.code = 'ERR_BAD_REQUEST'
    error.response = {
      data: body,
      status: httpStatus,
      statusText: '',
      headers: {},
      config,
      request: {},
    }
    throw error
  }

  return {
    data: body,
    status: httpStatus,
    statusText: 'OK',
    headers: {},
    config,
    request: {},
  }
}