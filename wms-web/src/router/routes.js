import AppLayout from '@/layouts/AppLayout.vue'

export const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '工作台', breadcrumb: ['工作台'] },
      },

      /* ---------------- 基础数据 ---------------- */
      {
        path: 'basedata/warehouses',
        name: 'WarehouseList',
        component: () => import('@/views/basedata/WarehouseView.vue'),
        meta: { title: '仓库管理', breadcrumb: ['基础数据', '仓库管理'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'basedata/zones',
        name: 'ZoneList',
        component: () => import('@/views/basedata/ZoneView.vue'),
        meta: { title: '库区管理', breadcrumb: ['基础数据', '库区管理'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'basedata/locations',
        name: 'LocationList',
        component: () => import('@/views/basedata/LocationView.vue'),
        meta: { title: '库位管理', breadcrumb: ['基础数据', '库位管理'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'basedata/products',
        name: 'ProductList',
        component: () => import('@/views/basedata/ProductView.vue'),
        meta: { title: '商品管理', breadcrumb: ['基础数据', '商品管理'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'basedata/partners',
        name: 'PartnerList',
        component: () => import('@/views/basedata/PartnerView.vue'),
        meta: { title: '往来单位', breadcrumb: ['基础数据', '往来单位'], roles: ['ADMIN', 'MANAGER'] },
      },

      /* ---------------- 入库 ---------------- */
      {
        path: 'inbound/orders',
        name: 'InboundOrderList',
        component: () => import('@/views/inbound/InboundOrderList.vue'),
        meta: { title: '入库单', breadcrumb: ['入库管理', '入库单'] },
      },
      // 注意：create 必须定义在 :id 之前，避免被当成 id
      {
        path: 'inbound/orders/create',
        name: 'InboundOrderCreate',
        component: () => import('@/views/inbound/InboundOrderCreate.vue'),
        meta: { title: '新建入库单', breadcrumb: ['入库管理', '入库单', '新建'] },
      },
      {
        path: 'inbound/orders/:id',
        name: 'InboundOrderDetail',
        component: () => import('@/views/inbound/InboundOrderDetail.vue'),
        meta: { title: '入库单详情', breadcrumb: ['入库管理', '入库单', '详情'] },
      },
      {
        path: 'putaway/tasks',
        name: 'PutawayTaskList',
        component: () => import('@/views/putaway/PutawayTaskList.vue'),
        meta: { title: '上架任务', breadcrumb: ['入库管理', '上架任务'] },
      },

      /* ---------------- 出库 ---------------- */
      {
        path: 'outbound/orders',
        name: 'OutboundOrderList',
        component: () => import('@/views/outbound/OutboundOrderList.vue'),
        meta: { title: '出库单', breadcrumb: ['出库管理', '出库单'] },
      },
      // create 必须定义在 :id 之前，避免被当成 id
      {
        path: 'outbound/orders/create',
        name: 'OutboundOrderCreate',
        component: () => import('@/views/outbound/OutboundOrderCreate.vue'),
        meta: { title: '新建出库单', breadcrumb: ['出库管理', '出库单', '新建'] },
      },
      {
        path: 'outbound/orders/:id',
        name: 'OutboundOrderDetail',
        component: () => import('@/views/outbound/OutboundOrderDetail.vue'),
        meta: { title: '出库单详情', breadcrumb: ['出库管理', '出库单', '详情'] },
      },
      {
        path: 'picking/tasks',
        name: 'PickTaskList',
        component: () => import('@/views/picking/PickTaskList.vue'),
        meta: { title: '拣货任务', breadcrumb: ['出库管理', '拣货任务'] },
      },

      /* ---------------- 库存 ---------------- */
      {
        path: 'inventory/stock',
        name: 'InventoryList',
        component: () => import('@/views/inventory/InventoryList.vue'),
        meta: { title: '库存查询', breadcrumb: ['库存管理', '库存查询'] },
      },
      {
        path: 'inventory/summary',
        name: 'InventorySummary',
        component: () => import('@/views/inventory/InventorySummary.vue'),
        meta: { title: '库存汇总', breadcrumb: ['库存管理', '库存汇总'] },
      },
      {
        path: 'inventory/alerts',
        name: 'InventoryAlert',
        component: () => import('@/views/inventory/InventoryAlert.vue'),
        meta: { title: '库存预警', breadcrumb: ['库存管理', '库存预警'] },
      },
      {
        path: 'inventory/transactions',
        name: 'TransactionList',
        component: () => import('@/views/inventory/TransactionList.vue'),
        meta: { title: '库存流水', breadcrumb: ['库存管理', '库存流水'] },
      },
      {
        path: 'inventory/transfer',
        name: 'TransferOrderList',
        component: () => import('@/views/inventory/TransferOrderList.vue'),
        meta: { title: '移库单', breadcrumb: ['库存管理', '移库单'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'inventory/transfer/create',
        name: 'TransferOrderCreate',
        component: () => import('@/views/inventory/TransferOrderCreate.vue'),
        meta: { title: '新建移库单', breadcrumb: ['库存管理', '移库单', '新建'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'inventory/transfer/quick',
        name: 'InventoryTransfer',
        component: () => import('@/views/inventory/InventoryTransfer.vue'),
        meta: { title: '快速移库', breadcrumb: ['库存管理', '移库单', '快速移库'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'inventory/transfer/:id',
        name: 'TransferOrderDetail',
        component: () => import('@/views/inventory/TransferOrderDetail.vue'),
        meta: { title: '移库单详情', breadcrumb: ['库存管理', '移库单', '详情'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'inventory/stocktaking',
        name: 'StocktakingOrderList',
        component: () => import('@/views/inventory/StocktakingOrderList.vue'),
        meta: { title: '盘点单', breadcrumb: ['库存管理', '盘点单'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'inventory/stocktaking/create',
        name: 'StocktakingOrderCreate',
        component: () => import('@/views/inventory/StocktakingOrderCreate.vue'),
        meta: { title: '新建盘点单', breadcrumb: ['库存管理', '盘点单', '新建'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'inventory/stocktaking/quick',
        name: 'Stocktaking',
        component: () => import('@/views/inventory/Stocktaking.vue'),
        meta: { title: '快速盘点', breadcrumb: ['库存管理', '盘点单', '快速盘点'], roles: ['ADMIN', 'MANAGER'] },
      },
      {
        path: 'inventory/stocktaking/:id',
        name: 'StocktakingOrderDetail',
        component: () => import('@/views/inventory/StocktakingOrderDetail.vue'),
        meta: { title: '盘点单详情', breadcrumb: ['库存管理', '盘点单', '详情'], roles: ['ADMIN', 'MANAGER'] },
        path: 'inventory/reconcile',
        name: 'Reconcile',
        component: () => import('@/views/inventory/Reconcile.vue'),
        meta: { title: '库存对账', breadcrumb: ['库存管理', '库存对账'], roles: ['ADMIN', 'MANAGER'] },
      },

      /* ---------------- 系统 ---------------- */
      {
        path: 'system/users',
        name: 'UserList',
        component: () => import('@/views/system/UserView.vue'),
        meta: { title: '用户管理', breadcrumb: ['系统', '用户管理'], roles: ['ADMIN'] },
      },
      {
        path: 'system/audit-logs',
        name: 'AuditLogList',
        component: () => import('@/views/system/AuditLogView.vue'),
        meta: { title: '操作日志', breadcrumb: ['系统', '操作日志'], roles: ['ADMIN'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]

export default routes
