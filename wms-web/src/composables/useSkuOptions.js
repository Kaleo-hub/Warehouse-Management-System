import { ref } from 'vue'
import { productApi } from '@/api/basedata'
import { fetchAllOptions } from '@/utils/loadAllOptions'
import { toastError } from '@/utils/error'

/**
 * 商品（SKU）下拉选项，模块级缓存，多个单据页共用。
 *
 * 只取启用状态的商品 —— 停用的 SKU 不应该还能开新单。
 * 商品是主数据，入库/出库单据只能引用它，不能在这里新建，
 * 需要新商品请先去「基础数据 → 商品管理」建档。
 *
 * 注意用 fetchAllOptions 分页拉取，不要直接写 size: 200 ——
 * 后端对 size 有 @Max(100) 约束，传 200 会直接 10002 参数校验失败。
 */
const cachedOptions = ref([])
const loaded = ref(false)

export function useSkuOptions() {
  const options = ref(cachedOptions.value)
  const loading = ref(false)

  async function load(force = false) {
    if (loaded.value && !force) {
      options.value = cachedOptions.value
      return options.value
    }

    loading.value = true
    try {
      const list = await fetchAllOptions((params) =>
        productApi.page({ ...params, status: 'ENABLED' }),
      )

      cachedOptions.value = list.map((item) => ({
        label: `${item.skuCode} ${item.name}`,
        value: item.id,
        code: item.skuCode,
        name: item.name,
        spec: item.spec,
        unit: item.unit,
        shelfLifeDays: item.shelfLifeDays,
      }))
      loaded.value = true
      options.value = cachedOptions.value
    } catch (error) {
      /*
       * 【不要静默吞异常】
       * 这里原来只写了 options.value = []，把「size 超过后端上限」的真实错误吃掉了，
       * 表现成下拉一片空白、界面上毫无提示，非常难排查。
       * 加载失败至少要让控制台和用户都能看到。
       */
      console.error('[skuOptions] 加载商品列表失败', error)
      toastError(error, '商品列表加载失败')
      options.value = []
    } finally {
      loading.value = false
    }

    return options.value
  }

  function findById(id) {
    return cachedOptions.value.find((item) => item.value === id) || null
  }

  return { options, loading, load, findById }
}