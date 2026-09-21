import {
  Box,
  Coordinate,
  DataLine,
  DocumentChecked,
  Download,
  Goods,
  Grid,
  Histogram,
  List,
  Location,
  Odometer,
  OfficeBuilding,
  RefreshRight,
  Setting,
  Upload,
  User,
  Van,
  Warning,
} from '@element-plus/icons-vue'

/**
 * 角色常量，和后端 system/enums/UserRole 一一对应。
 * 后端是权威定义，这里只是前端用得到的镜像。
 */
export const ROLES = {
  ADMIN: 'ADMIN',
  MANAGER: 'MANAGER',
  OPERATOR: 'OPERATOR',
}

/** 主数据维护 + 库存调整：管理员和仓库主管 */
const MANAGE = [ROLES.ADMIN, ROLES.MANAGER]
/** 库存调整类动作（会改账）：管理员和仓库主管 */
const ADJUST = [ROLES.ADMIN, ROLES.MANAGER]
/** 系统管理：仅管理员 */
const SYSTEM = [ROLES.ADMIN]

/**
 * 侧边栏导航配置。
 *
 * roles：不写 = 所有登录用户都能看；写了 = 只有这些角色能看。
 * soon : true 表示页面还没开发，菜单置灰并打「规划中」标记 —— 不做假页面。
 *
 * 【前端过滤的定位】只负责「看不到、不用点」，
 * 真正的权限边界在后端 @PreAuthorize（藏起来的菜单挡不住直接敲 URL）。
 */
export const menuGroups = [
  {
    title: '概览',
    items: [{ title: '工作台', path: '/dashboard', icon: Odometer }],
  },
  {
    title: '基础数据',
    items: [
      { title: '仓库管理', path: '/basedata/warehouses', icon: OfficeBuilding, roles: MANAGE },
      { title: '库区管理', path: '/basedata/zones', icon: Grid, roles: MANAGE },
      { title: '库位管理', path: '/basedata/locations', icon: Location, roles: MANAGE },
      { title: '商品管理', path: '/basedata/products', icon: Goods, roles: MANAGE },
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
      { title: '库存汇总', path: '/inventory/summary', icon: Histogram },
      { title: '库存预警', path: '/inventory/alerts', icon: Warning },
      { title: '库存流水', path: '/inventory/transactions', icon: List },
      { title: '库存移库', path: '/inventory/transfer', icon: Coordinate, roles: ADJUST },
      { title: '库存盘点', path: '/inventory/stocktaking', icon: DocumentChecked, roles: ADJUST },
      { title: '库存对账', path: '/inventory/reconcile', icon: RefreshRight, roles: ADJUST },
    ],
  },
  {
    title: '出库管理',
    items: [
      { title: '出库单', path: '/outbound/orders', icon: Upload },
      { title: '拣货任务', path: '/picking/tasks', icon: DataLine },
    ],
  },
  {
    title: '系统',
    items: [
      { title: '用户管理', path: '/system/users', icon: User, roles: SYSTEM },
      { title: '系统设置', path: '/settings', icon: Setting, roles: SYSTEM, soon: true },
    ],
  },
]

/**
 * 按角色过滤菜单：先过滤入口，再把「过滤后没有入口的分组」整个去掉。
 *
 * 为什么要去掉空分组：作业员看到一排「基础数据 / 系统」的空标题，
 * 只会以为是页面坏了。菜单里出现的每一项都应该是能点的。
 */
export function menuGroupsForRole(role) {
  return menuGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.roles || item.roles.includes(role)),
    }))
    .filter((group) => group.items.length > 0)
}