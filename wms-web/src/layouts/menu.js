import {
  Box,
  Coordinate,
  DataLine,
  Download,
  Goods,
  Grid,
  List,
  Location,
  Odometer,
  OfficeBuilding,
  Setting,
  Upload,
  Van,
} from '@element-plus/icons-vue'

/**
 * 侧边栏导航配置。
 * soon: true 表示该模块的页面还没开发，菜单置灰并打「规划中」标记 —— 不做假页面。
 */
export const menuGroups = [
  {
    title: '概览',
    items: [{ title: '工作台', path: '/dashboard', icon: Odometer }],
  },
  {
    title: '基础数据',
    items: [
      { title: '仓库管理', path: '/basedata/warehouses', icon: OfficeBuilding },
      { title: '库区管理', path: '/basedata/zones', icon: Grid },
      { title: '库位管理', path: '/basedata/locations', icon: Location },
      { title: '商品管理', path: '/basedata/products', icon: Goods },
    ],
  },
  {
    title: '入库管理',
    items: [
      { title: '入库单', path: '/inbound/orders', icon: Download },
      { title: '上架任务', path: '/putaway/tasks', icon: Van },
    ],
  },
  {
    title: '库存管理',
    items: [
      { title: '库存查询', path: '/inventory/stock', icon: Box },
      { title: '库存流水', path: '/inventory/transactions', icon: List },
    ],
  },
  {
    title: '出库管理',
    items: [
      { title: '出库单', path: '/outbound/orders', icon: Upload, soon: true },
      { title: '拣货任务', path: '/picking/tasks', icon: DataLine, soon: true },
    ],
  },
  {
    title: '系统',
    items: [
      { title: '库存移库', path: '/inventory/transfer', icon: Coordinate, soon: true },
      { title: '系统设置', path: '/settings', icon: Setting, soon: true },
    ],
  },
]