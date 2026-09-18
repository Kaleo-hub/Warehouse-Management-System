# wms-web · WMS 前端

配合 `wms-server` 的仓储管理前端。

进度：

| 模块 | 状态 |
|---|---|
| 登录 / JWT / 路由守卫 | ✅ 已对接真实接口 |
| 基础数据（仓库 / 库区 / 库位 / 商品） | ✅ 已对接真实接口 |
| 入库管理（入库单 / 收货 / 上架任务） | ✅ 页面完成，等后端接口（契约见 docs/入库模块-后端接口约定.md） |
| 库存管理（库存查询 / 库存流水） | ✅ 页面完成，等后端接口 |
| 出库 / 拣货 | ⬜ 未开始 |

技术栈：Vue 3.5 · Vite 8 · Vue Router 4 · Pinia · Element Plus 2.14 · Axios

## 快速开始

```bash
npm install

# 方式一：对接本地 Spring Boot（默认，vite 把 /api 代理到 localhost:8080）
npm run dev

# 方式二：纯前端演示，后端没起来也能点点看（内存 Mock）
npm run dev:mock

# 生产构建
npm run build
```

浏览器打开 http://localhost:5173 ，默认账号 `admin` / `123456`。

## 环境变量

| 文件 | 作用 |
|---|---|
| `.env` | 所有模式共享（标题、版本号） |
| `.env.development` | 开发模式，`VITE_USE_MOCK=false`，走 vite proxy |
| `.env.mock` | 演示模式，`VITE_USE_MOCK=true`，请求被 axios adapter 拦截 |
| `.env.production` | 生产构建，`/api` 交给 Nginx 反代 |

> Mock 是一层 axios adapter，不是假数据 mock 框架。业务代码、拦截器、错误处理走的是同一条链路，
> 接好后端改一个环境变量就切换过去，不用改任何业务代码。

## 目录结构

```
src/
├── api/               接口层：request 封装 + 各模块 API
│   ├── request.js     axios 实例、Result 拆包、错误码、traceId、401 跳转
│   ├── auth.js        登录
│   ├── basedata.js    仓库 / 库区 / 库位 / 商品
│   ├── inbound.js     入库单（含收货、生成上架任务）
│   ├── inventory.js   库存查询 / 库存流水（只读）
│   └── task.js        作业任务（领取 / 开始 / 完成）
├── components/
│   ├── common/        页面级通用组件（PageHeading）
│   ├── crud/          CrudTable：查询+表格+分页+抽屉表单+删除确认
│   └── form/          LineItemsEditor：可增删行的单据明细编辑器
├── composables/       组合式函数（仓库 / SKU 下拉选项缓存）
├── constants/         枚举选项（与数据库注释一一对应）
├── layouts/           应用骨架：侧边栏 / 顶栏 / 菜单配置
├── mock/              演示数据与 mock adapter
├── router/            路由表与登录守卫
├── stores/            Pinia：auth（登录态）、app（主题、侧边栏）
├── styles/            设计令牌 + 基础样式 + Element 主题覆盖
├── utils/             本地存储、格式化
└── views/             页面
```

## 设计系统

所有颜色、圆角、阴影、尺寸都在 `src/styles/variables.css` 里定义，组件内**不写死色值**。

- 品牌色：仓储蓝 `--brand-500 (#3b64f4)`，辅助扫描青 `--accent-500 (#0ea5e9)`
- 暗色模式：切换 `<html class="dark">`，令牌整体替换，Element Plus 通过 `element-overrides.css` 里的变量映射自动跟随
- 想换主题色，只改 `--brand-*` 一组变量即可，不用动组件

## 通用 CRUD 组件怎么用

`CrudTable` 把「查询栏 + 工具栏 + 表格 + 分页 + 表单抽屉 + 删除确认」封装成一个组件，
业务页面只提供四份配置：

```vue
<CrudTable
  :api="warehouseApi"          <!-- { page, create, update, remove } -->
  :columns="columns"           <!-- 列定义，支持 tag / mono / width -->
  :search-fields="searchFields"
  :form-fields="formFields"    <!-- 表单字段，支持 input/textarea/number/select -->
  entity-name="仓库"
/>
```

需要自定义单元格或表单项时用插槽：`#col-xxx` / `#form-xxx`（参考 `WarehouseView.vue`、`LocationView.vue`）。

## 和后端的对接约定

见 [docs/后端接口约定.md](docs/后端接口约定.md)。**新增接口前请先看这份文档**，
前端已经按里面的约定写好了请求层，后端照着实现即可零改动接通。

## 入库链路怎么点

`npm run dev:mock` 启动后，按这个顺序可以完整走一遍（Mock 里预置了三种状态的单据）：

```
入库单 → 对「待收货」的单据点收货 → 生成上架任务
       → 上架任务页：领取 → 开始 → 完成上架
       → 库存查询：看到新库存（这一步才会真正加库存）
       → 库存流水：看到 PUTAWAY_IN 那条记录
```

**关键认知**：收货只登记数量，不增加库存；**上架完成的瞬间才写库存和流水**。

## 已知待办

- [ ] 生产包体积优化：Element Plus 目前是全量引入（gzip 约 318KB）。接入
      `unplugin-vue-components` + `unplugin-auto-import` 按需引入可显著下降，
      但需要同步处理 `ElMessage` / `ElMessageBox` / `v-loading` 的样式引入。
- [ ] 401 自动跳转登录页的逻辑已实现，等后端补 `GET /api/v1/auth/me` 后可加「刷新页面重新拉用户信息」。
- [ ] 按钮级权限：等后端返回菜单/权限点后，在 `layouts/menu.js` 和 `CrudTable` 的
      `canCreate` / `canUpdate` / `canDelete` 上加判断。