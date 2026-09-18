import request from './request'

/**
 * 基础数据四张表结构一致，用工厂函数收敛，避免复制粘贴。
 * URL 规范见 docs/02-开发规范与计划.md 第 4.1 节：
 *   GET    /api/v1/basedata/{resource}        分页查询
 *   POST   /api/v1/basedata/{resource}        新增
 *   PUT    /api/v1/basedata/{resource}/{id}   修改
 *   DELETE /api/v1/basedata/{resource}/{id}   删除
 */
function createCrudApi(resource) {
  const base = `/v1/basedata/${resource}`
  return {
    page: (params) => request.get(base, { params }),
    detail: (id) => request.get(`${base}/${id}`),
    create: (payload) => request.post(base, payload),
    update: (id, payload) => request.put(`${base}/${id}`, payload),
    remove: (id) => request.delete(`${base}/${id}`),
  }
}

export const warehouseApi = createCrudApi('warehouses')
export const zoneApi = createCrudApi('zones')
export const locationApi = createCrudApi('locations')
export const productApi = createCrudApi('products')